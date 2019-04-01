package com.bilbo.service

import com.bilbo.model.User
import com.bilbo.model.Users
import com.bilbo.model.userUpdate
import com.bilbo.service.DatabaseFactory.dbQuery
import io.ktor.util.KtorExperimentalAPI
import org.jetbrains.exposed.sql.*


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

    suspend fun updateUser(id: Int, updateFields: userUpdate) = dbQuery {
        Users.update({Users.id eq id}) {
            if (updateFields.monzoToken != null) {
                it[monzo_token] = updateFields.monzoToken
            }
            if (updateFields.mainAccountId != null) {
                it[main_account_id] = updateFields.mainAccountId
            }
        }
    }

    private fun toUser(row: ResultRow): User =
        User(
            id = row[Users.id],
            email = row[Users.email],
            password = row[Users.password],
            monzoToken = row[Users.monzo_token],
            mainAccountId = row[Users.main_account_id]
        )
}