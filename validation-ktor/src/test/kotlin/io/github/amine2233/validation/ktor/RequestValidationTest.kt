package io.github.amine2233.validation.ktor

import io.github.amine2233.validation.*
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.install
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.plugins.requestvalidation.RequestValidation
import io.ktor.server.plugins.requestvalidation.RequestValidationException
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.response.respondText
import io.ktor.server.routing.post
import io.ktor.server.routing.routing
import io.ktor.server.testing.ApplicationTestBuilder
import io.ktor.server.testing.testApplication
import kotlinx.serialization.Serializable
import java.io.IOException
import kotlin.test.Test
import kotlin.test.assertEquals

@Serializable
data class User(
    val name: String,
    val age: Int,
) : Validatable<User> {
    override fun validations() =
        validations<User> {
            add(User::name, Validator.length(min = 5) and Validator.alphanumeric)
            add(User::age, Validator.range(min = 18))
        }
}

private val emailIsFree =
    AsyncValidator<String>("not already registered") { email ->
        when (email) {
            "taken@vapor.codes" -> throw BasicValidationError("is already registered")
            "down@vapor.codes" -> throw IOException("account service unavailable")
        }
    }

@Serializable
data class SignUp(
    val email: String,
) : AsyncValidatable<SignUp> {
    override fun validations() =
        asyncValidations<SignUp> {
            add(SignUp::email, Validator.email)
            add(SignUp::email, emailIsFree)
        }
}

class RequestValidationTest {
    private fun ApplicationTestBuilder.app() {
        application {
            install(ContentNegotiation) { json() }
            install(RequestValidation) {
                validatable<User>()
                asyncValidatable<SignUp>()
            }
            routing {
                post("/user") {
                    try {
                        call.respond(HttpStatusCode.Created, call.receive<User>().name)
                    } catch (exception: RequestValidationException) {
                        call.respondText(exception.reasons.joinToString("\n"), status = HttpStatusCode.BadRequest)
                    }
                }
                post("/signup") {
                    try {
                        call.respond(HttpStatusCode.Created, call.receive<SignUp>().email)
                    } catch (exception: RequestValidationException) {
                        call.respondText(exception.reasons.joinToString("\n"), status = HttpStatusCode.BadRequest)
                    }
                }
            }
        }
    }

    private suspend fun ApplicationTestBuilder.postJson(
        path: String,
        json: String,
    ) = client.post(path) {
        contentType(ContentType.Application.Json)
        setBody(json)
    }

    @Test
    fun validModelIsAccepted() =
        testApplication {
            app()
            assertEquals(HttpStatusCode.Created, postJson("/user", """{"name":"Tanner","age":30}""").status)
            assertEquals(HttpStatusCode.Created, postJson("/signup", """{"email":"tanner@vapor.codes"}""").status)
        }

    @Test
    fun invalidValidatableIsRejected() =
        testApplication {
            app()
            val response = postJson("/user", """{"name":"Tanner","age":12}""")
            assertEquals(HttpStatusCode.BadRequest, response.status)
            assertEquals("age is less than 18", response.bodyAsText())
        }

    @Test
    fun invalidAsyncValidatableIsRejected() =
        testApplication {
            app()
            val response = postJson("/signup", """{"email":"taken@vapor.codes"}""")
            assertEquals(HttpStatusCode.BadRequest, response.status)
            assertEquals("email is already registered", response.bodyAsText())
        }

    @Test
    fun reasonsListEveryFailureInRuleOrder() =
        testApplication {
            app()
            val response = postJson("/user", """{"name":"Vap!","age":3}""")
            assertEquals(HttpStatusCode.BadRequest, response.status)
            assertEquals(
                listOf(
                    "name is less than required minimum of 5 characters",
                    "name contains an invalid character: '!' (allowed: A-Z, a-z, 0-9)",
                    "age is less than 18",
                ),
                response.bodyAsText().lines(),
            )
        }

    @Test
    fun nonValidationErrorIsNotTurnedIntoRequestValidationException() =
        testApplication {
            app()
            val response = postJson("/signup", """{"email":"down@vapor.codes"}""")
            assertEquals(HttpStatusCode.InternalServerError, response.status)
        }
}
