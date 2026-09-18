package io.github.amine2233.validation

/**
 * Capable of being validated by checks that suspend. Implement [validations] and call [validate] on an
 * instance from a coroutine.
 *
 *     data class SignUpRequest(val email: String) : AsyncValidatable<SignUpRequest> {
 *         override fun validations() = asyncValidations<SignUpRequest> {
 *             add(SignUpRequest::email, Validator.email)
 *             add(SignUpRequest::email, emailIsAvailable)
 *         }
 *     }
 *     request.validate() // suspends; throws ValidateErrors listing every failure
 */
public interface AsyncValidatable<M : AsyncValidatable<M>> {
    /** The validations that run when [validate] is called on an instance of this type. */
    public fun validations(): AsyncValidations<M>
}

/** Validates the model, throwing a [ValidateErrors] if any validation fails. */
public suspend fun <M : AsyncValidatable<M>> M.validate(): Unit = validations().run(this)
