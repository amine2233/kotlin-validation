package io.github.amine2233.validation

private val emailRegex = Regex("[A-Z0-9a-z._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,64}", RegexOption.IGNORE_CASE)

/**
 * Validates that the data is a valid email address.
 *
 *     validations.add(User::email, Validator.email)
 */
public val Validator.Companion.email: Validator<String?> get() = emailValidator

private val emailValidator: Validator<String?> =
    Validator<String>("a valid email address") { data ->
        if (!emailRegex.matches(data)) throw BasicValidationError("is not a valid email address")
    }.ignoringNull()
