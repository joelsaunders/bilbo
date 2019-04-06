package com.bilbo.service

import com.bilbo.model.*
import io.ktor.util.KtorExperimentalAPI
import org.jetbrains.exposed.sql.*
import org.joda.time.DateTime
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

    /**
     * Fetch all bills due for withdrawal
     *
     * Due for withdrawal means that the bill due day is today and there is no
     * withdrawal for that bill this month.
     *
     * TODO: Also the deposit for this month must have been made.
     */
    suspend fun getBillsDueForWithdrawal(now: DateTime, user: User): List<Bill> = DatabaseFactory.dbQuery {
        val billTable = Bills.alias("bills")
        val thisMonthStart = DateTime(
            now.year,
            now.monthOfYear().get(),
            user.potDepositDay!!,
            0,
            0
        )

        val billIdCol = billTable[Bills.id]

        val ids = billTable.select {
            (
                (Bills.dueDayOfMonth eq now.dayOfMonth.absoluteValue) and
                (Bills.userId eq user.id!!) and
                    (notExists(
                        Withdrawals.select{
                            (
                                (Withdrawals.withdrawalDate greaterEq thisMonthStart) and
                                (Withdrawals.billId eq billIdCol)

                            )
                        }
                    ))
                )
        }.map { it[billIdCol] }

        if (ids.isNotEmpty()) {
            Bills.select {
                (Bills.id inList ids)
            }.map { toBill(it) }
        } else listOf()
    }

    /**
     * Fetch all bills due for deposit
     *
     * Due for deposit means that there is no deposit this month with that bill id
     */
    suspend fun getBillsDueForDeposit(now: DateTime, user: User): List<Bill> = DatabaseFactory.dbQuery {
        val billTable = Bills.alias("bills")
        val thisMonthStart = DateTime(
            now.year,
            now.monthOfYear().get(),
            user.potDepositDay!!,
            0,
            0
        )

        val billId = billTable[Bills.id]

        val ids = billTable.select {
            (
                (Bills.userId eq user.id!!) and
                (notExists(
                    Deposits.select{
                        (
                            (Deposits.depositDate greaterEq thisMonthStart) and
                            (Deposits.billId eq billId)
                        )
                    }
                ))
            )
        }.map { it[billId] }

        if (ids.isNotEmpty()) {
            Bills.select {
                (Bills.id inList ids)
            }.map { toBill(it) }
        } else listOf()
    }

    suspend fun addBill(bill: NewBill, userId: Int): Bill? = DatabaseFactory.dbQuery {
        val billId = Bills.insert {
            it[Bills.userId] = userId
            it[name] = bill.name
            it[amount] = bill.amount
            it[dueDayOfMonth] = bill.dueDayOfMonth
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
            it[dueDayOfMonth] = bill.dueDayOfMonth
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
            dueDayOfMonth = row[Bills.dueDayOfMonth]
        )
}