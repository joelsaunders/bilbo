package com.bilbo.service

import com.bilbo.model.*
import io.ktor.util.KtorExperimentalAPI
import org.jetbrains.exposed.sql.*
import org.joda.time.DateTime

@KtorExperimentalAPI
class BillService {

    suspend fun getBills(userId: Int): List<Bill> = DatabaseFactory.dbQuery {
        Bills.select {
            (Bills.userId eq userId)
        }.map { toBill(it) }
    }

//    suspend fun getDueBills(): List<Bill> = DatabaseFactory.dbQuery {
//        Bills.select {
//            (Bills.dueDate greater DateTime()) and
//            (Bills.dueDate less DateTime().plusHours(3))
//            // and bill not yet withdrawn
//        }.map { toBill(it) }
//    }

    /**
     * Fetch all bills due for deposit
     *
     * Due for deposit means that there is no deposit this month with that bill id
     */
    suspend fun getBillsDueForDeposit(now: DateTime, userId: Int): List<Bill> = DatabaseFactory.dbQuery {
        val billTable = Bills.alias("bills")
        val thisMonthStart = DateTime(
            now.year,
            now.monthOfYear().get(),
            1,
            0,
            0
        )

        val billId = billTable[Bills.id]

        val ids = billTable.select {
            (
                    (Bills.userId eq userId) and
                    (notExists(
                        Deposits.select{
                            (Deposits.depositDate greaterEq thisMonthStart) and (Deposits.billId eq billId)
                        }
                    ))
            )
        }.map { it[billId] }

        Bills.select {
            (Bills.id inList ids)
        }.map { toBill(it) }
    }

    suspend fun addBill(bill: NewBill, userId: Int) = DatabaseFactory.dbQuery {
        Bills.insert {
            it[Bills.userId] = userId
            it[name] = bill.name
            it[amount] = bill.amount
            it[dueDayOfMonth] = bill.dueDayOfMonth
        }
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