package io.github.amine2233.validation

import java.text.BreakIterator

/**
 * Validates that the data is empty (`null` counts as empty). Combine with `!` to require content.
 *
 *     validations.add(User::name, Validator.empty)
 *     validations.add(User::preferedColors, !Validator.empty)
 */
public val Validator.Companion.empty: Validator<Any?> get() = emptyValidator

private val emptyValidator: Validator<Any?> =
    Validator("empty") { data ->
        if (data != null &&
            sizeOf(data) != 0
        ) {
            throw BasicValidationError("is not empty")
        }
    }

// ponytail: runtime dispatch on the value's shape; Kotlin has no common supertype for "things with a size".
internal fun sizeOf(data: Any): Int =
    when (data) {
        is CharSequence -> graphemeCount(data)
        is Collection<*> -> data.size
        is Map<*, *> -> data.size
        is Array<*> -> data.size
        else -> throw IllegalArgumentException("count/empty need a CharSequence, Collection, Map or Array, got ${data.javaClass.name}")
    }

/** User-perceived characters, like Swift's `String.count`: "🤠" and "é" (e + combining accent) count as 1. */
internal fun graphemeCount(text: CharSequence): Int {
    val iterator = BreakIterator.getCharacterInstance().apply { setText(text.toString()) }
    var count = 0
    while (iterator.next() != BreakIterator.DONE) count++
    return count
}
