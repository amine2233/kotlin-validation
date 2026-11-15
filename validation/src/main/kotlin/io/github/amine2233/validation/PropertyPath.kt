package io.github.amine2233.validation

import kotlin.reflect.KProperty1

/**
 * A nested property of [M] whose value is a [T], built with [path]. Pass it to `add` to validate a nested
 * field with a single rule; failures are reported under [names] joined with dots.
 *
 *     validations.add(path(User::pet, Pet::name), Validator.length(min = 5))   // "pet.name is less than ..."
 */
public class PropertyPath<M, out T> internal constructor(
    /** The property names, outermost first. */
    public val names: List<String>,
    private val read: (M) -> Resolved<T>?,
) {
    /** Reads the value at this path, or `null` when an intermediate value is `null` and the rule is skipped. */
    internal fun resolve(model: M): Resolved<T>? = read(model)

    internal class Resolved<out T>(
        val value: T,
    )
}

/**
 * Joins [outer] and [inner] into one [PropertyPath]. When [outer] is `null` the rule is skipped and passes;
 * the inner value is passed through as it is, `null` included.
 *
 *     validations.add(path(User::pet, Pet::name), Validator.length(min = 5))   // "pet.name is less than ..."
 */
public fun <M, O : Any, T> path(
    outer: KProperty1<M, O?>,
    inner: KProperty1<O, T>,
): PropertyPath<M, T> =
    PropertyPath(listOf(outer.name, inner.name)) { model ->
        outer.get(model)?.let { PropertyPath.Resolved(inner.get(it)) }
    }
