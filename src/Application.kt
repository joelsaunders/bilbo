package com.bilbo

import SimpleJWT
import com.bilbo.model.LoginRegister
import com.bilbo.model.NewBill
import com.bilbo.model.NewUser
import com.bilbo.model.User
import com.bilbo.service.*
import com.fasterxml.jackson.databind.SerializationFeature
import com.fasterxml.jackson.datatype.joda.JodaModule
import io.ktor.application.Application
import io.ktor.application.ApplicationCall
import io.ktor.application.call
import io.ktor.application.install
import io.ktor.auth.Authentication
import io.ktor.auth.UserIdPrincipal
import io.ktor.auth.authenticate
import io.ktor.auth.jwt.jwt
import io.ktor.auth.principal
import io.ktor.features.CORS
import io.ktor.features.ContentNegotiation
import io.ktor.features.StatusPages
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.jackson.jackson
import io.ktor.request.receive
import io.ktor.response.respond
import io.ktor.routing.*
import io.ktor.util.KtorExperimentalAPI
import org.mindrot.jbcrypt.BCrypt
import java.util.*


fun main(args: Array<String>): Unit = io.ktor.server.netty.EngineMain.main(args)

fun extractUserId(call: ApplicationCall): Int {
    val principal = call.principal<UserIdPrincipal>() ?: error("No principal")
    return principal.name.toInt()
}

@KtorExperimentalAPI
@Suppress("unused") // Referenced in application.conf
@kotlin.jvm.JvmOverloads
fun Application.module(testing: Boolean = false) {
    val rootUrl = environment.config.property("ktor.rootUrl").getString()
    DatabaseFactory.init()
    val simpleJwt = SimpleJWT(environment.config.property("jwt.secret").getString())
    val userService = UserService()
    val billService = BillService()
    val monzoService = MonzoApiService()
    val schedulerService = SchedulerService()
//    schedulerService.init()

    install(CORS) {
        method(HttpMethod.Options)
        method(HttpMethod.Get)
        method(HttpMethod.Post)
        method(HttpMethod.Put)
        method(HttpMethod.Delete)
        method(HttpMethod.Patch)
        header(HttpHeaders.Authorization)
        allowCredentials = true
        anyHost()
    }

    install(ContentNegotiation) {
        jackson {
            enable(SerializationFeature.INDENT_OUTPUT)
            registerModule(JodaModule())
//            dateFormat = DateFormat.getDateInstance()
        }
    }
    install(Authentication) {
        jwt {
            verifier(simpleJwt.verifier)
            validate {
                UserIdPrincipal(it.payload.getClaim("name").asString())
            }
        }
    }

    install(StatusPages) {
        exception<Throwable> { cause ->
            call.respond(HttpStatusCode.InternalServerError)
            throw cause
        }

        exception<AuthException> { cause ->
            call.respond(HttpStatusCode.Unauthorized)
        }
    }


    routing {
        this@routing.billRoutes(userService, billService)
        this@routing.userRoutes(userService, monzoService, rootUrl)
        this@routing.schedulerRoutes(schedulerService)
        this@routing.debugRoutes(userService, billService)

        post("/login") {
            val post = call.receive<LoginRegister>()

            val credentials = userService.getUserCredentials(post.email)
            if (credentials == null || !BCrypt.checkpw(post.password, credentials.password)) {
                throw AuthException
            }

            call.respond(
                mapOf(
                    "token" to simpleJwt.sign(credentials.id.toString())
                )
            )
        }
    }
}

object AuthException : Throwable()

@KtorExperimentalAPI
fun Routing.debugRoutes(userService: UserService, billService: BillService) {
    authenticate {
        get("/all-users") {
            val users = userService.getAllUsers()
            call.respond(users)
        }

        get("/all-bills") {
            val bills = billService.getAllBills()
            call.respond(bills)
        }
    }
}

@KtorExperimentalAPI
fun Routing.schedulerRoutes(schedulerService: SchedulerService) {
    authenticate {
        get("/scheduler/start") {
            schedulerService.init()
            call.respond("started")
        }

        get("/scheduler/stop") {
            schedulerService.cancel()
            call.respond("cancelled")
        }
    }
}


@KtorExperimentalAPI
fun Routing.billRoutes(userService: UserService, billService: BillService) {
    authenticate {
        get("/bills") {
            val userId = extractUserId(call)
            val bills = billService.getBills(userId)
            call.respond(bills)
        }

        delete("/bills/{billId}") {
            val userId = extractUserId(call)
            val billId = call.parameters["billId"]?.toInt()!!
            billService.removeBill(billId, userId)
            call.respond(HttpStatusCode.NoContent)
        }

        post("/bills") {
            val userId = extractUserId(call)
            val post = call.receive<NewBill>()
            val bill = billService.addBill(post, userId)?: error("bill could not be created")
            call.respond(bill)
        }

        get("/bills/due-for-deposit") {
            val userId = extractUserId(call)
            val user = userService.getUserById(userId)?: error("user with id $userId could not be found")
            val bills = billService.getDueBills(user)
            call.respond(bills)
        }

        get("/bills/due-for-withdrawal") {
            val userId = extractUserId(call)
            val user = userService.getUserById(userId)?: error("user with id $userId could not be found")
            val bills = billService.getDueWithdrawals(user)
            call.respond(bills)
        }
    }
}

@KtorExperimentalAPI
fun Routing.userRoutes(userService: UserService, monzoService: MonzoApiService, rootUrl: String) {

    post("/user") {
        val post = call.receive<NewUser>()
        val user = userService.createUser(post)?: error("user could not be created")
        call.respond(user)
    }

    /**
     * User must first go to:
     * https://auth.monzo.com/?client_id=$client_id&redirect_uri=$redirect_uri&response_type=code&state=$state_token
     * get the access code and then come to this endpoint with the access code
     */
    get("/user/monzo-login") {
        val state = call.request.queryParameters["state"]!!
        val code: String = call.request.queryParameters["code"] ?: error("code is required")
        val user = userService.getUserByState(state)?: error("user with state $state could not be found")
        monzoService.oAuthLogin(code, user)
        call.respond("you are logged in!")
    }

    authenticate {
        get("/user/accounts") {
            val userId = extractUserId(call)
            val user = userService.getUserById(userId)?: error("user with id $userId could not be found")
            val accounts = monzoService.listAccounts(user)
            call.respond(accounts)
        }

        get("/user/pots") {
            val userId = extractUserId(call)
            val user = userService.getUserById(userId)?: error("user with id $userId could not be found")
            val pots = monzoService.listPots(user)
            call.respond(pots)
        }

        get("/user/current-user" ) {
            val userId = extractUserId(call)
            val user = userService.getUserById(userId)?: error("user with id $userId could not be found")
            call.respond(user)
        }

        put("/user") {
            val userId = extractUserId(call)
            val post = call.receive<User>()
            val user = userService.updateUser(userId, post)?: error("user could not be updated")
            call.respond(user)
        }

        get("/user/get-monzo-login-url") {
            val userId = extractUserId(call)
            val user = userService.getUserById(userId)?: error("user with id $userId could not be found")
            val updatedUser = user.copy(monzoState = UUID.randomUUID().toString())
            userService.updateUser(userId, updatedUser)
            val redirectUri = "$rootUrl/user/monzo-login"
            val loginUrl = "https://auth.monzo.com/?response_type=code&" +
                    "client_id=${monzoService.clientId}&" +
                    "redirect_uri=$redirectUri&" +
                    "state=${updatedUser.monzoState}"
            call.respond(
                mapOf("url" to loginUrl)
            )
        }

        get("/user/monzo-refresh") {
            val userId = extractUserId(call)
            val user = userService.getUserById(userId)?: error("user with id $userId could not be found")
            val updatedUser = monzoService.refreshToken(user)
            call.respond(updatedUser)
        }
    }
}