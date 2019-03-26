package com.bilbo.model

import org.jetbrains.exposed.sql.Column
import org.jetbrains.exposed.sql.Table


object Users: Table() {
    val id: Column<Int> = integer("id").autoIncrement().primaryKey()
    val email: Column<String> = varchar("email", 100).uniqueIndex()
    val password: Column<String> = varchar("password", 100)
}

data class User(
    val id: Int,
    val email: String,
    val password: String
)

data class NewUser(
    val id: Int?,
    val email: String,
    val password: String
)

class LoginRegister(val email: String, val password: String)