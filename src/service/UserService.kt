package com.bilbo.service

import com.bilbo.model.User
import com.bilbo.model.Users
import com.bilbo.service.DatabaseFactory.dbQuery
import io.ktor.util.KtorExperimentalAPI
import org.jetbrains.exposed.sql.*


@KtorExperimentalAPI
class UserService {

    suspend fun getAllUsers(): List<User> = dbQuery {
        Users.selectAll().map { toUser(it) }
    }

    suspend fun getReadyUsers(): List<User> = dbQuery {
        Users.select {
            Users.main_account_id.isNotNull() and
            Users.bilbo_pot_id.isNotNull() and
            Users.monzo_token.isNotNull() and
            Users.pot_deposit_day.isNotNull() and
            Users.monzo_refresh_token.isNotNull()
        }.mapNotNull { toUser(it) }
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

    suspend fun updateUser(id: Int, updatedUser: User) = dbQuery {
        Users.update({Users.id eq id}) {
            it[monzo_token] = updatedUser.monzoToken
            it[monzo_refresh_token] = updatedUser.monzoRefreshToken
            it[main_account_id] = updatedUser.mainAccountId
            it[bilbo_pot_id] = updatedUser.bilboPotId
            it[pot_deposit_day] = updatedUser.potDepositDay
        }
    }

    private fun toUser(row: ResultRow): User =
        User(
            id = row[Users.id],
            email = row[Users.email],
            password = row[Users.password],
            monzoToken = row[Users.monzo_token],
            monzoRefreshToken = row[Users.monzo_refresh_token],
            mainAccountId = row[Users.main_account_id],
            bilboPotId = row[Users.bilbo_pot_id],
            potDepositDay = row[Users.pot_deposit_day]
        )
}