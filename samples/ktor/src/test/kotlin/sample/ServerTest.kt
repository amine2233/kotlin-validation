package sample

import io.ktor.client.call.body
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.testing.testApplication
import kotlin.test.Test
import kotlin.test.assertEquals

class ServerTest {
    @Test
    fun validRequestIsCreated() =
        testApplication {
            application { module() }
            val client = createClient { install(ContentNegotiation) { json() } }
            val response =
                client.post("/signup") {
                    contentType(ContentType.Application.Json)
                    setBody(SignUpRequest(email = "tanner@vapor.codes", password = "hunter2hunter2", age = 30))
                }
            assertEquals(HttpStatusCode.Created, response.status)
        }

    @Test
    fun invalidRequestListsEveryFailure() =
        testApplication {
            application { module() }
            val client = createClient { install(ContentNegotiation) { json() } }
            val response =
                client.post("/signup") {
                    contentType(ContentType.Application.Json)
                    setBody(SignUpRequest(email = "nope", password = "short!", age = 12))
                }
            assertEquals(HttpStatusCode.BadRequest, response.status)
            assertEquals(
                ErrorResponse(
                    error =
                        "email is not a valid email address, " +
                            "password is less than required minimum of 8 characters, " +
                            "password contains an invalid character: '!' (allowed: A-Z, a-z, 0-9), " +
                            "age is less than 18",
                    errors =
                        listOf(
                            "email is not a valid email address",
                            "password is less than required minimum of 8 characters",
                            "password contains an invalid character: '!' (allowed: A-Z, a-z, 0-9)",
                            "age is less than 18",
                        ),
                ),
                response.body(),
            )
        }

    @Test
    fun alreadyRegisteredEmailIsRejected() =
        testApplication {
            application { module() }
            val client = createClient { install(ContentNegotiation) { json() } }
            val response =
                client.post("/signup") {
                    contentType(ContentType.Application.Json)
                    setBody(SignUpRequest(email = "taken@vapor.codes", password = "hunter2hunter2", age = 30))
                }
            assertEquals(HttpStatusCode.BadRequest, response.status)
            assertEquals(
                ErrorResponse(
                    error = "email is already registered",
                    errors = listOf("email is already registered"),
                ),
                response.body(),
            )
        }

    @Test
    fun malformedJsonIsBadRequest() =
        testApplication {
            application { module() }
            val client = createClient { install(ContentNegotiation) { json() } }
            val response =
                client.post("/signup") {
                    contentType(ContentType.Application.Json)
                    setBody("""{"email":""")
                }
            assertEquals(HttpStatusCode.BadRequest, response.status)
            assertEquals(ErrorResponse(listOf("malformed request body")), response.body())
        }
}
