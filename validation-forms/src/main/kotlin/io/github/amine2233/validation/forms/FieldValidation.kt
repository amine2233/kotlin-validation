package io.github.amine2233.validation.forms

import io.github.amine2233.validation.ValidationError
import io.github.amine2233.validation.Validator

/** The state of one form field. `None` means "nothing to say yet" (untouched), not "valid". */
public sealed interface FieldValidation {
    public data object None : FieldValidation

    public data object Valid : FieldValidation

    public data class Invalid(
        override val message: String,
    ) : FieldValidation

    public val isValid: Boolean get() = this !is Invalid
    public val message: String? get() = (this as? Invalid)?.message
}

/** One rule: a validator plus the message shown when it fails. */
public class FieldRule<in T>(
    public val validator: Validator<T>,
    public val message: String,
)

/** Runs [rules] in order and reports the first failure: the first unmet rule is the one to fix first. */
public fun <T> evaluate(
    value: T,
    vararg rules: FieldRule<T>,
): FieldValidation {
    for (rule in rules) {
        try {
            rule.validator.validate(value)
        } catch (_: ValidationError) {
            return FieldValidation.Invalid(rule.message)
        }
    }
    return FieldValidation.Valid
}
