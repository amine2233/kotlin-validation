package io.github.amine2233.validation

/**
 * Combines two async validators with AND logic, succeeding only if both succeed. Both are always run, so the
 * error lists every failure.
 *
 *     validations.add(SignUpRequest::email, Validator.email.async() and emailIsAvailable)
 */
public infix fun <T> AsyncValidator<T>.and(rhs: AsyncValidator<T>): AsyncValidator<T> =
    AsyncValidator("$readable and is ${rhs.readable}", ignoresNull && rhs.ignoresNull) { data ->
        val left = failure { validate(data) }
        val right = failure { rhs.validate(data) }
        if (left != null || right != null) throw AndValidatorError(left, right)
    }

/**
 * Combines two async validators with OR logic, succeeding if either succeeds. The right-hand check runs only
 * when the left one fails, so an expensive remote rule belongs on the right.
 *
 *     validations.add(SignUpRequest::email, Validator.nil.async() or emailIsAvailable)
 */
public infix fun <T> AsyncValidator<T>.or(rhs: AsyncValidator<T>): AsyncValidator<T> =
    AsyncValidator("$readable or is ${rhs.readable}", ignoresNull && rhs.ignoresNull) { data ->
        val left = failure { validate(data) } ?: return@AsyncValidator
        val right = failure { rhs.validate(data) } ?: return@AsyncValidator
        throw OrValidatorError(left, right)
    }

/**
 * Inverts an async validator. A validator that ignores `null` still ignores it when inverted.
 *
 *     validations.add(SignUpRequest::email, !emailIsAvailable)
 */
public operator fun <T> AsyncValidator<T>.not(): AsyncValidator<T> =
    AsyncValidator("not $readable", ignoresNull) { data ->
        if (ignoresNull && data == null) return@AsyncValidator
        if (failure { validate(data) } == null) throw BasicValidationError("is $readable")
    }

private suspend inline fun failure(block: () -> Unit): ValidationError? =
    try {
        block()
        null
    } catch (error: ValidationError) {
        error
    }
