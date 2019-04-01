package com.bilbo.service

import com.bilbo.service.DatabaseFactory.appConfig
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.client.features.json.JacksonSerializer
import io.ktor.util.KtorExperimentalAPI
import io.ktor.client.features.json.JsonFeature
import io.ktor.client.request.get
import io.ktor.client.request.headers
import io.ktor.client.request.post
import io.ktor.client.request.url
import io.ktor.http.ContentType
import io.ktor.http.content.TextContent
import org.joda.time.DateTime
import java.net.URL


data class MonzoAccount(
    val id: String,
    val description: String,
    val created: DateTime
)


data class MonzoAccountList(
    val accounts: List<MonzoAccount>
)


data class Deposit (
    val source_account_id: String,
    val amount: Int,
    val dedupeId: String
)


@KtorExperimentalAPI
class MonzoApiService {
    val secret = appConfig.property("monzo.clientSecret").getString()
    val clientId = appConfig.property("monzo.clientId").getString()
    val baseUrl = appConfig.property("monzo.baseApiUrl").getString()

    val httpClient = HttpClient(CIO) {
        install(JsonFeature) {
            serializer = JacksonSerializer()
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
        return httpClient.get<MonzoAccountList>("${baseUrl}/accounts") {
            headers {
                append("Authorization", "Bearer ${monzoToken}")
            }
        }
    }

    suspend fun depositIntoBilboPot(monzoToken: String, potId: Int, deposit: Deposit) {
        return httpClient.post<Unit> {
            url(URL("${baseUrl}/pots/$potId/deposit"))
            body = TextContent(
                jacksonObjectMapper().writeValueAsString(deposit),
                contentType = ContentType.Application.Json
            )
        }
    }
}