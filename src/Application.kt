package com.bilbo

import com.bilbo.model.LoginRegister
import com.bilbo.model.NewBill
import com.bilbo.model.User
import com.bilbo.service.*
import com.fasterxml.jackson.databind.SerializationFeature
import com.fasterxml.jackson.datatype.joda.JodaModule
import io.ktor.application.Application
import io.ktor.application.call
import io.ktor.application.install
import io.ktor.auth.Authentication
import io.ktor.auth.UserIdPrincipal
import io.ktor.auth.authenticate
import io.ktor.auth.jwt.jwt
import io.ktor.auth.principal
import io.ktor.features.ContentNegotiation
import io.ktor.jackson.jackson
import io.ktor.request.receive
import io.ktor.response.respond
import io.ktor.routing.*
import io.ktor.util.KtorExperimentalAPI
import org.joda.time.DateTime
import org.mindrot.jbcrypt.BCrypt
import SimpleJWT
import io.ktor.application.ApplicationCall


fun main(args: Array<String>): Unit = io.ktor.server.netty.EngineMain.main(args)

fun extractUserId(call: ApplicationCall): Int {
    val principal = call.principal<UserIdPrincipal>() ?: error("No principal")
    return principal.name.toInt()
}

@KtorExperimentalAPI
@Suppress("unused") // Referenced in application.conf
@kotlin.jvm.JvmOverloads
fun Application.module(testing: Boolean = false) {
    DatabaseFactory.init()
    val simpleJwt = SimpleJWT(environment.config.property("jwt.secret").getString())
    val userService = UserService()
    val billService = BillService()
    val monzoService = MonzoApiService()
    val schedulerService = SchedulerService()
    schedulerService.init()

    install(ContentNegotiation) {
        jackson {
            enable(SerializationFeature.INDENT_OUTPUT)
            registerModule(JodaModule())
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

    routing {
        this@routing.billRoutes(userService, billService)
        this@routing.userRoutes(userService, monzoService)

        post("/login") {
            val post = call.receive<LoginRegister>()

            val user = userService.getUser(post.email)
            if (user == null || !BCrypt.checkpw(post.password, user.password)) {
                throw Error("Invalid Credentials")
            }

            call.respond(mapOf("token" to simpleJwt.sign(user.id.toString())))
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

        post("/bills") {
            val userId = extractUserId(call)
            val post = call.receive<NewBill>()
            val bill = billService.addBill(post, userId)?: error("bill could not be created")
            call.respond(bill)
        }

        get("/bills/due-for-deposit") {
            val userId = extractUserId(call)
            val user = userService.getUserById(userId)?: error("user with id $userId could not be found")
            val bills = billService.getBillsDueForDeposit(DateTime(), user)
            call.respond(bills)
        }
    }
}

@KtorExperimentalAPI
fun Routing.userRoutes(userService: UserService, monzoService: MonzoApiService) {
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

        get("/user" ) {
            call.respond(userService.getAllUsers())
        }

        put("/user") {
            val userId = extractUserId(call)
            val post = call.receive<User>()
            val user = userService.updateUser(userId, post)?: error("user could not be updated")
            call.respond(user)
        }

        post("/user") {
            val post = call.receive<User>()
            val user = userService.createUser(post)?: error("user could not be created")
            call.respond(user)
        }

        /**
         * User must first go to:
         * https://auth.monzo.com/?client_id=$client_id&redirect_uri=$redirect_uri&response_type=code&state=$state_token
         * get the access code and then come to this endpoint with the access code
         */
        get("user/monzo-login") {
            val userId = extractUserId(call)
            val code: String = call.request.queryParameters["code"] ?: error("code is required")
            val user = userService.getUserById(userId)?: error("user with id $userId could not be found")
            monzoService.oAuthLogin(code, user)
            call.respond("monzo login successful")
        }

        get("user/monzo-refresh") {
            val userId = extractUserId(call)
            val user = userService.getUserById(userId)?: error("user with id $userId could not be found")
            val updatedUser = monzoService.refreshToken(user)
            call.respond(updatedUser)
        }
    }
}