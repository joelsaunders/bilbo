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
import io.ktor.routing.get
import io.ktor.routing.patch
import io.ktor.routing.post
import io.ktor.routing.routing
import io.ktor.util.KtorExperimentalAPI
import org.joda.time.DateTime
import org.mindrot.jbcrypt.BCrypt
import service.SimpleJWT


fun main(args: Array<String>): Unit = io.ktor.server.netty.EngineMain.main(args)


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
        authenticate {
            get ("/user" ) {
                call.respond(userService.getAllUsers())
            }

            get ("/bills") {
                val principal = call.principal<UserIdPrincipal>() ?: error("No principal")
                val userId = principal.name.toInt()
                val bills = billService.getBills(userId)
                call.respond(bills)
            }

            get ("/bills/due-for-deposit") {
                val principal = call.principal<UserIdPrincipal>() ?: error("No principal")
                val userId = principal.name.toInt()
                val user = userService.getUserById(userId)!!
                val bills = billService.getBillsDueForDeposit(DateTime(), user)
                call.respond(bills)
            }

            post ("/bills") {
                val principal = call.principal<UserIdPrincipal>() ?: error("No principal")
                val userId = principal.name.toInt()
                val post = call.receive<NewBill>()

                billService.addBill(post, userId)
                call.respond("")
            }

            get ("user/accounts") {
                val principal = call.principal<UserIdPrincipal>() ?: error("No principal")
                val userId = principal.name.toInt()
                val monzoToken = userService.getUserById(userId)?.monzoToken ?: error("No token for this user")
                val accounts = monzoService.listAccounts(monzoToken)
                call.respond(accounts)
            }

            patch("user") {
                val principal = call.principal<UserIdPrincipal>() ?: error("No principal")
                val userId = principal.name.toInt()
                val post = call.receive<User>()

                userService.updateUser(userId, post)
                call.respond("")
            }
        }

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

