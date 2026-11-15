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

private data class Parent(
    val child: Account?,
) : AsyncValidatable<Parent> {
    override fun validations() = asyncValidations<Parent> { add(Parent::child, AsyncValidator.valid()) }
}

private data class Grandparent(
    val parent: Parent,
) : AsyncValidatable<Grandparent> {
    override fun validations() = asyncValidations<Grandparent> { add(Grandparent::parent, AsyncValidator.valid()) }
}

private class Unreachable : AsyncValidatable<Unreachable> {
    override fun validations() = asyncValidations<Unreachable> { add("is reachable") { throw IOException("connection reset") } }
}

private data class Holder(
    val child: Unreachable?,
)

private object IsFree : AsyncValidatorType<String> {
    override val validatorReadable = "free"

    override suspend fun validate(data: String) {
        if (data == "taken@vapor.codes") throw BasicValidationError("is already registered")
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

    @Test
    fun builtAsyncValidationsRejectEveryAdd() =
        runBlocking {
            val calls = mutableListOf<String>()
            val validations =
                asyncValidations<Account> {
                    add(Account::age, Validator.range(min = 18))
                    add(Account::email, "is free") { calls += "is free" }
                    add("has a known pair") { calls += "has a known pair" }
                }
            val before = validations.toString()

            assertFailsWith<IllegalStateException> { validations.add(Account::email, Validator.email) }
            assertEquals(before, validations.toString())
            assertFailsWith<IllegalStateException> {
                validations.add(Account::email, AsyncValidator<String?>("late") { calls += "async validator" })
            }
            assertEquals(before, validations.toString())
            assertFailsWith<IllegalStateException> { validations.add(Account::email, "late") { calls += "property" } }
            assertEquals(before, validations.toString())
            assertFailsWith<IllegalStateException> { validations.add("late") { calls += "model" } }
            assertEquals(before, validations.toString())

            validations.run(Account("nope", 30))
            assertEquals(listOf("is free", "has a known pair"), calls)
            val error = assertFailsWith<ValidateErrors> { validations.run(Account("nope", 12)) }
            assertEquals(listOf(listOf("age") to "age is less than 18"), error.flatten().map { it.path to it.reason })
        }

    @Test
    fun directlyConstructedAsyncValidationsAcceptAdd() =
        runBlocking {
            val validations = AsyncValidations<Account>()
            validations.add(Account::age, Validator.range(min = 18))
            validations.add(Account::email, "is free") { throw BasicValidationError("is already registered") }
            assertEquals("age: is at least 18\nemail: is free", validations.toString())
            val syncError = assertFailsWith<ValidateErrors> { validations.run(Account("a@b.co", 12)) }
            assertEquals("age is less than 18", syncError.reason)
            val asyncError = assertFailsWith<ValidateErrors> { validations.run(Account("a@b.co", 30)) }
            assertEquals("email is already registered", asyncError.reason)
        }

    @Test
    fun validNestedModelPasses() =
        runBlocking {
            val directory = Directory()
            Parent(Account("free@vapor.codes", 30).apply { this.directory = directory }).validate()
            assertEquals(listOf("free@vapor.codes"), directory.lookups)
        }

    @Test
    fun nestedAsyncFailureIsPrefixedWithThePropertyPath() =
        runBlocking {
            val error = assertFailsWith<ValidateErrors> { Parent(Account("taken@vapor.codes", 30)).validate() }
            assertEquals("child.email is already registered", error.reason)
            assertEquals(
                listOf(listOf("child", "email") to "child.email is already registered"),
                error.flatten().map { it.path to it.reason },
            )
        }

    @Test
    fun nestedSyncFailureIsPrefixedAndSkipsTheLookup() =
        runBlocking {
            val directory = Directory()
            val error = assertFailsWith<ValidateErrors> { Parent(Account("nope", 30).apply { this.directory = directory }).validate() }
            assertEquals("child.email is not a valid email address", error.reason)
            assertEquals(listOf(listOf("child", "email")), error.flatten().map { it.path })
            assertEquals(emptyList(), directory.lookups)
        }

    @Test
    fun nullNestedModelIsSkipped() =
        runBlocking {
            val directory = Directory()
            Parent(null).validate()
            assertEquals(emptyList(), directory.lookups)
        }

    @Test
    fun nestedValidHonoursCustomPath() =
        runBlocking {
            val validations = asyncValidations<Parent> { add(Parent::child, AsyncValidator.valid(), at = listOf("other")) }
            val error = assertFailsWith<ValidateErrors> { validations.run(Parent(Account("taken@vapor.codes", 30))) }
            assertEquals("other.email is already registered", error.reason)
            assertEquals(listOf(listOf("other", "email")), error.flatten().map { it.path })
        }

    @Test
    fun twoLevelsOfNestingGiveTheFullPath() =
        runBlocking {
            val error = assertFailsWith<ValidateErrors> { Grandparent(Parent(Account("taken@vapor.codes", 30))).validate() }
            assertEquals("parent.child.email is already registered", error.reason)
            assertEquals(listOf(listOf("parent", "child", "email")), error.flatten().map { it.path })
        }

    @Test
    fun nestedNetworkFailuresPropagate() {
        val validations = asyncValidations<Holder> { add(Holder::child, AsyncValidator.valid()) }
        assertFailsWith<IOException> { runBlocking { validations.run(Holder(Unreachable())) } }
    }

    @Test
    fun asyncValidIsANullSkippingRuleNamedValid() {
        val validator = AsyncValidator.valid<Account>()
        assertEquals("valid", validator.readable)
        assertTrue(validator.ignoresNull)
    }

    @Test
    fun asyncValidatorTypeBecomesAnAsyncValidator() =
        runBlocking {
            val validator = IsFree.validator()
            assertEquals(IsFree.validatorReadable, validator.readable)
            validator.validate("free@vapor.codes")

            val validations = asyncValidations<Account> { add(Account::email, IsFree.validator().ignoringNull()) }
            validations.run(Account("free@vapor.codes", 30))
            val error = assertFailsWith<ValidateErrors> { validations.run(Account("taken@vapor.codes", 30)) }
            assertEquals("email is already registered", error.reason)
            assertEquals(listOf(listOf("email")), error.flatten().map { it.path })
        }
}
