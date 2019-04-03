package com.bilbo.model

import org.jetbrains.exposed.sql.Column
import org.jetbrains.exposed.sql.Table
import org.joda.time.DateTime


object Deposits: Table() {
    val id: Column<Int> = integer("id").autoIncrement().primaryKey()
    val amount: Column<Int> = integer("amount")
    val billId: Column<Int> = integer("bill_id") references Bills.id
    val depositDate: Column<DateTime> = datetime("deposit_date")
}

data class Deposit(
    val id: Int,
    val amount: Int,
    val billId: Int,
    val depositDate: DateTime
)