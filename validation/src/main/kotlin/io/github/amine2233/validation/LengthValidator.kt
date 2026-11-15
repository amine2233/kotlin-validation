package io.github.amine2233.validation

/**
 * Validates that the text's user-perceived character count (`"🤠"` is 1) is within [range].
 *
 *     validations.add(User::name, Validator.length(5..10))
 *     validations.add(User::name, Validator.length(5..<10))
 *
 * @throws IllegalArgumentException if [range] is empty or has a negative bound.
 */
public fun Validator.Companion.length(range: IntRange): Validator<CharSequence?> = length(min = range.first, max = range.last)

/**
 * Validates that the text's user-perceived character count is at least [min] and at most [max] (both inclusive, both optional).
 *
 *     validations.add(User::name, Validator.length(min = 5))
 *     validations.add(User::name, Validator.length(max = 10))
 *
 * @throws IllegalArgumentException if [min] or [max] is negative, or [min] is greater than [max].
 */
public fun Validator.Companion.length(
    min: Int? = null,
    max: Int? = null,
): Validator<CharSequence?> = boundedCount(min, max, unit = "character", measure = ::graphemeCount)

/**
 * Validates that the collection's element count is within [range].
 *
 *     validations.add(User::tags, Validator.size(1..10))
 *
 * @throws IllegalArgumentException if [range] is empty or has a negative bound.
 */
public fun Validator.Companion.size(range: IntRange): Validator<Collection<*>?> = size(min = range.first, max = range.last)

/**
 * Validates that the collection's element count is at least [min] and at most [max] (both inclusive, both optional).
 *
 *     validations.add(User::tags, Validator.size(min = 1))
 *     validations.add(User::tags, Validator.size(max = 10))
 *
 * @throws IllegalArgumentException if [min] or [max] is negative, or [min] is greater than [max].
 */
public fun Validator.Companion.size(
    min: Int? = null,
    max: Int? = null,
): Validator<Collection<*>?> = boundedCount(min, max, unit = "item") { it.size }

/**
 * Validates that the map's entry count is within [range].
 *
 *     validations.add(User::settings, Validator.mapSize(1..10))
 *
 * @throws IllegalArgumentException if [range] is empty or has a negative bound.
 */
public fun Validator.Companion.mapSize(range: IntRange): Validator<Map<*, *>?> = mapSize(min = range.first, max = range.last)

/**
 * Validates that the map's entry count is at least [min] and at most [max] (both inclusive, both optional).
 *
 *     validations.add(User::settings, Validator.mapSize(min = 1))
 *     validations.add(User::settings, Validator.mapSize(max = 10))
 *
 * @throws IllegalArgumentException if [min] or [max] is negative, or [min] is greater than [max].
 */
public fun Validator.Companion.mapSize(
    min: Int? = null,
    max: Int? = null,
): Validator<Map<*, *>?> = boundedCount(min, max, unit = "item") { it.size }

/**
 * Validates that the array's element count is within [range].
 *
 *     validations.add(User::nicknames, Validator.arraySize(1..10))
 *
 * @throws IllegalArgumentException if [range] is empty or has a negative bound.
 */
public fun Validator.Companion.arraySize(range: IntRange): Validator<Array<*>?> = arraySize(min = range.first, max = range.last)

/**
 * Validates that the array's element count is at least [min] and at most [max] (both inclusive, both optional).
 *
 *     validations.add(User::nicknames, Validator.arraySize(min = 1))
 *     validations.add(User::nicknames, Validator.arraySize(max = 10))
 *
 * @throws IllegalArgumentException if [min] or [max] is negative, or [min] is greater than [max].
 */
public fun Validator.Companion.arraySize(
    min: Int? = null,
    max: Int? = null,
): Validator<Array<*>?> = boundedCount(min, max, unit = "item") { it.size }

private fun <T : Any> boundedCount(
    min: Int?,
    max: Int?,
    unit: String,
    measure: (T) -> Int,
): Validator<T?> {
    require(min == null || min >= 0) { "min ($min) must not be negative" }
    require(max == null || max >= 0) { "max ($max) must not be negative" }
    require(min == null || max == null || min <= max) { "min ($min) must not be greater than max ($max)" }
    val readable =
        when {
            min != null && max != null -> "of length between $min and $max"
            min != null -> "of length at least $min"
            max != null -> "of length at most $max"
            else -> "valid"
        }

    fun elements(bound: Int): String = if (bound == 1) "1 $unit" else "$bound ${unit}s"
    return Validator<T>(readable) { data ->
        val count = measure(data)
        if (min != null && count < min) throw BasicValidationError("is less than required minimum of ${elements(min)}")
        if (max != null && count > max) throw BasicValidationError("is greater than required maximum of ${elements(max)}")
    }.ignoringNull()
}
