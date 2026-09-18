package io.github.amine2233.validation

import kotlinx.coroutines.runBlocking
import java.io.IOException
import kotlin.coroutines.cancellation.CancellationException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/** A fake remote directory: records every lookup so tests can assert which calls were actually made. */
private class Directory(
    private val taken: Set<String> = setOf("taken@vapor.codes"),
) {
    val lookups = mutableListOf<String>()

    suspend fun isRegistered(email: String): Boolean {
        lookups += email
        return email in taken
    }

    fun emailIsFree(): AsyncValidator<String?> =
        AsyncValidator<String>("not already registered") { email ->
            if (isRegistered(email)) throw BasicValidationError("is already registered")
        }.ignoringNull()
}

private data class Account(
    val email: String?,
    val age: Int,
) : AsyncValidatable<Account> {
    var directory: Directory = Directory()

    override fun validations() =
        asyncValidations<Account> {
            add(Account::email, Validator.email)
            add(Account::age, Validator.range(min = 18))
            add(Account::email, directory.emailIsFree())
        }
}

class AsyncValidationTests {
    @Test
    fun asyncFailureIsReportedWithItsPath() =
        runBlocking {
            val directory = Directory()
            val validations = asyncValidations<Account> { add(Account::email, directory.emailIsFree()) }
            val error = assertFailsWith<ValidateErrors> { validations.run(Account("taken@vapor.codes", 30)) }
            assertEquals("email is already registered", error.reason)
            assertEquals(listOf(listOf("email") to "email is already registered"), error.flatten().map { it.path to it.reason })
        }

    @Test
    fun syncFailuresSkipTheAsyncRules() =
        runBlocking {
            val account = Account("nope", 12).apply { directory = Directory() }
            val error = assertFailsWith<ValidateErrors> { account.validate() }
            assertEquals("email is not a valid email address, age is less than 18", error.reason)
            assertEquals(emptyList(), account.directory.lookups)
        }

    @Test
    fun asyncRulesRunWhenSyncRulesPass() =
        runBlocking {
            val account = Account("free@vapor.codes", 30)
            account.validate()
            assertEquals(listOf("free@vapor.codes"), account.directory.lookups)
        }

    @Test
    fun asyncRulesCollectEveryFailure() =
        runBlocking {
            val validations =
                asyncValidations<Account> {
                    add(Account::email, "is free") { throw BasicValidationError("is already registered") }
                    add(Account::age, "is plausible") { throw BasicValidationError("does not match our records") }
                }
            val error = assertFailsWith<ValidateErrors> { validations.run(Account("a@b.co", 30)) }
            assertEquals("email is already registered, age does not match our records", error.reason)
            assertEquals(listOf(listOf("email"), listOf("age")), error.flatten().map { it.path })
        }

    @Test
    fun asyncRulesRunOneAtATimeInDeclarationOrder() =
        runBlocking {
            val calls = mutableListOf<String>()
            val validations =
                asyncValidations<Account> {
                    add("first") { calls += "first" }
                    add("second") { calls += "second" }
                    add("third") { calls += "third" }
                }
            validations.run(Account("a@b.co", 30))
            assertEquals(listOf("first", "second", "third"), calls)
        }

    @Test
    fun syncValidatorsLiftIntoTheAsyncWorld() =
        runBlocking {
            val lifted = Validator.email.async()
            assertEquals("a valid email address", lifted.readable)
            lifted.validate("tanner@vapor.codes")
            assertFailsWith<ValidationError> { lifted.validate("asdf") }
            assertTrue(lifted.ignoresNull)
            lifted.validate(null)
        }

    @Test
    fun ignoringNullSkipsTheCall() =
        runBlocking {
            val account = Account(null, 30)
            account.validate()
            assertEquals(emptyList(), account.directory.lookups)
        }

    @Test
    fun networkFailuresPropagate() {
        val validations = asyncValidations<Account> { add(Account::email, "is free") { throw IOException("connection reset") } }
        assertFailsWith<IOException> { runBlocking { validations.run(Account("a@b.co", 30)) } }
    }

    @Test
    fun cancellationIsNotAValidationFailure() {
        val calls = mutableListOf<String>()
        val validations =
            asyncValidations<Account> {
                add("cancels") { throw CancellationException("the caller went away") }
                add("later") { calls += "later" }
            }
        assertFailsWith<CancellationException> { runBlocking { validations.run(Account("a@b.co", 30)) } }
        assertEquals(emptyList(), calls)
    }

    @Test
    fun customAsyncClosureIsPrefixedWithThePath() =
        runBlocking {
            val validations =
                asyncValidations<Account> {
                    add(Account::email, "is free", at = listOf("user", "email")) {
                        throw BasicValidationError("is already registered")
                    }
                }
            val error = assertFailsWith<ValidateErrors> { validations.run(Account("a@b.co", 30)) }
            assertEquals("user.email is already registered", error.reason)
            assertEquals(listOf(listOf("user", "email")), error.flatten().map { it.path })
        }

    @Test
    fun wholeModelAsyncClosureReportsAtTheRoot() =
        runBlocking {
            val validations = asyncValidations<Account> { add("has a known pair") { throw BasicValidationError("is not a known pair") } }
            val error = assertFailsWith<ValidateErrors> { validations.run(Account("a@b.co", 30)) }
            assertEquals("data is not a known pair", error.reason)
        }

    @Test
    fun asyncCombinators() =
        runBlocking {
            val free = Directory(setOf("taken", "taken@vapor.codes")).emailIsFree()
            val both = Validator.email.async() and free
            both.validate("free@vapor.codes")
            val error = assertFailsWith<ValidationError> { both.validate("taken") }
            assertEquals("data is not a valid email address and data is already registered", error.reason)
            assertEquals("a valid email address and is not already registered", both.readable)

            val either = Validator.nil.async() or free
            either.validate(null)
            either.validate("free@vapor.codes")
            assertFailsWith<ValidationError> { either.validate("taken@vapor.codes") }

            val taken = !free
            taken.validate("taken@vapor.codes")
            assertEquals("not not already registered", taken.readable)
            assertFailsWith<ValidationError> { taken.validate("free@vapor.codes") }
            taken.validate(null)
        }

    @Test
    fun toStringListsSyncRulesThenAsyncRules() {
        val validations =
            asyncValidations<Account> {
                add(Account::age, Validator.range(min = 18))
                add(Account::email, Directory().emailIsFree())
            }
        assertEquals("age: is at least 18\nemail: is not already registered", validations.toString())
    }
}
