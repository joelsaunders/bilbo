package com.bilbo.service

import com.bilbo.model.User
import com.bilbo.model.Users
import com.bilbo.service.DatabaseFactory.dbQuery
import io.ktor.util.KtorExperimentalAPI
import org.jetbrains.exposed.sql.*
import org.joda.time.DateTime


@KtorExperimentalAPI
class UserService {

    suspend fun getAllUsers(): List<User> = dbQuery {
        Users.selectAll().map { toUser(it) }
    }

    suspend fun getUser(email: String): User? = dbQuery {
        Users.select {
            (Users.email eq email)
        }.mapNotNull { toUser(it) }
            .singleOrNull()
    }

    suspend fun getUserById(id: Int): User? = dbQuery {
        Users.select {
            (Users.id eq id)
        }.mapNotNull { toUser(it) }
            .singleOrNull()
    }

    suspend fun getUserByPayDay(payday: DateTime): List<User> = dbQuery {
        Users.select {
            (Users.pot_deposit_date greater DateTime()) and
            (Users.pot_deposit_date less DateTime().plusHours(3))
        }.map { toUser(it) }
    }

    suspend fun updateUser(id: Int, updatedUser: User) = dbQuery {
        Users.update({Users.id eq id}) {
            it[monzo_token] = updatedUser.monzoToken
            it[main_account_id] = updatedUser.mainAccountId
            it[bilbo_pot_id] = updatedUser.bilboPotId
            it[pot_deposit_date] = updatedUser.potDepositDate
        }
    }

    private fun toUser(row: ResultRow): User =
        User(
            id = row[Users.id],
            email = row[Users.email],
            password = row[Users.password],
            monzoToken = row[Users.monzo_token],
            mainAccountId = row[Users.main_account_id],
            bilboPotId = row[Users.bilbo_pot_id],
            potDepositDate = row[Users.pot_deposit_date]
        )
}