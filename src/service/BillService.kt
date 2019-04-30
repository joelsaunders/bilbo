package com.bilbo.service

import com.bilbo.model.*
import io.ktor.util.KtorExperimentalAPI
import kotlinx.coroutines.selects.select
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.transactions.transaction
import org.joda.time.DateTime
import java.lang.IllegalArgumentException
import kotlin.math.absoluteValue

@KtorExperimentalAPI
class BillService {

    suspend fun getAllBills(): List<Bill> = DatabaseFactory.dbQuery {
        Bills.selectAll().map { toBill(it) }
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

    /**
     * a due date this period is in the past and has not yet been withdrawn
     */
    suspend fun getDueWithdrawals(user: User): Map<Bill, List<DateTime>> = DatabaseFactory.dbQuery {
        val thisPeriodStart = getCurrentPeriodStart(DateTime(), user)

        val bills = Bills.select {
            (Bills.userId eq user.id)
        }.map { toBill(it) }

        val billsDatesMap = bills.map {
            it to calculatePaymentsThisPeriod(
                thisPeriodStart,
                thisPeriodStart.plusMonths(1),
                it.periodFrequency,
                it.periodType,
                it.startDate
            )
        }.filter {
            it.second.count() > 0
        }.toMap()

        billsDatesMap.filter {
            val dates = it.value
            val bill = it.key
            val lastPastDate = dates.filter { it1 -> it1.isBeforeNow }.sortedDescending().firstOrNull()

            lastPastDate != null && Withdrawals.select {
                (Withdrawals.billId eq bill.id) and (Withdrawals.withdrawalDate greaterEq lastPastDate)
            }.count() == 0
        }
    }

    /**Fetch all bills due for deposit
     *
     * due for deposit means that the bill is due to be paid at least once this pay period and has not
     * yet been deposited.
     */
    suspend fun getDueBills(user: User): Map<Bill, List<DateTime>> = DatabaseFactory.dbQuery {
        val thisPeriodStart = getCurrentPeriodStart(DateTime(), user)

        val billTable = Bills.alias("bills")
        val billId = billTable[Bills.id]

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

        bills.map {
            it to calculatePaymentsThisPeriod(
                thisPeriodStart,
                thisPeriodStart.plusMonths(1),
                it.periodFrequency,
                it.periodType,
                it.startDate
            )
        }.filter { it.second.count() > 0 }.toMap()
    }

    suspend fun addBill(bill: NewBill, userId: Int): Bill? = DatabaseFactory.dbQuery {
        val billId = Bills.insert {
            it[Bills.userId] = userId
            it[name] = bill.name
            it[amount] = bill.amount
            it[periodType] = bill.periodType
            it[periodFrequency] = bill.periodFrequency
            it[startDate] = bill.startDate
        } get Bills.id

        if (billId != null) {
            getBillById(billId)
        } else null
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
