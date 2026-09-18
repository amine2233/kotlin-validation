package io.github.amine2233.validation

/**
 * Validates that the data is `null`. Combine with `!` to require a value.
 *
 *     validations.add(User::email, Validator.nil or Validator.email)
 *     validations.add(User::email, !Validator.nil and Validator.email)
 */
public val Validator.Companion.nil: Validator<Any?> get() = nilValidator

private val nilValidator: Validator<Any?> = Validator("nil") { data -> if (data != null) throw BasicValidationError("is not nil") }
