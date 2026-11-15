package io.github.amine2233.validation

/**
 * Runs a nested [AsyncValidatable]'s own validations, sync rules first and then its async rules; its
 * failures are reported under the property path.
 *
 * It is itself an async rule, so it runs in the parent's second phase, only after every one of the
 * parent's sync rules has passed.
 *
 *     validations.add(Order::buyer, AsyncValidator.valid())   // "buyer.email is already registered"
 */
public fun <T : AsyncValidatable<T>> AsyncValidator.Companion.valid(): AsyncValidator<T?> =
    AsyncValidator<T>("valid") { data -> data.validate() }.ignoringNull()
