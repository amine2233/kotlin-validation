package sample

import io.github.amine2233.validation.*
import io.github.amine2233.validation.ktor.asyncValidatable
import io.ktor.http.HttpStatusCode
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.Application
import io.ktor.server.application.install
import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty
import io.ktor.server.plugins.BadRequestException
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.plugins.requestvalidation.RequestValidation
import io.ktor.server.plugins.requestvalidation.RequestValidationException
import io.ktor.server.plugins.statuspages.StatusPages
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.post
import io.ktor.server.routing.routing
import kotlinx.serialization.Serializable

// ponytail: stands in for the account service; swap in a real HTTP/DB call, the rule does not change.
private val registeredEmails = setOf("taken@vapor.codes")

private suspend fun isRegistered(email: String): Boolean = email in registeredEmails

/** The remote rule: only reached once the local rules pass, so a malformed email costs no request. */
private val emailIsFree =
    AsyncValidator<String>("not already registered") { email ->
        if (isRegistered(email)) throw BasicValidationError("is already registered")
    }

@Serializable
data class SignUpRequest(
    val email: String,
    val password: String,
    val age: Int,
) : AsyncValidatable<SignUpRequest> {
    override fun validations() =
        asyncValidations<SignUpRequest> {
            add(SignUpRequest::email, Validator.email)
            add(SignUpRequest::password, Validator.length(min = 8) and Validator.alphanumeric)
            add(SignUpRequest::age, Validator.range(min = 18))
            add(SignUpRequest::email, emailIsFree)
        }
}

@Serializable
data class ErrorResponse(
    val error: String,
    val errors: List<String>,
) {
    constructor(errors: List<String>) : this(errors.joinToString(", "), errors)
}

fun Application.module() {
    install(ContentNegotiation) { json() }
    install(RequestValidation) { asyncValidatable<SignUpRequest>() }
    install(StatusPages) {
        exception<RequestValidationException> { call, cause ->
            call.respond(HttpStatusCode.BadRequest, ErrorResponse(cause.reasons))
        }
        exception<BadRequestException> { call, _ ->
            call.respond(HttpStatusCode.BadRequest, ErrorResponse(listOf("malformed request body")))
        }
    }
    routing {
        post("/signup") {
            val request = call.receive<SignUpRequest>()
            call.respond(HttpStatusCode.Created, request)
        }
    }
}

fun main() {
    embeddedServer(Netty, port = 8080, module = Application::module).start(wait = true)
}
