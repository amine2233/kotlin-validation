package io.github.amine2233.validation

/**
 * Runs a nested [Validatable]'s own validations; its failures are reported under the property path.
 *
 *     validations.add(User::pet, Validator.valid())   // "pet.name is less than required minimum of 5 characters"
 */
public fun <T : Validatable<T>> Validator.Companion.valid(): Validator<T?> =
    Validator<T>("valid") { data -> data.validate() }.ignoringNull()
