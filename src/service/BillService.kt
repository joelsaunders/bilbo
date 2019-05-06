package com.bilbo.service

import com.bilbo.model.*
import io.ktor.util.KtorExperimentalAPI
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.transactions.transaction
import org.joda.time.DateTime
import java.lang.IllegalArgumentException
import kotlin.math.absoluteValue


object BillServiceError: Throwable()


@KtorExperimentalAPI
class BillService {

    suspend fun getAllBills(userId: Int): List<Bill> = DatabaseFactory.dbQuery {
        Bills.select { Bills.userId eq  userId}.map { toBill(it) }
    }

    private fun getBillById(id: Int): Bill? {
        return Bills.select {
            (Bills.id eq id)
        }.mapNotNull { toBill(it) }
            .singleOrNull()
    }

    suspend fun getBills(userId: Int): List<Bill> = DatabaseFactory.dbQuery {
        Bills.select {
            (Bills.userId eq userId)
        }.map { toBill(it) }
    }

    private fun getCurrentPeriodStart(now: DateTime, user: User): DateTime {
        return if (now.dayOfMonth.absoluteValue >= user.potDepositDay!!) {
            DateTime(
                now.year,
                now.monthOfYear().get(),
                user.potDepositDay,
                0,
                0
            )
        } else {
            DateTime(
                now.year,
                now.minusMonths(1).monthOfYear().get(),
                user.potDepositDay,
                0,
                0
            )
        }
    }

    private fun calculatePaymentsThisPeriod(
        periodStart: DateTime,
        periodEnd: DateTime,
        periodFrequency: Int,
        periodType: String,
        paymentStart: DateTime
    ): List<DateTime> {

        var current: DateTime = paymentStart
        val datesList = mutableListOf<DateTime>()

        while (current < periodEnd) {
            if (current > periodStart && current < periodEnd) {
                datesList.add(current)
            }
            current = when (periodType) {
                "day" -> current.plusDays(periodFrequency)
                "week" -> current.plusWeeks(periodFrequency)
                "month" -> current.plusMonths(periodFrequency)
                else -> throw IllegalArgumentException("period type $periodType not allowed")
            }
        }
        return datesList
    }

    /** Calculate if a bill is currently due for withdrawal
     *
     * A bill is due for withdrawal if there is no withdrawal record for the latest passed withdrawal date
     *
     */
    private fun currentlyDueWithdrawal(bill: Bill, now: DateTime, user: User): DateTime? {
        val thisPeriodStart = getCurrentPeriodStart(DateTime(), user)
        val thisPeriodEnd = thisPeriodStart.plusMonths(1)

        val paymentsThisPeriod = calculatePaymentsThisPeriod(
            thisPeriodStart,
            thisPeriodEnd,
            bill.periodFrequency,
            bill.periodType,
            bill.startDate
        )

        val lastPassedWithdrawalDate = paymentsThisPeriod.lastOrNull {
            it.isBefore(now)
        } ?: return null

        val noWithdrawal = Withdrawals.select {
            (Withdrawals.billId eq bill.id) and
            (Withdrawals.withdrawalDate greaterEq lastPassedWithdrawalDate)
        }.count() == 0

        return if (noWithdrawal) lastPassedWithdrawalDate else null
    }


    /**Fetch all bills due for withdrawal
     *
     * A bill due for withdrawal has a due date within 1 hour and has not already got a withdrawal
     * entry within the last 2 hours
     */
    suspend fun getDueWithdrawals(user: User): List<Bill> = DatabaseFactory.dbQuery {

        // get all bills
        val bills = Bills.select {
            (Bills.userId eq user.id)
        }.map { toBill(it) }

        // for each bill, check if there is a currently due withdrawal
        bills.map {
            it to currentlyDueWithdrawal(
                it,
                DateTime(),
                user
            )
        }.filter{ it.second != null }.map { it.first }
    }

    /**Fetch all bills due for deposit
     *
     * due for deposit means that the bill is due to be paid at least once this pay period and has not
     * yet been deposited.
     */
    suspend fun getDueDeposits(user: User): Map<Bill, List<DateTime>> = DatabaseFactory.dbQuery {
        val thisPeriodStart = getCurrentPeriodStart(DateTime(), user)

        val billTable = Bills.alias("bills")
        val billId = billTable[Bills.id]

        // Fetch all bills where the start date is in the past and there is no deposit for the bill
        // so far since the start of this period.
        val bills = transaction {
            billTable.select {
                (
                    (Bills.startDate less thisPeriodStart.plusMonths(1)) and
                            (Bills.userId eq user.id) and
                            (notExists(
                                Deposits.select{
                                    (
                                            (Deposits.depositDate greaterEq thisPeriodStart) and
                                                    (Deposits.billId eq billId)
                                            )
                                }
                            ))
                    )
            }.map {
                Bill(
                    id = it[billId],
                    userId = it[billTable[Bills.userId]],
                    name = it[billTable[Bills.name]],
                    amount = it[billTable[Bills.amount]],
                    periodType = it[billTable[Bills.periodType]],
                    periodFrequency = it[billTable[Bills.periodFrequency]],
                    startDate = it[billTable[Bills.startDate]]
                )
            }
        }

        // map the bills to the payments that are due this period in the future so we can get a total amount
        // needed to be deposited
        bills.map {
            it to calculatePaymentsThisPeriod(
                thisPeriodStart,
                thisPeriodStart.plusMonths(1),
                it.periodFrequency,
                it.periodType,
                it.startDate
            ).filter { dateTime -> dateTime.isAfter(DateTime()) }
        }.filter { it.second.count() > 0 }.toMap()
    }

    /**Add a new newBill item
     *
     * If the newBill's next due date this period is in the past, then also a
     * withdrawal for it (without calling monzo) to stop one actually being made by the scheduler.
     */
    suspend fun addBill(newBill: NewBill, user: User): Bill = DatabaseFactory.dbQuery {
        val billId = Bills.insert {
            it[userId] = user.id
            it[name] = newBill.name
            it[amount] = newBill.amount
            it[periodType] = newBill.periodType
            it[periodFrequency] = newBill.periodFrequency
            it[startDate] = newBill.startDate
        } get Bills.id

        val bill = if (billId != null) {
            getBillById(billId)?: throw BillServiceError
        } else throw BillServiceError


        // if there is a currently due withdrawal then make the record so that a real withdrawal is not made
        currentlyDueWithdrawal(bill, DateTime(), user)?: return@dbQuery bill

        Withdrawals.insert {
            it[withdrawalDate] = DateTime()
            it[success] = false
            it[Withdrawals.billId] = bill.id
        }

        bill
    }

    suspend fun updateBill(bill: Bill) = DatabaseFactory.dbQuery {
        Bills.update({Bills.id eq bill.id}) {
            it[userId] = bill.userId
            it[name] = bill.name
            it[amount] = bill.amount
            it[periodType] = bill.periodType
            it[periodFrequency] = bill.periodFrequency
            it[startDate] = bill.startDate
        }
    }

    suspend fun removeBill(id: Int, userId: Int) = DatabaseFactory.dbQuery {
        Bills.deleteWhere { ((Bills.id eq id) and (Bills.userId eq userId)) }
    }

    private fun toBill(row: ResultRow): Bill =
        Bill(
            id = row[Bills.id],
            userId = row[Bills.userId],
            name = row[Bills.name],
            amount = row[Bills.amount],
            periodType = row[Bills.periodType],
            periodFrequency = row[Bills.periodFrequency],
            startDate = row[Bills.startDate]
        )
}
