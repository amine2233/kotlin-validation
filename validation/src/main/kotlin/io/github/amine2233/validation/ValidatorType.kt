package io.github.amine2233.validation

/**
 * Something that can validate a [T]. Implement it to define a reusable custom validator, then call
 * [validator] to obtain the [Validator] users compose with.
 *
 *     object IsVapor : ValidatorType<String> {
 *         override val validatorReadable = "vapor"
 *         override fun validate(data: String) {
 *             if (data != "vapor") throw BasicValidationError("is not vapor")
 *         }
 *     }
 *     validations.add(User::name, IsVapor.validator())
 */
public interface ValidatorType<in T> {
    /** Readable name explaining what this validator does. Suitable for placing after `is` _and_ `is not`. */
    public val validatorReadable: String

    /** Validates [data], throwing a [ValidationError] if it is not valid. */
    public fun validate(data: T)
}

/** Creates a [Validator] from this [ValidatorType]. */
public fun <T> ValidatorType<T>.validator(): Validator<T> = Validator(validatorReadable, closure = ::validate)
