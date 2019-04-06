package com.bilbo.service

import com.bilbo.model.Deposit
import com.bilbo.model.Deposits
import org.joda.time.DateTime
import com.bilbo.service.DatabaseFactory.dbQuery
import io.ktor.util.KtorExperimentalAPI
import org.jetbrains.exposed.sql.*


@KtorExperimentalAPI
class DepositService {

    suspend fun makeDeposit(billId: Int, amount: Int) = dbQuery {
        Deposits.insert {
            it[Deposits.amount] = amount
            it[Deposits.billId] = billId
            it[Deposits.depositDate] = DateTime()
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