package com.bilbo.service

import com.bilbo.model.Users
import com.typesafe.config.ConfigFactory
import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import io.ktor.config.HoconApplicationConfig
import io.ktor.server.engine.applicationEngineEnvironment
import io.ktor.util.KtorExperimentalAPI
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.SchemaUtils.create
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.insertIgnore
import org.jetbrains.exposed.sql.transactions.transaction
import org.mindrot.jbcrypt.BCrypt


@KtorExperimentalAPI
object DatabaseFactory {

    val appConfig = HoconApplicationConfig(ConfigFactory.load())

    fun init() {
        Database.connect(hikari())
        transaction {
            Users.insertIgnore {
                it[email] = appConfig.property("db.defaultUserEmail").getString()
                it[password] = BCrypt.hashpw(
                    appConfig.property("db.defaultUserPassword").getString(), BCrypt.gensalt()
                )

            }
        }
    }

    private fun hikari(): HikariDataSource {
        val config = HikariConfig()
        config.driverClassName = "org.postgresql.Driver"
        config.jdbcUrl = appConfig.property("db.jdbcUrl").getString()
        config.username = appConfig.property("db.dbUser").getString()
        config.password = appConfig.property("db.dbPassword").getString()
        config.maximumPoolSize = 3
        config.isAutoCommit = false
        config.transactionIsolation = "TRANSACTION_REPEATABLE_READ"
        config.validate()
        return HikariDataSource(config)
    }

    suspend fun <T> dbQuery(
        block: () -> T): T =
        withContext(Dispatchers.IO) {
            transaction { block() }
        }

}