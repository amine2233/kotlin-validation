package io.github.amine2233.validation.ktor

import io.github.amine2233.validation.AsyncValidatable
import io.github.amine2233.validation.Validatable
import io.github.amine2233.validation.ValidationError
import io.github.amine2233.validation.validate
import io.ktor.server.plugins.requestvalidation.RequestValidationConfig
import io.ktor.server.plugins.requestvalidation.ValidationResult

/**
 * Validates every received [M] with its [Validatable.validations]. A failure makes `call.receive` throw a
 * `RequestValidationException` with one reason per failing rule, in rule order, each including its key path.
 *
 *     install(RequestValidation) { validatable<User>() }
 *
 * Only a [ValidationError] becomes [ValidationResult.Invalid]; any other exception propagates.
 */
public inline fun <reified M : Validatable<M>> RequestValidationConfig.validatable() {
    validate<M> { body -> validationResult { body.validate() } }
}

/**
 * Validates every received [M] with its [AsyncValidatable.validations], suspending on the async rules. A failure
 * makes `call.receive` throw a `RequestValidationException` with one reason per failing rule, in rule order, each
 * including its key path.
 *
 *     install(RequestValidation) { asyncValidatable<SignUpRequest>() }
 *
 * Only a [ValidationError] becomes [ValidationResult.Invalid]; any other exception, such as an `IOException` from an
 * async rule, propagates.
 */
public inline fun <reified M : AsyncValidatable<M>> RequestValidationConfig.asyncValidatable() {
    validate<M> { body -> validationResult { body.validate() } }
}

/** Runs [validation] and maps a thrown [ValidationError] to [ValidationResult.Invalid] with its flattened reasons. */
@PublishedApi
internal inline fun validationResult(validation: () -> Unit): ValidationResult =
    try {
        validation()
        ValidationResult.Valid
    } catch (error: ValidationError) {
        ValidationResult.Invalid(error.flatten().map { it.reason })
    }
