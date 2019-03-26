package com.bilbo.model

import org.jetbrains.exposed.sql.Column
import org.jetbrains.exposed.sql.Table
import org.joda.time.DateTime


object Bills: Table() {
    val id: Column<Int> = integer("id").autoIncrement().primaryKey()
    val userId: Column<Int> = integer("user_id") references Users.id
    val name: Column<String> = varchar("name", 50)
    val amount: Column<Int> = integer("amount")
    val dueDate: Column<DateTime> = datetime("due_date")

    init {
        index(true, userId, name)
    }
}

data class Bill(
    val id: Int,
    val userId: Int,
    val name: String,
    val amount: Int,
    val dueDate: DateTime
)

data class NewBill(
    val id: Int?,
    val userId: Int,
    val name: String,
    val amount: Int,
    val dueDate: DateTime
)