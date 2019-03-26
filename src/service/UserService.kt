package com.bilbo.service

import com.bilbo.model.User
import com.bilbo.model.Users
import com.bilbo.service.DatabaseFactory.dbQuery
import org.jetbrains.exposed.sql.*


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

    private fun toUser(row: ResultRow): User =
        User(
            id = row[Users.id],
            email = row[Users.email],
            password = row[Users.password]
        )
}