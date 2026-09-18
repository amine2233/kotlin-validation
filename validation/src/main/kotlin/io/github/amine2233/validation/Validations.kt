package io.github.amine2233.validation

import kotlin.reflect.KProperty1

/**
 * An ordered collection of validators for a model [M]. [run] executes **all** of them and reports every
 * failure at once as a single [ValidateErrors].
 *
 *     val validations = validations<User> {
 *         add(User::name, Validator.count(min = 5) and Validator.alphanumeric)
 *         add(User::age, Validator.range(min = 18))
 *         add(User::email, Validator.nil or Validator.email)
 *     }
 *     validations.run(user)
 */
public class Validations<M> {
    private val storage = mutableListOf<Validator<M>>()

    /**
     * Adds [validator] for [property]. Failures are reported at [at], which defaults to the property name.
     *
     *     validations.add(User::name, Validator.count(min = 5) and Validator.alphanumeric)
     *     validations.add(User::name, Validator.alphanumeric, at = listOf("first_name"))
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
     * Adds a custom check on the whole model. Throw a [ValidationError] from [custom] when it is invalid.
     *
     *     validations.add("passwords match") { model ->
     *         if (model.password != model.confirmation) throw BasicValidationError("has mismatching passwords")
     *     }
     */
    public fun add(readable: String, custom: (M) -> Unit) {
        storage += Validator(readable, closure = custom)
    }

    /** Runs every validator on [model], throwing [ValidateErrors] if at least one fails. */
    public fun run(model: M) {
        val errors = storage.mapNotNull { validator ->
            try {
                validator.validate(model)
                null
            } catch (error: ValidationError) {
                error
            }
        }
        if (errors.isNotEmpty()) throw ValidateErrors(errors)
    }

    override fun toString(): String = storage.joinToString("\n")
}

/** Builds a [Validations] for [M]. */
public inline fun <M> validations(build: Validations<M>.() -> Unit): Validations<M> = Validations<M>().apply(build)
