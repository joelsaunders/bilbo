package com.bilbo.service

import com.bilbo.service.DatabaseFactory.appConfig
import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.datatype.joda.JodaModule
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.client.features.json.JacksonSerializer
import io.ktor.util.KtorExperimentalAPI
import io.ktor.client.features.json.JsonFeature
import io.ktor.client.request.*
import io.ktor.client.request.forms.FormDataContent
import io.ktor.client.response.HttpResponse
import io.ktor.http.Parameters
import org.joda.time.DateTime
import java.net.URL


@JsonIgnoreProperties(ignoreUnknown = true)
data class MonzoAccount(
    val id: String,
    val description: String,
    val created: DateTime,
    val type: String
)


data class MonzoAccountList(
    val accounts: List<MonzoAccount>
)


data class MonzoDeposit(
    val source_account_id: String,
    val amount: String,
    val dedupe_id: String
)


@KtorExperimentalAPI
class MonzoApiService {
    private val secret = appConfig.property("monzo.clientSecret").getString()
    private val clientId = appConfig.property("monzo.clientId").getString()
    private val baseUrl = appConfig.property("monzo.baseApiUrl").getString()

    val httpClient = HttpClient(CIO) {
        install(JsonFeature) {
            serializer = JacksonSerializer {
                registerModule(JodaModule())
            }
        }
        engine {
            maxConnectionsCount = 1000 // Maximum number of socket connections.
            endpoint.apply {
                maxConnectionsPerRoute = 100 // Maximum number of requests for a specific endpoint route.
                pipelineMaxSize = 20 // Max number of opened endpoints.
                keepAliveTime = 5000 // Max number of milliseconds to keep each connection alive.
                connectTimeout = 5000 // Number of milliseconds to wait trying to connect to the server.
                connectRetryAttempts = 5 // Maximum number of attempts for retrying a connection.
            }
        }
    }

    suspend fun listAccounts(monzoToken: String): MonzoAccountList {
        return httpClient.get("$baseUrl/accounts") {
            headers {
                append("Authorization", "Bearer $monzoToken")
            }
        }
    }

    suspend fun postFeedItem(monzoToken: String, accountId: String, title: String, postBody: String) {
        val requestUrl = "$baseUrl/feed"

        val response = httpClient.post<HttpResponse> {
            url(URL(requestUrl))
            body = FormDataContent(
                Parameters.build {
                    append("account_id", accountId)
                    append("type", "basic")
//                    append("params", mapOf("title" to title, "body" to postBody, ""))
                    append("params[title]", title)
                    append("params[body]", postBody)
                    append("params[image_url]", "https://media.ntslive.co.uk/crop/430x430/7be5a6a5-dd54-4311-8428-f9cb8d661414_1530144000.png")
                }
            )
            headers {
                append("Authorization", "Bearer $monzoToken")
            }
        }
    }

    suspend fun depositIntoBilboPot(monzoToken: String, potId: String, deposit: MonzoDeposit) {
        val requestUrl = "$baseUrl/pots/$potId/deposit"
        val putBody = jacksonObjectMapper().writeValueAsString(deposit)

        println("posting to $requestUrl with $putBody")
        val response = httpClient.put<HttpResponse> {
            url(URL(requestUrl))
            body = FormDataContent(
                Parameters.build {
                    append("source_account_id", deposit.source_account_id)
                    append("amount", deposit.amount)
                    append("dedupe_id", deposit.dedupe_id)
                }
            )
            headers {
                append("Authorization", "Bearer $monzoToken")
            }
        }
    }
}