package com.bilbo.service

import com.bilbo.model.LoginCredentials
import com.bilbo.model.NewUser
import com.bilbo.model.User
import com.bilbo.model.Users
import com.bilbo.service.DatabaseFactory.dbQuery
import io.ktor.util.KtorExperimentalAPI
import org.jetbrains.exposed.sql.*
import org.mindrot.jbcrypt.BCrypt


@KtorExperimentalAPI
class UserService {

    suspend fun getReadyUsers(): List<User> = dbQuery {
        Users.select {
            Users.main_account_id.isNotNull() and
            Users.bilbo_pot_id.isNotNull() and
            Users.monzo_token.isNotNull() and
            Users.pot_deposit_day.isNotNull() and
            Users.monzo_refresh_token.isNotNull()
        }.mapNotNull { toUser(it) }
    }

    suspend fun getUserCredentials(email: String): LoginCredentials? = dbQuery {
        Users.select {
            (Users.email eq email)
        }.mapNotNull {
            LoginCredentials(
                id = it[Users.id],
                email = it[Users.email],
                password = it[Users.password]
            )
        }.singleOrNull()
    }

    private fun _getUserById(id: Int): User? {
        return Users.select {
            (Users.id eq id)
        }.mapNotNull { toUser(it) }
            .singleOrNull()
    }

    suspend fun getUserById(id: Int): User? = dbQuery {
        _getUserById(id)
    }

    suspend fun getUserByState(state: String): User? = dbQuery {
        Users.select {
            (Users.monzo_state eq state)
        }.mapNotNull { toUser(it) }
            .singleOrNull()
    }

    suspend fun updateUser(id: Int, updatedUser: User): User? = dbQuery {
        Users.update({Users.id eq id}) {
            it[monzo_token] = updatedUser.monzoToken
            it[monzo_refresh_token] = updatedUser.monzoRefreshToken
            it[monzo_state] = updatedUser.monzoState
            it[main_account_id] = updatedUser.mainAccountId
            it[bilbo_pot_id] = updatedUser.bilboPotId
            it[pot_deposit_day] = updatedUser.potDepositDay
        }
        _getUserById(id)
    }

    suspend fun createUser(user: NewUser): User? = dbQuery {
        val userId = Users.insert {
            it[email] = user.email
            it[password] = BCrypt.hashpw(
                user.password, BCrypt.gensalt()
            )
            it[monzo_token] = user.monzoToken
            it[monzo_refresh_token] = user.monzoRefreshToken
            it[main_account_id] = user.mainAccountId
            it[bilbo_pot_id] = user.bilboPotId
            it[pot_deposit_day] = user.potDepositDay
        } get Users.id
        if (userId != null) {
            _getUserById(userId)
        } else null
    }

    private fun toUser(row: ResultRow): User =
        User(
            id = row[Users.id],
            email = row[Users.email],
            monzoToken = row[Users.monzo_token],
            monzoRefreshToken = row[Users.monzo_refresh_token],
            monzoState = row[Users.monzo_state],
            mainAccountId = row[Users.main_account_id],
            bilboPotId = row[Users.bilbo_pot_id],
            potDepositDay = row[Users.pot_deposit_day]
        )
}