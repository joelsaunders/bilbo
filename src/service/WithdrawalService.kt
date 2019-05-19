package com.bilbo.service

import com.bilbo.model.Bills
import com.bilbo.model.Withdrawal
import com.bilbo.model.Withdrawals
import com.bilbo.service.DatabaseFactory.dbQuery
import io.ktor.util.KtorExperimentalAPI
import org.jetbrains.exposed.sql.ResultRow
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.select
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

    suspend fun getWithdrawals(userId: Int, date: DateTime) = dbQuery {
        (Withdrawals leftJoin Bills).select {
            (
                (Withdrawals.withdrawalDate greaterEq date) and
                (Withdrawals.withdrawalDate less date.plusDays(1)) and
                (Withdrawals.billId eq Bills.id) and
                (Bills.userId eq userId)
            )
        }.mapNotNull { toWithdrawal(it) }
    }

    suspend fun getWithdrawals(userId: Int) = dbQuery {
        (Withdrawals leftJoin Bills).select {
            (
                (Withdrawals.billId eq Bills.id) and
                (Bills.userId eq userId)
            )
        }.mapNotNull { toWithdrawal(it) }
    }

    private fun toWithdrawal(row: ResultRow): Withdrawal =
            Withdrawal(
                id = row[Withdrawals.id],
                withdrawalDate = row[Withdrawals.withdrawalDate],
                success = row[Withdrawals.success],
                billId = row[Withdrawals.billId]
            )
}