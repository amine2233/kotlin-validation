package io.github.amine2233.validation

/**
 * Something that can validate a [T] with a check that suspends. Implement it to define a reusable
 * custom async validator, then call [validator] to obtain the [AsyncValidator] users compose with.
 *
 *     object IsAvailable : AsyncValidatorType<String> {
 *         override val validatorReadable = "available"
 *         override suspend fun validate(data: String) {
 *             if (api.isRegistered(data)) throw BasicValidationError("is already registered")
 *         }
 *     }
 *     validations.add(User::email, IsAvailable.validator())
 */
public interface AsyncValidatorType<in T> {
    /** Readable name explaining what this validator does. Suitable for placing after `is` _and_ `is not`. */
    public val validatorReadable: String

    /** Validates [data], throwing a [ValidationError] if it is not valid. */
    public suspend fun validate(data: T)
}

/** Creates an [AsyncValidator] from this [AsyncValidatorType]. */
public fun <T> AsyncValidatorType<T>.validator(): AsyncValidator<T> = AsyncValidator(validatorReadable) { validate(it) }
