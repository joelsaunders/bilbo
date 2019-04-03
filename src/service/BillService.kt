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

    suspend fun getDueBills(): List<Bill> = DatabaseFactory.dbQuery {
        Bills.select {
            (Bills.dueDate greater DateTime()) and
            (Bills.dueDate less DateTime().plusHours(3))
            // and bill not yet withdrawn
        }.map { toBill(it) }
    }

    suspend fun getBillsDueForDeposit(now: DateTime): List<Bill> = DatabaseFactory.dbQuery {
        val billTable = Bills.alias("bills")

        billTable.select {
            notExists(
                Deposits.select{
                    (Deposits.depositDate.month() eq now.monthOfYear) and (Deposits.billId eq billTable[Bills.id])
                }
            )
        }.map { toBill(it) }
    }

    suspend fun addBill(bill: NewBill, userId: Int) = DatabaseFactory.dbQuery {
        Bills.insert {
            it[Bills.userId] = userId
            it[name] = bill.name
            it[amount] = bill.amount
            it[dueDate] = bill.dueDate
        }
    }

    suspend fun updateBill(bill: Bill) = DatabaseFactory.dbQuery {
        Bills.update({Bills.id eq bill.id}) {
            it[userId] = bill.userId
            it[name] = bill.name
            it[amount] = bill.amount
            it[dueDate] = bill.dueDate
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
            dueDate = row[Bills.dueDate]
        )
}