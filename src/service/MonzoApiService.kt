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
    private val secret = appConfig.property("monzo.clientSecret").getString()
    private val clientId = appConfig.property("monzo.clientId").getString()
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
                    append("redirect_uri", "https://www.thebookofjoel.com")
                    append("code", accessCode)
                }
            )
        }
        val updatedUser = user.copy(
            monzoRefreshToken = tokenResponse.refresh_token,
            monzoToken = tokenResponse.access_token
        )
        userService.updateUser(updatedUser.id!!, updatedUser)
    }

    private suspend fun <T>refreshTokenWrapper(user: User, counter: Int = 0, request: suspend (user: User) -> T): T {
        return try {
            request(user)
        } catch (e: BadResponseStatusException) {
            if (e.statusCode.value == 401) {
                if (counter > 2) {
                    val updatedUser = user.copy(
                        monzoToken = null
                    )
                    userService.updateUser(updatedUser.id!!, updatedUser)
                    throw e
                }
                refreshToken(user)
                val refreshedUser = userService.getUserById(user.id!!)
                return refreshTokenWrapper(refreshedUser!!, counter + 1, request)
            } else throw e
        }
    }

    suspend fun refreshToken(user: User) {
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
        userService.updateUser(updatedUser.id!!, updatedUser)
    }

    suspend fun listAccounts(user: User): MonzoAccountList {
        return refreshTokenWrapper(user){
            httpClient.get<MonzoAccountList>("$baseUrl/accounts") {
                headers {
                    append("Authorization", "Bearer ${it.monzoToken}")
                }
            }
        }
    }

    suspend fun listPots(user: User): MonzoPotList {
        return refreshTokenWrapper(user){
            httpClient.get<MonzoPotList>("$baseUrl/pots") {
                headers {
                    append("Authorization", "Bearer ${it.monzoToken}")
                }
            }
        }
    }

    suspend fun postFeedItem(user: User, title: String, postBody: String) {
        val requestUrl = "$baseUrl/feed"

        refreshTokenWrapper(user) {
            httpClient.post<Unit> {
                url(URL(requestUrl))
                body = FormDataContent(
                    Parameters.build {
                        append("account_id", user.mainAccountId!!)
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
    }

    suspend fun withdrawFromBilboPot(user: User, amount: Int) {
        val requestUrl = "$baseUrl/pots/${user.bilboPotId}/withdraw"

        logger.debug { "posting withdrawal to $requestUrl with  amount: $amount" }
        refreshTokenWrapper(user) {
            httpClient.put<Unit> {
                url(URL(requestUrl))
                body = FormDataContent(
                    Parameters.build {
                        append("destination_account_id", user.mainAccountId!!)
                        append("amount", amount.toString())
                        append("dedupe_id", UUID.randomUUID().toString())
                    }
                )
                headers {
                    append("Authorization", "Bearer ${it.monzoToken}")
                }
            }
        }
    }

    suspend fun depositIntoBilboPot(user: User, deposit: MonzoDeposit) {
        val requestUrl = "$baseUrl/pots/${user.bilboPotId}/deposit"

        logger.debug { "posting deposit to $requestUrl with $deposit" }
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
    }
}