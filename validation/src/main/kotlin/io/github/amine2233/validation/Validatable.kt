package io.github.amine2233.validation

/**
 * Capable of being validated. Implement [validations] and call [validate] on an instance.
 *
 *     data class User(val name: String, val age: Int) : Validatable<User> {
 *         override fun validations() = validations<User> {
 *             add(User::name, Validator.count(min = 5) and Validator.alphanumeric)
 *             add(User::age, Validator.range(min = 18))
 *         }
 *     }
 *     User("Vapor", 3).validate() // throws ValidateErrors: "age is less than 18"
 */
public interface Validatable<M : Validatable<M>> {
    /** The validations that run when [validate] is called on an instance of this type. */
    public fun validations(): Validations<M>
}

/** Validates the model, throwing a [ValidateErrors] if any validation fails. */
public fun <M : Validatable<M>> M.validate(): Unit = validations().run(this)
