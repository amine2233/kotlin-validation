package sample

import io.github.amine2233.validation.*
import io.ktor.http.HttpStatusCode
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.Application
import io.ktor.server.application.install
import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
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
            add(SignUpRequest::password, Validator.count(min = 8) and Validator.alphanumeric)
            add(SignUpRequest::age, Validator.range(min = 18))
            add(SignUpRequest::email, emailIsFree)
        }
}

@Serializable
data class FieldError(
    val path: String,
    val reason: String,
)

@Serializable
data class ErrorResponse(
    val error: String,
    val errors: List<FieldError>,
)

fun ValidationError.toResponse() =
    ErrorResponse(
        error = reason,
        errors = flatten().map { FieldError(it.path.joinToString("."), it.reason) },
    )

fun Application.module() {
    install(ContentNegotiation) { json() }
    install(StatusPages) {
        exception<ValidationError> { call, error ->
            call.respond(HttpStatusCode.BadRequest, error.toResponse())
        }
    }
    routing {
        post("/signup") {
            val request = call.receive<SignUpRequest>()
            request.validate()
            call.respond(HttpStatusCode.Created, request)
        }
    }
}

fun main() {
    embeddedServer(Netty, port = 8080, module = Application::module).start(wait = true)
}
