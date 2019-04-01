package com.bilbo.service

import com.bilbo.model.Bill
import com.bilbo.model.Bills
import com.bilbo.model.NewBill
import io.ktor.util.KtorExperimentalAPI
import org.jetbrains.exposed.sql.*

@KtorExperimentalAPI
class BillService {

    suspend fun getBills(userId: Int): List<Bill> = DatabaseFactory.dbQuery {
        Bills.select {
            Bills.userId eq userId
        }.map { toBill(it) }
    }

    suspend fun addBill(bill: NewBill) = DatabaseFactory.dbQuery {
        Bills.insert {
            it[userId] = bill.userId
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