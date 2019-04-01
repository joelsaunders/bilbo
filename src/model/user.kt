package com.bilbo.model

import org.jetbrains.exposed.sql.Column
import org.jetbrains.exposed.sql.Table


object Users: Table() {
    val id: Column<Int> = integer("id").autoIncrement().primaryKey()
    val email: Column<String> = varchar("email", 100).uniqueIndex()
    val password: Column<String> = varchar("password", 100)
    val monzo_token: Column<String> = varchar("monzo_token", 300)
    val main_account_id: Column<String> = varchar("main_account_id", 200)
}

data class User(
    val id: Int,
    val email: String,
    val password: String,
    val monzoToken: String?,
    val mainAccountId: String?
)

data class NewUser(
    val id: Int?,
    val email: String,
    val password: String,
    val monzoToken: String?,
    val mainAccountId: String?
)

class LoginRegister(val email: String, val password: String)

class userUpdate(val monzoToken: String?, val mainAccountId: String?)
