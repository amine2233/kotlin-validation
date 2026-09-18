package io.github.amine2233.validation

/**
 * Validates that the data is one of [values].
 *
 *     validations.add(User::luckyNumber, Validator.nil or Validator.oneOf(5, 7))
 */
public fun <T : Any> Validator.Companion.oneOf(vararg values: T): Validator<T?> = oneOf(values.asList())

/**
 * Validates that the data is one of [values].
 *
 *     validations.add(User::role, Validator.oneOf(Role.entries))
 */
public fun <T : Any> Validator.Companion.oneOf(values: Collection<T>): Validator<T?> {
    val readable = "in (${values.joinToString(", ")})"
    return Validator<T>(readable) { data -> if (data !in values) throw BasicValidationError("is not $readable") }.ignoringNull()
}
