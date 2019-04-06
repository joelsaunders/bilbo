package com.bilbo.service

import com.bilbo.model.Withdrawal
import com.bilbo.model.Withdrawals
import com.bilbo.service.DatabaseFactory.dbQuery
import io.ktor.util.KtorExperimentalAPI
import org.jetbrains.exposed.sql.ResultRow
import org.jetbrains.exposed.sql.insert
import org.joda.time.DateTime


@KtorExperimentalAPI
class WithdrawalService {

    suspend fun makeWithdrawal(billId: Int, success: Boolean) = dbQuery {
        Withdrawals.insert {
            it[Withdrawals.withdrawalDate] = DateTime()
            it[Withdrawals.success] = success
            it[Withdrawals.billId] = billId
        }
    }

    private fun toWithdrawal(row: ResultRow): Withdrawal =
            Withdrawal(
                id = row[Withdrawals.id],
                withdrawalDate = row[Withdrawals.withdrawalDate],
                success = row[Withdrawals.success],
                billId = row[Withdrawals.billId]
            )
}