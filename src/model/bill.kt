package com.bilbo.model

import org.jetbrains.exposed.sql.Column
import org.jetbrains.exposed.sql.Table


object Bills: Table() {
    val id: Column<Int> = integer("id").autoIncrement().primaryKey()
    val userId: Column<Int> = integer("user_id") references Users.id
    val name: Column<String> = varchar("name", 50)
    val amount: Column<Int> = integer("amount")
    val dueDayOfMonth: Column<Int> = integer("due_day_of_month")

    init {
        index(true, userId, name)
    }
}

data class Bill(
    val id: Int,
    val userId: Int,
    val name: String,
    val amount: Int,
    val dueDayOfMonth: Int
)

data class NewBill(
    val name: String,
    val amount: Int,
    val dueDayOfMonth: Int
)