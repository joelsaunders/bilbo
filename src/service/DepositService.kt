package com.bilbo.service

import com.bilbo.model.Deposit
import com.bilbo.model.Deposits
import org.joda.time.DateTime
import com.bilbo.service.DatabaseFactory.dbQuery
import io.ktor.util.KtorExperimentalAPI
import org.jetbrains.exposed.sql.*


@KtorExperimentalAPI
class DepositService {

    suspend fun getDepositByMonth(now: DateTime, billId: Int): List<Deposit> = dbQuery {
        // get any deposits of a certain bill id made in this month
        Deposits.select {
            (Deposits.depositDate.month() eq now.monthOfYear) and
            (Deposits.billId eq billId)
        }.map { toDeposit(it) }
    }

    suspend fun makeDeposit(billId: Int, amount: Int) = dbQuery {
        Deposits.insert {
            it[Deposits.amount] = amount
            it[Deposits.billId] = billId
        }
    }

    private fun toDeposit(row: ResultRow): Deposit =
        Deposit(
            id = row[Deposits.id],
            amount = row[Deposits.amount],
            billId = row[Deposits.billId],
            depositDate = row[Deposits.depositDate]
        )
}