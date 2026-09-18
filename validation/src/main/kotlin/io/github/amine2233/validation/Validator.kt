package io.github.amine2233.validation

/**
 * A discrete validator: a [readable] description plus the check itself.
 *
 * `Validator` is contravariant, so a `Validator<Any?>` (such as [Validator.Companion.empty]) can be used
 * wherever a `Validator<String>` or `Validator<List<Int>>` is expected. Built-in validators for nullable
 * types skip `null` (see [ignoringNull]); combine with `!Validator.nil` to require a value.
 *
 *     Validator.email.validate("user@example.com")
 *     (Validator.count(min = 5) and Validator.alphanumeric).validate("hello1")
 */
public class Validator<in T>(
    /** Readable name explaining what this validator does. Suitable for placing after `is` _and_ `is not`. */
    public val readable: String,
    /** Whether `null` is skipped rather than validated. `!` on such a validator also skips `null`. */
    public val ignoresNull: Boolean = false,
    private val closure: (T) -> Unit,
) {
    /** Validates [data], throwing a [ValidationError] if it is not valid. */
    public fun validate(data: T): Unit = closure(data)

    override fun toString(): String = readable

    /** Anchor for the built-in factories (`Validator.email`, `Validator.count(...)`, ...). */
    public companion object
}

/**
 * Lifts a non-null validator to a nullable one that passes `null` through untouched, like Swift's
 * `NilIgnoringValidator`. Every built-in is defined this way; use it on your own validators to combine them
 * with [Validator.Companion.nil]:
 *
 *     validations.add(User::nickname, Validator.nil or IsVapor.validator().ignoringNull())
 */
public fun <T : Any> Validator<T>.ignoringNull(): Validator<T?> =
    Validator(readable, ignoresNull = true) { data -> if (data != null) validate(data) }
