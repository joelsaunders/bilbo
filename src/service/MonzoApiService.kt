package com.bilbo.service

import com.bilbo.model.User
import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.datatype.joda.JodaModule
import com.typesafe.config.ConfigFactory
import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.client.features.BadResponseStatusException
import io.ktor.client.features.json.JacksonSerializer
import io.ktor.client.features.json.JsonFeature
import io.ktor.client.request.*
import io.ktor.client.request.forms.FormDataContent
import io.ktor.config.HoconApplicationConfig
import io.ktor.http.Parameters
import io.ktor.util.KtorExperimentalAPI
import mu.KotlinLogging
import java.net.URL
import java.util.*


private val logger = KotlinLogging.logger {}


@JsonIgnoreProperties(ignoreUnknown = true)
data class MonzoAccount(
    val id: String,
    val description: String,
    val type: String
)

data class MonzoAccountList(
    val accounts: List<MonzoAccount>
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class MonzoPot(
    val id: String,
    val name: String
)

data class MonzoPotList(
    val pots: List<MonzoPot>
)

data class MonzoDeposit(
    val source_account_id: String,
    val amount: String,
    val dedupe_id: String
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class TokenRefresh(
    val access_token: String,
    val client_id: String,
    val expires_in: String,
    val refresh_token: String,
    val token_type: String,
    val user_id: String
)

@KtorExperimentalAPI
class MonzoApiService {
    val appConfig = HoconApplicationConfig(ConfigFactory.load())
    val clientId = appConfig.property("monzo.clientId").getString()
    private val secret = appConfig.property("monzo.clientSecret").getString()
    private val baseUrl = appConfig.property("monzo.baseApiUrl").getString()
    private val userService = UserService()

    val httpClient = HttpClient(CIO) {
        install(JsonFeature) {
            serializer = JacksonSerializer {
                registerModule(JodaModule())
            }
        }
        expectSuccess = true
        engine {
            endpoint.apply {
                connectTimeout = 5000 // Number of milliseconds to wait trying to connect to the server.
                connectRetryAttempts = 5 // Maximum number of attempts for retrying a connection.
            }
        }
    }

    suspend fun oAuthLogin(accessCode: String, user: User) {
        val tokenResponse = httpClient.post<TokenRefresh>("$baseUrl/oauth2/token") {
            body = FormDataContent(
                Parameters.build {
                    append("grant_type", "authorization_code")
                    append("client_id", clientId)
                    append("client_secret", secret)
                    append("redirect_uri", "https://bilbo.thebookofjoel.com/user/monzo-login")
                    append("code", accessCode)
                }
            )
        }
        val updatedUser = user.copy(
            monzoRefreshToken = tokenResponse.refresh_token,
            monzoToken = tokenResponse.access_token
        )
        userService.updateUser(updatedUser.id, updatedUser)
    }

    private suspend fun <T> refreshTokenWrapper(user: User, request: suspend (user: User) -> T): T {
        return try {
            logger.info{ "Wrapper entered" }
            val response = request(user)
            logger.info{ "Wrapper exited" }
            response
        } catch (e: BadResponseStatusException) {
            if (e.statusCode.value == 401) {
                logger.info { "Monzo request failed with status 401 for user ${user.id}" }
                val refreshedUser = refreshToken(user)
                return refreshTokenWrapper(refreshedUser, request)
            } else throw e
        }
    }

    suspend fun refreshToken(user: User): User {
        return try {
            logger.info { "Refreshing monzo token for user ${user.id}" }
            val tokenRefresh = httpClient.post<TokenRefresh>("$baseUrl/oauth2/token") {
                body = FormDataContent(
                    Parameters.build {
                        append("grant_type", "refresh_token")
                        append("client_id", clientId)
                        append("client_secret", secret)
                        append("refresh_token", user.monzoRefreshToken!!)
                    }
                )
            }
            val updatedUser = user.copy(
                monzoRefreshToken = tokenRefresh.refresh_token,
                monzoToken = tokenRefresh.access_token
            )
            val savedUser = userService.updateUser(updatedUser.id, updatedUser)!!
            logger.info {
                "Token refresh successful for user ${user.id}" +
                        " token ${savedUser.monzoToken}, ${tokenRefresh.access_token}" +
                        " refresh token ${savedUser.monzoRefreshToken}, ${tokenRefresh.refresh_token}"
            }
            savedUser
        } catch (e: BadResponseStatusException) {
            if (e.statusCode.value == 401) {
                logger.info { "Token refresh received 401 for user ${user.id}, setting monzo token to null" }
                val updatedUser = user.copy(
                    monzoToken = null
                )
                userService.updateUser(updatedUser.id, updatedUser)!!
                throw UnsuccessfulTokenRefresh
            }
            throw e
        }
    }

    suspend fun listAccounts(user: User): MonzoAccountList {
        return refreshTokenWrapper(user) {
            httpClient.get<MonzoAccountList>("$baseUrl/accounts") {
                headers {
                    append("Authorization", "Bearer ${it.monzoToken}")
                }
            }
        }
    }

    suspend fun listPots(user: User): MonzoPotList {
        return refreshTokenWrapper(user) {
            httpClient.get<MonzoPotList>("$baseUrl/pots") {
                headers {
                    append("Authorization", "Bearer ${it.monzoToken}")
                }
            }
        }
    }

    suspend fun postFeedItem(user: User, title: String, postBody: String) {
        val requestUrl = "$baseUrl/feed"

        logger.info { "Posting feed item for user ${user.id}" }
        refreshTokenWrapper(user) {
            httpClient.post<Unit> {
                url(URL(requestUrl))
                body = FormDataContent(
                    Parameters.build {
                        append("account_id", it.mainAccountId!!)
                        append("type", "basic")
                        append("params[title]", title)
                        append("params[body]", postBody)
                        append(
                            "params[image_url]",
                            "https://media.ntslive.co.uk/crop/430x430/7be5a6a5-dd54-4311-8428-f9cb8d661414_1530144000.png"
                        )
                    }
                )
                headers {
                    append("Authorization", "Bearer ${it.monzoToken}")
                }
            }
        }
        logger.info { "Finished posting feed item for user ${user.id}" }
    }

    suspend fun withdrawFromBilboPot(user: User, amount: Int) {
        val requestUrl = "$baseUrl/pots/${user.bilboPotId}/withdraw"

        logger.info { "posting withdrawal for user ${user.id} to $requestUrl with  amount: $amount" }
        refreshTokenWrapper(user) {
            httpClient.put<Unit> {
                url(URL(requestUrl))
                body = FormDataContent(
                    Parameters.build {
                        append("destination_account_id", it.mainAccountId!!)
                        append("amount", amount.toString())
                        append("dedupe_id", UUID.randomUUID().toString())
                    }
                )
                headers {
                    append("Authorization", "Bearer ${it.monzoToken}")
                }
            }
        }
        logger.info { "Withdraw for user ${user.id} finished" }
    }

    suspend fun depositIntoBilboPot(user: User, deposit: MonzoDeposit) {
        val requestUrl = "$baseUrl/pots/${user.bilboPotId}/deposit"

        logger.debug { "posting deposit for user ${user.id} to $requestUrl with $deposit" }
        refreshTokenWrapper(user) {
            httpClient.put<Unit> {
                url(URL(requestUrl))
                body = FormDataContent(
                    Parameters.build {
                        append("source_account_id", deposit.source_account_id)
                        append("amount", deposit.amount)
                        append("dedupe_id", deposit.dedupe_id)
                    }
                )
                headers {
                    append("Authorization", "Bearer ${it.monzoToken}")
                }
            }
        }
        logger.info { "Deposit for user ${user.id} finished" }
    }
}

object UnsuccessfulTokenRefresh : Throwable()
