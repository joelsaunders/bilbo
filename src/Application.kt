package com.bilbo

import com.bilbo.model.LoginRegister
import com.bilbo.service.BillService
import com.bilbo.service.DatabaseFactory
import com.bilbo.service.UserService
import io.ktor.application.*
import io.ktor.response.*
import io.ktor.request.*
import io.ktor.routing.*
import com.fasterxml.jackson.databind.*
import io.ktor.auth.Authentication
import io.ktor.auth.UserIdPrincipal
import io.ktor.auth.authenticate
import io.ktor.auth.jwt.jwt
import io.ktor.auth.principal
import io.ktor.jackson.*
import io.ktor.features.*
import io.ktor.util.KtorExperimentalAPI
import org.mindrot.jbcrypt.BCrypt
import service.SimpleJWT
import java.lang.Error


fun main(args: Array<String>): Unit = io.ktor.server.netty.EngineMain.main(args)


@KtorExperimentalAPI
@Suppress("unused") // Referenced in application.conf
@kotlin.jvm.JvmOverloads
fun Application.module(testing: Boolean = false) {

    val simpleJwt = SimpleJWT(environment.config.property("jwt.secret").getString())

    install(ContentNegotiation) {
        jackson {
            enable(SerializationFeature.INDENT_OUTPUT)
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

    DatabaseFactory.init()

    val userService = UserService()
    val billService = BillService()

    routing {
        authenticate {
            get ("/users" ) {
                call.respond(userService.getAllUsers())
            }

            get ("/bills") {
                val principal = call.principal<UserIdPrincipal>() ?: error("No principal")
                val userId = principal.name.toInt()
                val bills = billService.getBills(userId)
                call.respond(bills)
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

