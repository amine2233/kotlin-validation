package sample.android

import io.github.amine2233.validation.*

/** Rules declared once, shared by the per-field UI checks and the whole-model `validate()`. */
object SignUpRules {
    val email = Validator.email
    val password = Validator.count(min = 8)
    val age = Validator.range(min = 18)
}

/** The state of one form field. `None` means "nothing to say yet" (untouched), not "valid". */
sealed interface FieldValidation {
    data object None : FieldValidation
    data object Valid : FieldValidation
    data class Invalid(override val message: String) : FieldValidation

    val isValid: Boolean get() = this !is Invalid
    val message: String? get() = (this as? Invalid)?.message
}

/** One rule: a validator plus the message shown when it fails. */
class FieldRule<in T>(val validator: Validator<T>, val message: String)

/** Runs [rules] in order and reports the first failure: the first unmet rule is the one to fix first. */
fun <T> evaluate(value: T, vararg rules: FieldRule<T>): FieldValidation {
    for (rule in rules) {
        try {
            rule.validator.validate(value)
        } catch (_: ValidationError) {
            return FieldValidation.Invalid(rule.message)
        }
    }
    return FieldValidation.Valid
}

data class SignUpForm(val email: String = "", val password: String = "", val age: String = "") : Validatable<SignUpForm> {
    enum class Field { Email, Password, Age }

    fun validation(field: Field): FieldValidation = when (field) {
        Field.Email -> if (email.isEmpty()) FieldValidation.None else evaluate(
            email,
            FieldRule(SignUpRules.email, "Enter a valid email address."),
        )
        Field.Password -> if (password.isEmpty()) FieldValidation.None else evaluate(
            password,
            FieldRule(SignUpRules.password, "At least 8 characters."),
            FieldRule(Validator.alphanumeric, "Letters and digits only."),
        )
        Field.Age -> if (age.isEmpty()) FieldValidation.None else evaluate(
            age.toIntOrNull(),
            FieldRule(Validator("a number") { if (it == null) throw BasicValidationError("is not a number") }, "Enter a number."),
            FieldRule(SignUpRules.age, "You must be 18 or older."),
        )
    }

    val isValid: Boolean get() = Field.entries.all { validation(it).isValid }

    /** The server-style check: every failure at once, used when the user submits. */
    override fun validations() = validations<SignUpForm> {
        add(SignUpForm::email, SignUpRules.email)
        add(SignUpForm::password, SignUpRules.password and Validator.alphanumeric)
        add(SignUpForm::age, "is at least 18") { SignUpRules.age.validate(it.toIntOrNull()) }
    }
}
