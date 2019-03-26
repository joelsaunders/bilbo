package com.bilbo.service

import com.bilbo.model.Bill
import com.bilbo.model.Bills
import org.jetbrains.exposed.sql.ResultRow
import org.jetbrains.exposed.sql.select

class BillService {

    suspend fun getBills(userId: Int): List<Bill> = DatabaseFactory.dbQuery {
        Bills.select {
            Bills.userId eq userId
        }.map { toBill(it) }
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