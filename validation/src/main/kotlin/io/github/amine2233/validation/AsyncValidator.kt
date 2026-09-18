package io.github.amine2233.validation

/**
 * A discrete validator whose check suspends — typically because it asks a remote API, a database or
 * any other source that cannot answer in memory.
 *
 * It mirrors [Validator] exactly, including contravariance and the `null`-skipping convention, and throws
 * the same [ValidationError]s, so paths, `reason` and [ValidationError.flatten] behave identically.
 *
 *     val emailIsAvailable = AsyncValidator<String>("available") { email ->
 *         if (api.isRegistered(email)) throw BasicValidationError("is already registered")
 *     }
 *     emailIsAvailable.validate("user@example.com")
 */
public class AsyncValidator<in T>(
    /** Readable name explaining what this validator does. Suitable for placing after `is` _and_ `is not`. */
    public val readable: String,
    /** Whether `null` is skipped rather than validated. `!` on such a validator also skips `null`. */
    public val ignoresNull: Boolean = false,
    private val closure: suspend (T) -> Unit,
) {
    /** Validates [data], throwing a [ValidationError] if it is not valid. */
    public suspend fun validate(data: T): Unit = closure(data)

    override fun toString(): String = readable

    /** Anchor for custom factories, mirroring [Validator.Companion]. */
    public companion object
}

/**
 * Lifts a synchronous validator into the async world, so every built-in composes with remote checks:
 *
 *     validations.add(SignUpRequest::email, Validator.email.async() and emailIsAvailable)
 */
public fun <T> Validator<T>.async(): AsyncValidator<T> = AsyncValidator(readable, ignoresNull) { data -> validate(data) }

/**
 * Lifts a non-null async validator to a nullable one that passes `null` through untouched, like
 * [Validator.ignoringNull]:
 *
 *     validations.add(User::email, Validator.nil.async() or emailIsAvailable.ignoringNull())
 */
public fun <T : Any> AsyncValidator<T>.ignoringNull(): AsyncValidator<T?> =
    AsyncValidator(readable, ignoresNull = true) { data -> if (data != null) validate(data) }
