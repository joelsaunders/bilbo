package com.bilbo.model

import org.jetbrains.exposed.sql.Column
import org.jetbrains.exposed.sql.Table
import org.joda.time.DateTime


object Withdrawals: Table() {
    val id: Column<Int> = integer("id").autoIncrement().primaryKey()
    val withdrawalDate: Column<DateTime> = datetime("withdrawal_date")
    val success: Column<Boolean> = bool("success")
    val billId: Column<Int> = integer("bill_id") references Bills.id
}

data class Withdrawal(
    val id: Int,
    val withdrawalDate: DateTime,
    val success: Boolean,
    val billId: Int
)