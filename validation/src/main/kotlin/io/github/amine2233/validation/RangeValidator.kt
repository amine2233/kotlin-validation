package io.github.amine2233.validation

/**
 * Validates that the data is within [range] (an `IntRange` such as `5..10` or `5..<10` works too).
 *
 *     validations.add(User::age, Validator.range(18..120))
 *
 * @throws IllegalArgumentException if [range] has its start greater than its end.
 */
public fun <T : Comparable<T>> Validator.Companion.range(range: ClosedRange<T>): Validator<T?> =
    range(min = range.start, max = range.endInclusive)

/**
 * Validates that the data is at least [min] and at most [max] (both inclusive, both optional).
 *
 *     validations.add(User::age, Validator.range(min = 18))
 *     validations.add(User::discount, Validator.range(max = 0.5))
 *
 * @throws IllegalArgumentException if [min] is greater than [max].
 */
public fun <T : Comparable<T>> Validator.Companion.range(
    min: T? = null,
    max: T? = null,
): Validator<T?> {
    require(min == null || max == null || min <= max) { "min ($min) must not be greater than max ($max)" }
    val readable =
        when {
            min != null && max != null -> "between $min and $max"
            min != null -> "at least $min"
            max != null -> "at most $max"
            else -> "valid"
        }
    return Validator<T>(readable) { data ->
        if (min != null && data < min) throw BasicValidationError("is less than $min")
        if (max != null && data > max) throw BasicValidationError("is greater than $max")
    }.ignoringNull()
}
