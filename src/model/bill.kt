package com.bilbo.model

import org.jetbrains.exposed.sql.Column
import org.jetbrains.exposed.sql.Table
import org.joda.time.DateTime
import java.lang.IllegalArgumentException


object Bills: Table() {
    val id: Column<Int> = integer("id").autoIncrement().primaryKey()
    val userId: Column<Int> = integer("user_id") references Users.id
    val name: Column<String> = varchar("name", 50)
    val amount: Column<Int> = integer("amount")
    val periodType: Column<String> = varchar("period_type", 10)
    val periodFrequency: Column<Int> = integer("period_frequency")
    val startDate: Column<DateTime> = datetime("start_date")

    init {
        index(true, userId, name)
    }
}

data class Bill(
    val id: Int,
    val userId: Int,
    val name: String,
    val amount: Int,
    val periodType: String,
    val periodFrequency: Int,
    val startDate: DateTime
)

data class NewBill(
    val name: String,
    val amount: Int,
    val periodType: String,
    val periodFrequency: Int,
    val startDate: DateTime
) {
    init {
        if (!listOf("day", "week", "month").contains(periodType))
            throw IllegalArgumentException("$periodType is not a valid bill type")
    }
}