package io.github.amine2233.validation

/**
 * Validates that the data's count (user-perceived characters for text, elements otherwise) is within [range].
 *
 *     validations.add(User::name, Validator.count(5..10))
 *     validations.add(User::name, Validator.count(5..<10))
 *
 * @throws IllegalArgumentException if [range] is empty or has a negative bound.
 */
public fun Validator.Companion.count(range: IntRange): Validator<Any?> = count(min = range.first, max = range.last)

/**
 * Validates that the data's count is at least [min] and at most [max] (both inclusive, both optional).
 *
 *     validations.add(User::name, Validator.count(min = 5))
 *     validations.add(User::tags, Validator.count(max = 10))
 *
 * @throws IllegalArgumentException if [min] or [max] is negative, or [min] is greater than [max].
 */
public fun Validator.Companion.count(
    min: Int? = null,
    max: Int? = null,
): Validator<Any?> {
    require(min == null || min >= 0) { "min ($min) must not be negative" }
    require(max == null || max >= 0) { "max ($max) must not be negative" }
    require(min == null || max == null || min <= max) { "min ($min) must not be greater than max ($max)" }
    // ponytail: the readable can't know whether it will see text or a collection, so it stays neutral.
    val readable =
        when {
            min != null && max != null -> "of length between $min and $max"
            min != null -> "of length at least $min"
            max != null -> "of length at most $max"
            else -> "valid"
        }
    return Validator<Any>(readable) { data ->
        val count = sizeOf(data)

        fun elements(bound: Int): String =
            when {
                data is CharSequence -> if (bound == 1) "1 character" else "$bound characters"
                else -> if (bound == 1) "1 item" else "$bound items"
            }
        if (min != null && count < min) throw BasicValidationError("is less than required minimum of ${elements(min)}")
        if (max != null && count > max) throw BasicValidationError("is greater than required maximum of ${elements(max)}")
    }.ignoringNull()
}
