package sample.android

import io.github.amine2233.validation.*
import io.github.amine2233.validation.forms.*
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class SignUpFormTest {
    @Test
    fun untouchedFieldsHaveNothingToSay() {
        val form = SignUpForm()
        assertEquals(FieldValidation.None, form.validation(SignUpForm.Field.Email))
        assertTrue(form.isValid)
    }

    @Test
    fun firstFailingRuleWins() {
        val form = SignUpForm(password = "ab!")
        assertEquals(FieldValidation.Invalid("Enter a valid email address."), form.copy(email = "nope").validation(SignUpForm.Field.Email))
        assertEquals(FieldValidation.Invalid("At least 8 characters."), form.validation(SignUpForm.Field.Password))
        assertEquals(FieldValidation.Invalid("Enter a number."), form.copy(age = "x").validation(SignUpForm.Field.Age))
        assertEquals(FieldValidation.Invalid("You must be 18 or older."), form.copy(age = "9").validation(SignUpForm.Field.Age))
    }

    @Test
    fun submitReportsEveryFailure() {
        val error = assertFailsWith<ValidateErrors> { SignUpForm(email = "nope", password = "short!", age = "9").validate() }
        assertEquals(
            "email is not a valid email address, " +
                "password is less than required minimum of 8 characters and " +
                "password contains an invalid character: '!' (allowed: A-Z, a-z, 0-9), " +
                "age is less than 18",
            error.reason,
        )
        SignUpForm(email = "tanner@vapor.codes", password = "hunter2hunter2", age = "30").validate()
    }
}
