package io.github.amine2233.validation

import kotlin.reflect.KProperty1

/**
 * An ordered collection of validators for a model [M]. [run] executes **all** of them and reports every
 * failure at once as a single [ValidateErrors].
 *
 *     val validations = validations<User> {
 *         add(User::name, Validator.length(min = 5) and Validator.alphanumeric)
 *         add(User::age, Validator.range(min = 18))
 *         add(User::email, Validator.nil or Validator.email)
 *     }
 *     validations.run(user)
 *
 * An instance returned by [validations] is frozen: its rules are fixed once the builder block returns, so it
 * is safe to share across threads, and calling [add] on it throws [IllegalStateException]. An instance built
 * with the constructor is never frozen; populate it fully before sharing it across threads, or use
 * [validations] to get a frozen one.
 */
public class Validations<M> {
    private val storage = mutableListOf<Validator<M>>()

    @Volatile
    private var frozen: List<Validator<M>>? = null

    private val rules: List<Validator<M>>
        get() = frozen ?: storage

    @PublishedApi
    internal fun freeze() {
        frozen = storage.toList()
    }

    /**
     * Adds [validator] for [property]. Failures are reported at [at], which defaults to the property name.
     *
     *     validations.add(User::name, Validator.length(min = 5) and Validator.alphanumeric)
     *     validations.add(User::name, Validator.alphanumeric, at = listOf("first_name"))
     *
     * @throws IllegalStateException if this instance is frozen (returned by [validations]).
     */
    public fun <T> add(
        property: KProperty1<M, T>,
        validator: Validator<T>,
        at: List<String> = listOf(property.name),
    ): Unit = add(property, "is ${validator.readable}", at) { validator.validate(it) }

    /**
     * Adds a custom check for [property]. Throw a [ValidationError] from [custom] when the value is invalid.
     *
     *     validations.add(User::name, "is vapor") { name ->
     *         if (name != "vapor") throw BasicValidationError("is not vapor")
     *     }
     *
     * @throws IllegalStateException if this instance is frozen (returned by [validations]).
     */
    public fun <T> add(
        property: KProperty1<M, T>,
        readable: String,
        at: List<String> = listOf(property.name),
        custom: (T) -> Unit,
    ) {
        add("${at.joinToString(".")}: $readable") { model ->
            try {
                custom(property.get(model))
            } catch (error: ValidationError) {
                throw error.prefixed(at)
            }
        }
    }

    /**
     * Adds [validator] for the nested property at [path]. Failures are reported at [at], which defaults to the
     * dotted path. The rule is skipped when the outer value is `null`.
     *
     *     validations.add(path(User::pet, Pet::name), Validator.length(min = 5))
     *
     * @throws IllegalStateException if this instance is frozen (returned by [validations]).
     */
    public fun <T> add(
        path: PropertyPath<M, T>,
        validator: Validator<T>,
        at: List<String> = path.names,
    ): Unit = add(path, "is ${validator.readable}", at) { validator.validate(it) }

    /**
     * Adds a custom check for the nested property at [path]. Throw a [ValidationError] from [custom] when the
     * value is invalid. The rule is skipped when the outer value is `null`.
     *
     *     validations.add(path(User::pet, Pet::name), "is not rex") { name ->
     *         if (name == "rex") throw BasicValidationError("is rex")
     *     }
     *
     * @throws IllegalStateException if this instance is frozen (returned by [validations]).
     */
    public fun <T> add(
        path: PropertyPath<M, T>,
        readable: String,
        at: List<String> = path.names,
        custom: (T) -> Unit,
    ) {
        add("${at.joinToString(".")}: $readable") { model ->
            val resolved = path.resolve(model) ?: return@add
            try {
                custom(resolved.value)
            } catch (error: ValidationError) {
                throw error.prefixed(at)
            }
        }
    }

    /**
     * Adds a custom check on the whole model. Throw a [ValidationError] from [custom] when it is invalid.
     *
     *     validations.add("passwords match") { model ->
     *         if (model.password != model.confirmation) throw BasicValidationError("has mismatching passwords")
     *     }
     *
     * @throws IllegalStateException if this instance is frozen (returned by [validations]).
     */
    public fun add(
        readable: String,
        custom: (M) -> Unit,
    ) {
        check(frozen == null) { "Validations is frozen: add must be called inside the validations { } block" }
        storage += Validator(readable, closure = custom)
    }

    /** Runs every validator on [model], throwing [ValidateErrors] if at least one fails. */
    public fun run(model: M) {
        val errors =
            rules.mapNotNull { validator ->
                try {
                    validator.validate(model)
                    null
                } catch (error: ValidationError) {
                    error
                }
            }
        if (errors.isNotEmpty()) throw ValidateErrors(errors)
    }

    override fun toString(): String = rules.joinToString("\n")
}

/** Builds a [Validations] for [M]. The returned instance is frozen: [Validations.add] on it throws [IllegalStateException]. */
public inline fun <M> validations(build: Validations<M>.() -> Unit): Validations<M> = Validations<M>().apply(build).also { it.freeze() }
