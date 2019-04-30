package com.bilbo.model

import org.jetbrains.exposed.sql.Column
import org.jetbrains.exposed.sql.Table


object Users: Table() {
    val id: Column<Int> = integer("id").autoIncrement().primaryKey()
    val email: Column<String> = varchar("email", 100).uniqueIndex()
    val password: Column<String> = varchar("password", 100)
    val monzo_token: Column<String?> = varchar("monzo_token", 300).nullable()
    val monzo_refresh_token: Column<String?> = varchar("monzo_refresh_token", 300).nullable()
    val monzo_state: Column<String?> = varchar("monzo_state", 200).nullable()
    val main_account_id: Column<String?> = varchar("main_account_id", 200).nullable()
    val bilbo_pot_id: Column<String?> = varchar("bilbo_pot_id", 200).nullable()
    val pot_deposit_day: Column<Int?> = integer("pot_deposit_day").nullable()
    val active: Column<Boolean> = bool("active")
    val whitelisted: Column<Boolean> = bool("whitelisted")
}

data class LoginCredentials(
    val id: Int,
    val email: String,
    val password: String
)

data class User(
    val id: Int,
    val email: String,
    val monzoToken: String?,
    val monzoRefreshToken: String?,
    val monzoState: String?,
    val mainAccountId: String?,
    val bilboPotId: String?,
    val potDepositDay: Int?,
    val active: Boolean,
    val whitelisted: Boolean
)

data class NewUser(
    val email: String,
    val password: String,
    val monzoToken: String?,
    val monzoRefreshToken: String?,
    val mainAccountId: String?,
    val bilboPotId: String?,
    val potDepositDay: Int?
)

data class LoginRegister(val email: String, val password: String)
