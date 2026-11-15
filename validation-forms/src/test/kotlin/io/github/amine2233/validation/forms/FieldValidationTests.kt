package io.github.amine2233.validation.forms

import io.github.amine2233.validation.BasicValidationError
import io.github.amine2233.validation.Validator
import io.github.amine2233.validation.alphanumeric
import io.github.amine2233.validation.length
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class FieldValidationTests {
    private val minLength = FieldRule(Validator.length(min = 8), "At least 8 characters.")
    private val lettersAndDigits = FieldRule(Validator.alphanumeric, "Letters and digits only.")

    @Test
    fun noneIsValidWithoutMessage() {
        assertTrue(FieldValidation.None.isValid)
        assertNull(FieldValidation.None.message)
    }

    @Test
    fun validIsValidWithoutMessage() {
        assertTrue(FieldValidation.Valid.isValid)
        assertNull(FieldValidation.Valid.message)
    }

    @Test
    fun invalidIsNotValidAndCarriesItsMessage() {
        val invalid = FieldValidation.Invalid("Enter a number.")
        assertFalse(invalid.isValid)
        assertEquals("Enter a number.", invalid.message)
    }

    @Test
    fun firstFailingRuleWins() {
        assertEquals(FieldValidation.Invalid("At least 8 characters."), evaluate("ab!", minLength, lettersAndDigits))
        assertEquals(FieldValidation.Invalid("Letters and digits only."), evaluate("abcdefgh!", minLength, lettersAndDigits))
    }

    @Test
    fun allRulesPassingIsValid() {
        assertEquals(FieldValidation.Valid, evaluate("abcdefgh1", minLength, lettersAndDigits))
    }

    @Test
    fun noRulesIsValid() {
        assertEquals(FieldValidation.Valid, evaluate("anything"))
    }

    @Test
    fun ruleFailureUsesTheRuleMessageNotTheValidatorReason() {
        val notANumber =
            FieldRule<Int?>(
                Validator("a number") {
                    if (it ==
                        null
                    ) {
                        throw BasicValidationError("is not a number")
                    }
                },
                "Enter a number.",
            )
        assertEquals(FieldValidation.Invalid("Enter a number."), evaluate(null, notANumber))
    }

    @Test
    fun nonValidationErrorsPropagate() {
        val broken = FieldRule(Validator<String>("broken") { throw IllegalStateException("boom") }, "Never shown.")
        val error = assertFailsWith<IllegalStateException> { evaluate("x", broken) }
        assertEquals("boom", error.message)
    }
}
