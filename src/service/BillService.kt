package com.bilbo.service

import com.bilbo.model.*
import io.ktor.util.KtorExperimentalAPI
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.transactions.transaction
import org.joda.time.DateTime
import java.lang.IllegalArgumentException
import kotlin.math.absoluteValue

@KtorExperimentalAPI
class BillService {

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

    fun getCurrentPeriodStart(now: DateTime, user: User): DateTime {
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

    fun calculatePaymentsThisPeriod(
        periodStart: DateTime,
        periodEnd: DateTime,
        periodFrequency: Int,
        periodType: String,
        paymentStart: DateTime
    ): List<DateTime> {

        var current: DateTime = paymentStart
        var datesList = mutableListOf<DateTime>()

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
//
//    /**
//     * Fetch all monthly bills due for withdrawal
//     *
//     * Due for withdrawal means that the bill due day is today and there is no
//     * withdrawal for that bill this month.
//     *
//     */
//    suspend fun getMonthlyBillsDueForWithdrawal(now: DateTime, user: User): List<Bill> = DatabaseFactory.dbQuery {
//        val billTable = Bills.alias("bills")
//        val thisPeriodStart = getCurrentPeriodStart(now, user)
//
//        val billIdCol = billTable[Bills.id]
//
//        val ids = billTable.select {
//            (
//                    (Bills.dueDayOfMonth eq now.dayOfMonth.absoluteValue) and
//                            (Bills.userId eq user.id!!) and
//                            (notExists(
//                                Withdrawals.select{
//                                    (
//                                            (Withdrawals.withdrawalDate greaterEq thisPeriodStart) and
//                                                    (Withdrawals.billId eq billIdCol)
//
//                                            )
//                                }
//                            ))
//                    )
//        }.map { it[billIdCol] }
//
//        if (ids.isNotEmpty()) {
//            Bills.select {
//                (Bills.id inList ids)
//            }.map { toBill(it) }
//        } else listOf()
//    }

    /**
     * a due date this period is in the past and has not yet been withdrawn
     */
    suspend fun getDueWithdrawals(user: User) = DatabaseFactory.dbQuery {
        val thisPeriodStart = getCurrentPeriodStart(DateTime(), user)

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
                            (Bills.userId eq user.id!!) and
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

    suspend fun removeBill(id: Int) = DatabaseFactory.dbQuery {
        Bills.deleteWhere { Bills.id eq id }
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
