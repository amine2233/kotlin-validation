package io.github.amine2233.validation

/**
 * Combines two validators with AND logic, succeeding only if both succeed. Both are always run, so the
 * error lists every failure.
 *
 *     validations.add(User::name, Validator.count(min = 5) and Validator.alphanumeric)
 */
public infix fun <T> Validator<T>.and(rhs: Validator<T>): Validator<T> =
    Validator("$readable and is ${rhs.readable}", ignoresNull && rhs.ignoresNull) { data ->
        val left = failure { validate(data) }
        val right = failure { rhs.validate(data) }
        if (left != null || right != null) throw AndValidatorError(left, right)
    }

/**
 * Combines two validators with OR logic, succeeding if either succeeds.
 *
 *     validations.add(User::email, Validator.nil or Validator.email)
 */
public infix fun <T> Validator<T>.or(rhs: Validator<T>): Validator<T> =
    Validator("$readable or is ${rhs.readable}", ignoresNull && rhs.ignoresNull) { data ->
        val left = failure { validate(data) } ?: return@Validator
        val right = failure { rhs.validate(data) } ?: return@Validator
        throw OrValidatorError(left, right)
    }

/**
 * Inverts a validator. A validator that ignores `null` still ignores it when inverted.
 *
 *     validations.add(User::email, !Validator.nil and Validator.email)
 */
public operator fun <T> Validator<T>.not(): Validator<T> =
    Validator("not $readable", ignoresNull) { data ->
        if (ignoresNull && data == null) return@Validator
        if (failure { validate(data) } == null) throw BasicValidationError("is $readable")
    }

private inline fun failure(block: () -> Unit): ValidationError? =
    try {
        block()
        null
    } catch (error: ValidationError) {
        error
    }

internal class AndValidatorError(
    private val left: ValidationError?,
    private val right: ValidationError?,
    path: List<String> = emptyList(),
) : ValidationError(path) {
    override val reason: String get() = flatten().joinToString(" and ") { it.reason }

    override val detail: String get() = listOfNotNull(left, right).joinToString(" and ") { it.relativeDetail() }

    override fun prefixed(path: List<String>): ValidationError = AndValidatorError(left, right, path + this.path)

    override fun flatten(): List<ValidationError> = listOfNotNull(left, right).flatMap { it.prefixed(path).flatten() }
}

internal class OrValidatorError(
    private val left: ValidationError,
    private val right: ValidationError,
    path: List<String> = emptyList(),
) : ValidationError(path) {
    override val reason: String get() = "${left.prefixed(path).reason} and ${right.prefixed(path).reason}"

    override val detail: String get() = "${left.relativeDetail()} and ${right.relativeDetail()}"

    override fun prefixed(path: List<String>): ValidationError = OrValidatorError(left, right, path + this.path)
}
