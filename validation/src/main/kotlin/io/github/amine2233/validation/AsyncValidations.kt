package io.github.amine2233.validation

import kotlin.reflect.KProperty1

/**
 * An ordered collection of validators for a model [M], where some checks suspend.
 *
 * [run] executes the synchronous rules first and reports **all** of their failures at once; the async rules
 * run afterwards, one at a time, and only when every synchronous rule passed — no network call is spent on
 * input that is already invalid.
 *
 *     val validations = asyncValidations<SignUpRequest> {
 *         add(SignUpRequest::email, Validator.email)
 *         add(SignUpRequest::email, emailIsAvailable)
 *         add(SignUpRequest::username, "is taken") { if (api.isTaken(it)) throw BasicValidationError("is taken") }
 *     }
 *     validations.run(request)
 *
 * An instance returned by [asyncValidations] is frozen: its sync and async rules are fixed once the builder
 * block returns, so it is safe to share across threads, and calling [add] on it throws
 * [IllegalStateException]. An instance built with the constructor is never frozen; populate it fully before
 * sharing it across threads, or use [asyncValidations] to get a frozen one.
 */
public class AsyncValidations<M> {
    private val sync = Validations<M>()
    private val storage = mutableListOf<AsyncValidator<M>>()

    @Volatile
    private var frozen: List<AsyncValidator<M>>? = null

    private val rules: List<AsyncValidator<M>>
        get() = frozen ?: storage

    @PublishedApi
    internal fun freeze() {
        sync.freeze()
        frozen = storage.toList()
    }

    private fun checkNotFrozen() {
        check(frozen == null) { "AsyncValidations is frozen: add must be called inside the asyncValidations { } block" }
    }

    /**
     * Adds a synchronous [validator] for [property], run in the first phase. Failures are reported at [at],
     * which defaults to the property name.
     *
     *     validations.add(SignUpRequest::email, Validator.email)
     *
     * @throws IllegalStateException if this instance is frozen (returned by [asyncValidations]).
     */
    public fun <T> add(
        property: KProperty1<M, T>,
        validator: Validator<T>,
        at: List<String> = listOf(property.name),
    ) {
        checkNotFrozen()
        sync.add(property, validator, at)
    }

    /**
     * Adds a suspending [validator] for [property], run in the second phase.
     *
     *     validations.add(SignUpRequest::email, emailIsAvailable)
     *
     * @throws IllegalStateException if this instance is frozen (returned by [asyncValidations]).
     */
    public fun <T> add(
        property: KProperty1<M, T>,
        validator: AsyncValidator<T>,
        at: List<String> = listOf(property.name),
    ): Unit = add(property, "is ${validator.readable}", at) { validator.validate(it) }

    /**
     * Adds a custom suspending check for [property], run in the second phase. Throw a [ValidationError] from
     * [custom] when the value is invalid.
     *
     *     validations.add(SignUpRequest::email, "is not already registered") { email ->
     *         if (api.isRegistered(email)) throw BasicValidationError("is already registered")
     *     }
     *
     * @throws IllegalStateException if this instance is frozen (returned by [asyncValidations]).
     */
    public fun <T> add(
        property: KProperty1<M, T>,
        readable: String,
        at: List<String> = listOf(property.name),
        custom: suspend (T) -> Unit,
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
     * Adds a synchronous [validator] for the nested property at [path], run in the first phase. Failures are
     * reported at [at], which defaults to the dotted path. The rule is skipped when the outer value is `null`.
     *
     *     validations.add(path(SignUpRequest::address, Address::zip), Validator.count(min = 5))
     *
     * @throws IllegalStateException if this instance is frozen (returned by [asyncValidations]).
     */
    public fun <T> add(
        path: PropertyPath<M, T>,
        validator: Validator<T>,
        at: List<String> = path.names,
    ) {
        checkNotFrozen()
        sync.add(path, validator, at)
    }

    /**
     * Adds a suspending [validator] for the nested property at [path], run in the second phase. The rule is
     * skipped when the outer value is `null`.
     *
     *     validations.add(path(SignUpRequest::contact, Contact::email), emailIsAvailable)
     *
     * @throws IllegalStateException if this instance is frozen (returned by [asyncValidations]).
     */
    public fun <T> add(
        path: PropertyPath<M, T>,
        validator: AsyncValidator<T>,
        at: List<String> = path.names,
    ): Unit = add(path, "is ${validator.readable}", at) { validator.validate(it) }

    /**
     * Adds a custom suspending check for the nested property at [path], run in the second phase. Throw a
     * [ValidationError] from [custom] when the value is invalid. The rule is skipped when the outer value is
     * `null`.
     *
     *     validations.add(path(SignUpRequest::contact, Contact::email), "is not already registered") { email ->
     *         if (api.isRegistered(email)) throw BasicValidationError("is already registered")
     *     }
     *
     * @throws IllegalStateException if this instance is frozen (returned by [asyncValidations]).
     */
    public fun <T> add(
        path: PropertyPath<M, T>,
        readable: String,
        at: List<String> = path.names,
        custom: suspend (T) -> Unit,
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
     * Adds a custom suspending check on the whole model, run in the second phase. Throw a [ValidationError]
     * from [custom] when the model is invalid.
     *
     *     validations.add("has an unused invite code") { model ->
     *         if (api.isSpent(model.inviteCode)) throw BasicValidationError("has a spent invite code")
     *     }
     *
     * @throws IllegalStateException if this instance is frozen (returned by [asyncValidations]).
     */
    public fun add(
        readable: String,
        custom: suspend (M) -> Unit,
    ) {
        checkNotFrozen()
        storage += AsyncValidator(readable, closure = custom)
    }

    /**
     * Runs the synchronous rules, then — only if they all passed — every async rule in declaration order,
     * throwing [ValidateErrors] if at least one fails.
     *
     * Only [ValidationError]s are collected: a `CancellationException`, or the `IOException` of a request
     * that never reached the server, propagates to the caller. Catch those inside your own rule and throw a
     * [BasicValidationError] if "could not check" should read as "invalid".
     */
    public suspend fun run(model: M) {
        sync.run(model)
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

    override fun toString(): String =
        listOf(sync.toString(), rules.joinToString("\n"))
            .filter { it.isNotEmpty() }
            .joinToString("\n")
}

/** Builds an [AsyncValidations] for [M]. The returned instance is frozen: [AsyncValidations.add] on it throws [IllegalStateException]. */
public inline fun <M> asyncValidations(build: AsyncValidations<M>.() -> Unit): AsyncValidations<M> =
    AsyncValidations<M>().apply(build).also { it.freeze() }
