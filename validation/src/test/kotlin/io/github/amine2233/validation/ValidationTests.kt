package io.github.amine2233.validation

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertTrue

class ValidationTests {
    @Test
    fun validate() {
        val user =
            User(
                name = "Tanner",
                age = 23,
                pet = Pet(name = "Zizek Pulaski", age = 4),
                preferedColors = listOf("blue?", "green?"),
                luckyNumber = 7,
                email = "tanner@vapor.codes",
            )
        user.validate()
        user.pet.validate()

        val secondUser =
            User(
                name = "Natan",
                age = 30,
                pet = Pet(name = "Nina Simone", age = 4),
                preferedColors = listOf("pink"),
                profilePictureURL = "https://www.somedomain.com/somePath.png",
                email = "natan@vapor.codes",
            )
        secondUser.validate()
    }

    @Test
    fun validateCollectsEveryFailureWithPaths() {
        val user = User(name = "Bob", age = 12, pet = Pet(name = "Rex", age = 1), preferedColors = emptyList())
        val error = assertFailsWith<ValidateErrors> { user.validate() }
        assertEquals(
            "name is less than required minimum of 5 characters, " +
                "age is less than 18, " +
                "pet.name is less than required minimum of 5 characters, pet.age is less than 3, " +
                "preferedColors is empty",
            error.reason,
        )
        assertEquals(error.reason, error.message)
        assertEquals(4, error.errors.size)
    }

    @Test
    fun ascii() {
        Validator.ascii.validate("ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789")
        Validator.ascii.validate("\n\r\t")
        assertFailsWith<ValidationError> { Validator.ascii.validate("\n\r\tĩ") }
        Validator.ascii.validate(" !\"#$%&'()*+,-./:;<=>?@[\\]^_`{|}~")
        val error =
            assertFailsWith<ValidationError> {
                Validator.ascii.validate("ABCDEFGHIJKLMNOPQR🤠STUVWXYZabcdefghijklmnopqrstuvwxyz0123456789+/")
            }
        assertEquals("data contains an invalid character: '🤠' (allowed: ASCII)", error.reason)
    }

    @Test
    fun alphanumeric() {
        Validator.alphanumeric.validate("ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789")
        val error =
            assertFailsWith<ValidationError> {
                Validator.alphanumeric.validate("ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789+/")
            }
        assertEquals("data contains an invalid character: '+' (allowed: A-Z, a-z, 0-9)", error.reason)
    }

    @Test
    fun characterSetUnion() {
        val validator = Validator.characterSet(CharacterSet.alphanumerics + CharacterSet.whitespaces + CharacterSet.of("_-"))
        validator.validate("Zizek Pulaski_the-2nd")
        assertFailsWith<ValidationError> { validator.validate("Zizek\nPulaski") }
        assertEquals("in character set (A-Z, a-z, 0-9, whitespace, _-)", validator.readable)
    }

    @Test
    fun empty() {
        Validator.empty.validate("")
        assertFailsWith<ValidationError> { Validator.empty.validate("something") }
        Validator.empty.validate(emptyList<Int>())
        assertFailsWith<ValidationError> { Validator.empty.validate(listOf(1, 2)) }
        Validator.empty.validate(null)
        (!Validator.empty).validate(mapOf(1 to 2))
        assertFailsWith<IllegalArgumentException> { Validator.empty.validate(42) }
    }

    @Test
    fun email() {
        Validator.email.validate("tanner@vapor.codes")
        assertFailsWith<ValidationError> { Validator.email.validate("tanner@vapor.codestanner@vapor.codes") }
        assertFailsWith<ValidationError> { Validator.email.validate("tanner@vapor.codes.") }
        assertFailsWith<ValidationError> { Validator.email.validate("tanner@@vapor.codes") }
        assertFailsWith<ValidationError> { Validator.email.validate("@vapor.codes") }
        assertFailsWith<ValidationError> { Validator.email.validate("tanner@codes") }
        assertFailsWith<ValidationError> { Validator.email.validate("asdf") }
    }

    @Test
    fun range() {
        Validator.range(-5..5).validate(4)
        Validator.range(-5..5).validate(5)
        Validator.range(-5..5).validate(-5)
        val tooHigh = assertFailsWith<ValidationError> { Validator.range(-5..5).validate(6) }
        assertEquals("data is greater than 5", tooHigh.reason)
        val tooLow = assertFailsWith<ValidationError> { Validator.range(-5..5).validate(-6) }
        assertEquals("data is less than -5", tooLow.reason)

        Validator.range(min = 5).validate(Int.MAX_VALUE)
        Validator.range(max = 1.5).validate(1.0)
        assertFailsWith<ValidationError> { Validator.range(max = 1.5).validate(2.0) }

        Validator.range(-5..<6).validate(-5)
        Validator.range(-5..<6).validate(-4)
        Validator.range(-5..<6).validate(5)
        assertFailsWith<ValidationError> { Validator.range(-5..<6).validate(-6) }
        assertFailsWith<ValidationError> { Validator.range(-5..<6).validate(6) }
    }

    @Suppress("DEPRECATION")
    @Test
    fun countCharacters() {
        val validator = Validator.count(1..6)
        validator.validate("1")
        validator.validate("123")
        validator.validate("123456")
        val tooShort = assertFailsWith<ValidationError> { validator.validate("") }
        assertEquals("data is less than required minimum of 1 character", tooShort.reason)
        val tooLong = assertFailsWith<ValidationError> { validator.validate("1234567") }
        assertEquals("data is greater than required maximum of 6 characters", tooLong.reason)
    }

    @Suppress("DEPRECATION")
    @Test
    fun countItems() {
        val validator = Validator.count(1..6)
        validator.validate(listOf(1))
        validator.validate(listOf(1, 2, 3))
        validator.validate(listOf(1, 2, 3, 4, 5, 6))
        val tooShort = assertFailsWith<ValidationError> { validator.validate(emptyList<Int>()) }
        assertEquals("data is less than required minimum of 1 item", tooShort.reason)
        val tooLong = assertFailsWith<ValidationError> { validator.validate(listOf(1, 2, 3, 4, 5, 6, 7)) }
        assertEquals("data is greater than required maximum of 6 items", tooLong.reason)
        assertEquals("of length between 1 and 6", validator.readable)
        assertEquals("of length at least 5", Validator.count(min = 5).readable)
    }

    @Suppress("DEPRECATION")
    @Test
    fun countUsesUserPerceivedCharacters() {
        Validator.count(max = 1).validate("🤠")
        Validator.count(max = 1).validate("e\u0301")
        assertFailsWith<ValidationError> { Validator.count(max = 1).validate("ab") }
        Validator.count(min = 1).validate(null)
    }

    @Test
    fun rangeRejectsInvertedBounds() {
        assertFailsWith<IllegalArgumentException> { Validator.range(10..1) }
        val error = assertFailsWith<IllegalArgumentException> { Validator.range(min = 10, max = 1) }
        assertEquals("min (10) must not be greater than max (1)", error.message)
        assertFailsWith<IllegalArgumentException> { Validator.range(min = 2.0, max = 1.0) }
    }

    @Test
    fun rangeAcceptsWellFormedBounds() {
        Validator.range(5..5).validate(5)
        Validator.range(min = 5, max = 5).validate(5)
        Validator.range(min = 18)
        Validator.range(max = 1.5)
        Validator.range<Int>()
        Validator.range(-5..5)
        Validator.range(min = -10, max = -1).validate(-5)
    }

    @Suppress("DEPRECATION")
    @Test
    fun countRejectsInvertedBounds() {
        assertFailsWith<IllegalArgumentException> { Validator.count(5..<5) }
        assertFailsWith<IllegalArgumentException> { Validator.count(10..1) }
        val error = assertFailsWith<IllegalArgumentException> { Validator.count(min = 10, max = 1) }
        assertEquals("min (10) must not be greater than max (1)", error.message)
    }

    @Suppress("DEPRECATION")
    @Test
    fun countRejectsNegativeBounds() {
        val minError = assertFailsWith<IllegalArgumentException> { Validator.count(min = -1) }
        assertEquals("min (-1) must not be negative", minError.message)
        val maxError = assertFailsWith<IllegalArgumentException> { Validator.count(max = -1) }
        assertEquals("max (-1) must not be negative", maxError.message)
        assertFailsWith<IllegalArgumentException> { Validator.count(-1..3) }
    }

    @Suppress("DEPRECATION")
    @Test
    fun countAcceptsWellFormedBounds() {
        Validator.count(3..3).validate("abc")
        Validator.count(min = 3, max = 3).validate("abc")
        Validator.count(min = 5)
        Validator.count(max = 10)
        Validator.count()
        Validator.count(max = 0).validate("")
        Validator.count(min = 0).validate("")
        Validator.count(0..0).validate(emptyList<Int>())
    }

    @Test
    fun lengthCountsCharacters() {
        val validator = Validator.length(1..6)
        validator.validate("1")
        validator.validate("123456")
        validator.validate(null)
        val tooShort = assertFailsWith<ValidationError> { validator.validate("") }
        assertEquals("data is less than required minimum of 1 character", tooShort.reason)
        val tooLong = assertFailsWith<ValidationError> { validator.validate("1234567") }
        assertEquals("data is greater than required maximum of 6 characters", tooLong.reason)
        assertEquals("of length between 1 and 6", validator.readable)
        assertEquals("of length at least 5", Validator.length(min = 5).readable)
        assertEquals("of length at most 5", Validator.length(max = 5).readable)
        assertEquals("valid", Validator.length().readable)
    }

    @Test
    fun lengthUsesUserPerceivedCharacters() {
        Validator.length(max = 1).validate("🤠")
        Validator.length(max = 1).validate("e\u0301")
        Validator.length(min = 1).validate("🤠")
        Validator.length(min = 1).validate("e\u0301")
        val error = assertFailsWith<ValidationError> { Validator.length(max = 1).validate("ab") }
        assertEquals("data is greater than required maximum of 1 character", error.reason)
    }

    @Test
    fun lengthRejectsMalformedBounds() {
        assertEquals("min (-1) must not be negative", assertFailsWith<IllegalArgumentException> { Validator.length(min = -1) }.message)
        assertEquals("max (-1) must not be negative", assertFailsWith<IllegalArgumentException> { Validator.length(max = -1) }.message)
        assertEquals(
            "min (10) must not be greater than max (1)",
            assertFailsWith<IllegalArgumentException> { Validator.length(min = 10, max = 1) }.message,
        )
        assertEquals(
            "min (5) must not be greater than max (4)",
            assertFailsWith<IllegalArgumentException> { Validator.length(5..<5) }.message,
        )
    }

    @Test
    fun sizeCountsItems() {
        val validator = Validator.size(1..6)
        validator.validate(listOf(1))
        validator.validate(setOf(1, 2, 3, 4, 5, 6))
        validator.validate(null)
        val tooShort = assertFailsWith<ValidationError> { validator.validate(emptyList<Int>()) }
        assertEquals("data is less than required minimum of 1 item", tooShort.reason)
        val tooLong = assertFailsWith<ValidationError> { validator.validate(listOf(1, 2, 3, 4, 5, 6, 7)) }
        assertEquals("data is greater than required maximum of 6 items", tooLong.reason)
        assertEquals("of length between 1 and 6", validator.readable)
    }

    @Test
    fun sizeRejectsMalformedBounds() {
        assertEquals("min (-1) must not be negative", assertFailsWith<IllegalArgumentException> { Validator.size(min = -1) }.message)
        assertEquals("max (-1) must not be negative", assertFailsWith<IllegalArgumentException> { Validator.size(max = -1) }.message)
        assertEquals(
            "min (10) must not be greater than max (1)",
            assertFailsWith<IllegalArgumentException> { Validator.size(min = 10, max = 1) }.message,
        )
        assertEquals(
            "min (5) must not be greater than max (4)",
            assertFailsWith<IllegalArgumentException> { Validator.size(5..<5) }.message,
        )
    }

    @Test
    fun mapSizeCountsEntries() {
        val validator = Validator.mapSize(min = 1, max = 2)
        validator.validate(mapOf("a" to 1))
        validator.validate(mapOf("a" to 1, "b" to 2))
        validator.validate(null)
        val tooShort = assertFailsWith<ValidationError> { validator.validate(emptyMap<String, Int>()) }
        assertEquals("data is less than required minimum of 1 item", tooShort.reason)
        val tooLong = assertFailsWith<ValidationError> { validator.validate(mapOf("a" to 1, "b" to 2, "c" to 3)) }
        assertEquals("data is greater than required maximum of 2 items", tooLong.reason)
        assertEquals("of length between 1 and 2", validator.readable)
    }

    @Test
    fun mapSizeRejectsMalformedBounds() {
        assertEquals("min (-1) must not be negative", assertFailsWith<IllegalArgumentException> { Validator.mapSize(min = -1) }.message)
        assertEquals("max (-1) must not be negative", assertFailsWith<IllegalArgumentException> { Validator.mapSize(max = -1) }.message)
        assertEquals(
            "min (10) must not be greater than max (1)",
            assertFailsWith<IllegalArgumentException> { Validator.mapSize(min = 10, max = 1) }.message,
        )
        assertEquals(
            "min (5) must not be greater than max (4)",
            assertFailsWith<IllegalArgumentException> { Validator.mapSize(5..<5) }.message,
        )
    }

    @Test
    fun arraySizeCountsElements() {
        val validator = Validator.arraySize(2..3)
        validator.validate(arrayOf(1, 2))
        validator.validate(arrayOf("a", "b", "c"))
        validator.validate(null)
        val tooShort = assertFailsWith<ValidationError> { validator.validate(arrayOf(1)) }
        assertEquals("data is less than required minimum of 2 items", tooShort.reason)
        val tooLong = assertFailsWith<ValidationError> { validator.validate(arrayOf(1, 2, 3, 4)) }
        assertEquals("data is greater than required maximum of 3 items", tooLong.reason)
        assertEquals("of length between 2 and 3", validator.readable)
        val single = assertFailsWith<ValidationError> { Validator.arraySize(max = 1).validate(arrayOf(1, 2)) }
        assertEquals("data is greater than required maximum of 1 item", single.reason)
    }

    @Test
    fun arraySizeRejectsMalformedBounds() {
        assertEquals("min (-1) must not be negative", assertFailsWith<IllegalArgumentException> { Validator.arraySize(min = -1) }.message)
        assertEquals("max (-1) must not be negative", assertFailsWith<IllegalArgumentException> { Validator.arraySize(max = -1) }.message)
        assertEquals(
            "min (10) must not be greater than max (1)",
            assertFailsWith<IllegalArgumentException> { Validator.arraySize(min = 10, max = 1) }.message,
        )
        assertEquals(
            "min (5) must not be greater than max (4)",
            assertFailsWith<IllegalArgumentException> { Validator.arraySize(5..<5) }.message,
        )
    }

    @Test
    fun url() {
        Validator.url.validate("https://www.somedomain.com/somepath.png")
        Validator.url.validate("https://www.somedomain.com/")
        Validator.url.validate("file:///Users/vapor/rocks/somePath.png")
        assertFailsWith<ValidationError> { Validator.url.validate("www.somedomain.com/") }
        assertFailsWith<ValidationError> { Validator.url.validate("bananas") }
        assertFailsWith<ValidationError> { Validator.url.validate("http://exa mple.com") }
        assertFailsWith<ValidationError> { Validator.url.validate("mailto:tanner@vapor.codes") }
        Validator.url.validate("FILE:///x")
        Validator.url.validate("http://my_host.com/")
        Validator.url.validate("http://例え.jp/")
    }

    @Test
    fun oneOf() {
        Validator.oneOf(5, 7).validate(7)
        val error = assertFailsWith<ValidationError> { Validator.oneOf(5, 7).validate(6) }
        assertEquals("data is not in (5, 7)", error.reason)
        Validator.oneOf(listOf("a", "b")).validate("a")
    }

    @Test
    fun nilCombinators() {
        val nilOrEmail: Validator<String?> = Validator.nil or Validator.email
        nilOrEmail.validate(null)
        nilOrEmail.validate("tanner@vapor.codes")
        val orError = assertFailsWith<ValidationError> { nilOrEmail.validate("asdf") }
        assertEquals("data is not nil and data is not a valid email address", orError.reason)

        val required: Validator<String?> = !Validator.nil and Validator.email
        required.validate("tanner@vapor.codes")
        assertEquals("data is nil", assertFailsWith<ValidationError> { required.validate(null) }.reason)
        assertEquals(
            "data is not a valid email address",
            assertFailsWith<ValidationError> { required.validate("asdf") }.reason,
        )
        assertEquals("not nil and is a valid email address", required.readable)
    }

    @Test
    fun notKeepsIgnoringNull() {
        (!Validator.email).validate(null)
        (!Validator.oneOf("admin")).validate(null)
        (!(Validator.email and Validator.url)).validate(null)
        assertFailsWith<ValidationError> { (!Validator.nil).validate(null) }
        assertFailsWith<ValidationError> { (!Validator.empty).validate(null) }
        assertFailsWith<ValidationError> { (!(Validator.nil or Validator.email)).validate(null) }
    }

    @Test
    fun ignoringNullLiftsCustomValidators() {
        val isVapor = Validator<String>("vapor") { if (it != "vapor") throw BasicValidationError("is not vapor") }
        val validator: Validator<String?> = Validator.nil or isVapor.ignoringNull()
        validator.validate(null)
        validator.validate("vapor")
        assertFailsWith<ValidationError> { validator.validate("swift") }
    }

    @Test
    fun flattenGivesOneErrorPerFailingRuleWithFullPaths() {
        val user = User(name = "a!", age = 12, pet = Pet(name = "Rex", age = 1), preferedColors = emptyList())
        val error = assertFailsWith<ValidateErrors> { user.validate() }
        assertEquals(
            listOf(
                listOf("name") to "name is less than required minimum of 5 characters",
                listOf("name") to "name contains an invalid character: '!' (allowed: A-Z, a-z, 0-9)",
                listOf("age") to "age is less than 18",
                listOf("pet", "name") to "pet.name is less than required minimum of 5 characters",
                listOf("pet", "age") to "pet.age is less than 3",
                listOf("preferedColors") to "preferedColors is empty",
            ),
            error.flatten().map { it.path to it.reason },
        )
    }

    @Test
    fun flattenGivesPathLessDetailPerFailingRule() {
        val user = User(name = "a!", age = 12, pet = Pet(name = "Rex", age = 1), preferedColors = emptyList())
        val error = assertFailsWith<ValidateErrors> { user.validate() }
        assertEquals(
            listOf(
                listOf("name") to "is less than required minimum of 5 characters",
                listOf("name") to "contains an invalid character: '!' (allowed: A-Z, a-z, 0-9)",
                listOf("age") to "is less than 18",
                listOf("pet", "name") to "is less than required minimum of 5 characters",
                listOf("pet", "age") to "is less than 3",
                listOf("preferedColors") to "is empty",
            ),
            error.flatten().map { it.path to it.detail },
        )
    }

    @Test
    fun flattenGivesPathLessDetailForOr() {
        val user = User(name = "abcde", age = 18, email = "asdf", pet = Pet(name = "Kitty", age = 4), preferedColors = listOf("blue"))
        val flattened = assertFailsWith<ValidateErrors> { user.validate() }.flatten()
        assertEquals(listOf(listOf("email"), listOf("email")), flattened.map { it.path })
        assertEquals(
            listOf("is not nil and is not a valid email address", "is not a valid email address and is not nil"),
            flattened.map { it.detail },
        )
        assertEquals("email is not nil and email is not a valid email address", flattened.first().reason)
    }

    @Test
    fun detailOmitsOwnPathOnCompositeErrors() {
        val andError = assertFailsWith<ValidationError> { (Validator.count(min = 5) and Validator.alphanumeric).validate("a!") }
        val andDetail = "is less than required minimum of 5 characters and contains an invalid character: '!' (allowed: A-Z, a-z, 0-9)"
        assertEquals(andDetail, andError.detail)
        val prefixedAnd = andError.prefixed(listOf("name"))
        assertEquals(andDetail, prefixedAnd.detail)
        assertTrue(prefixedAnd.reason.startsWith("name is less than"))

        val petError = assertFailsWith<ValidateErrors> { Pet("Rex", 1).validate() }
        val petDetail = "name is less than required minimum of 5 characters, age is less than 3"
        assertEquals(petDetail, petError.detail)
        val prefixedPet = petError.prefixed(listOf("pet"))
        assertEquals(petDetail, prefixedPet.detail)
        assertTrue(prefixedPet.reason.startsWith("pet.name is less than"))
    }

    @Test
    fun customErrorDetailDefaultsToReason() {
        val error = CustomValidationError()
        assertEquals("custom failure", error.detail)
        assertEquals(error.reason, error.detail)
    }

    @Test
    fun builtInsSkipNull() {
        Validator.email.validate(null)
        Validator.url.validate(null)
        Validator.range(min = 1).validate(null)
        Validator.oneOf(1).validate(null)
        Validator.alphanumeric.validate(null)
        assertFailsWith<ValidationError> { Validator.nil.validate("x") }
    }

    @Test
    fun characterSets() {
        Validator.alphanumeric.validate("e\u0301½")
        Validator.characterSet(CharacterSet.letters).validate("Zoë")
        Validator.characterSet(CharacterSet.decimalDigits).validate("0123456789")
        assertFailsWith<ValidationError> { Validator.characterSet(CharacterSet.decimalDigits).validate("1a") }
        Validator.characterSet(CharacterSet.newlines + CharacterSet.whitespaces).validate("\n\r \t\u2028")
        assertEquals(listOf("A-Z", "a-z", "0-9"), (CharacterSet.alphanumerics + CharacterSet.letters).traits)

        val vowels = CharacterSet(emptyList()) { it in "aeiou".codePoints().toArray() }
        assertEquals("in character set", Validator.characterSet(vowels).readable)
        val error = assertFailsWith<ValidationError> { Validator.characterSet(vowels).validate("ab") }
        assertEquals("data contains an invalid character: 'b'", error.reason)
    }

    @Suppress("DEPRECATION")
    @Test
    fun countHalfOpenRangeAndRootPath() {
        Validator.count(5..<10).validate("123456789")
        assertFailsWith<ValidationError> { Validator.count(5..<10).validate("1234567890") }
        val validations = validations<Pet> { add(Pet::name, Validator.count(max = 2), at = emptyList()) }
        val error = assertFailsWith<ValidateErrors> { validations.run(Pet("Rex", 3)) }
        assertEquals("data is greater than required maximum of 2 characters", error.reason)
    }

    @Test
    fun unexpectedExceptionsPropagate() {
        val validations =
            validations<Pet> {
                add(Pet::name, Validator("valid") { error("boom") })
            }
        assertFailsWith<IllegalStateException> { validations.run(Pet("Rex", 3)) }
        assertFailsWith<IllegalStateException> { (Validator<String>("x") { error("boom") } or Validator.email).validate("a") }
    }

    @Test
    fun andReportsBothFailures() {
        val validator = Validator.length(min = 5) and Validator.alphanumeric
        assertEquals("of length at least 5 and is in character set (A-Z, a-z, 0-9)", validator.readable)
        val error = assertFailsWith<ValidationError> { validator.validate("a!") }
        assertEquals(
            "data is less than required minimum of 5 characters and " +
                "data contains an invalid character: '!' (allowed: A-Z, a-z, 0-9)",
            error.reason,
        )
    }

    @Test
    fun customValidatorType() {
        val isVapor =
            object : ValidatorType<String> {
                override val validatorReadable = "vapor"

                override fun validate(data: String) {
                    if (data != "vapor") throw BasicValidationError("is not vapor")
                }
            }
        isVapor.validator().validate("vapor")
        assertFailsWith<ValidationError> { isVapor.validator().validate("swift") }
        assertEquals("not vapor", (!isVapor.validator()).readable)
    }

    @Test
    fun explicitPathAndCustomClosures() {
        val validations =
            validations<Pet> {
                add(Pet::name, Validator.alphanumeric, at = listOf("pet", "first_name"))
                add(Pet::age, "is even") { age -> if (age % 2 != 0) throw BasicValidationError("is odd") }
                add("has a name matching its age") { pet ->
                    if (pet.name.length != pet.age) throw BasicValidationError("name/age mismatch")
                }
            }
        val error = assertFailsWith<ValidateErrors> { validations.run(Pet(name = "Re x", age = 3)) }
        assertEquals(
            "pet.first_name contains an invalid character: ' ' (allowed: A-Z, a-z, 0-9), age is odd, data name/age mismatch",
            error.reason,
        )
        assertEquals(
            "pet.first_name: is in character set (A-Z, a-z, 0-9)\nage: is even\nhas a name matching its age",
            validations.toString(),
        )
        assertEquals(listOf(listOf("pet", "first_name"), listOf("age"), emptyList()), error.flatten().map { it.path })
    }

    @Test
    fun nestedValidatableReportsDottedPaths() {
        data class Toy(
            val name: String,
        ) : Validatable<Toy> {
            override fun validations() = validations<Toy> { add(Toy::name, Validator.length(min = 3)) }
        }

        data class Owner(
            val toy: Toy?,
        ) : Validatable<Owner> {
            override fun validations() = validations<Owner> { add(Owner::toy, Validator.valid()) }
        }
        Owner(toy = null).validate()
        val error = assertFailsWith<ValidationError> { Owner(Toy("x")).validate() }
        assertIs<ValidateErrors>(error)
        assertEquals("toy.name is less than required minimum of 3 characters", error.reason)
    }

    @Test
    fun builtValidationsRejectEveryAdd() {
        val ran = mutableListOf<String>()
        val validations =
            validations<Pet> {
                add(Pet::name, Validator.length(min = 5))
                add(Pet::age, "is even") { age -> if (age % 2 != 0) throw BasicValidationError("is odd") }
                add("has a long name") { pet -> if (pet.name.length < 10) throw BasicValidationError("has a short name") }
            }
        val before = validations.toString()

        assertFailsWith<IllegalStateException> { validations.add(Pet::name, Validator.alphanumeric) }
        assertEquals(before, validations.toString())
        assertFailsWith<IllegalStateException> { validations.add(Pet::age, "late") { ran += "property" } }
        assertEquals(before, validations.toString())
        assertFailsWith<IllegalStateException> { validations.add("late") { ran += "model" } }
        assertEquals(before, validations.toString())

        val error = assertFailsWith<ValidateErrors> { validations.run(Pet(name = "Rex", age = 3)) }
        assertEquals(
            listOf(
                listOf("name") to "name is less than required minimum of 5 characters",
                listOf("age") to "age is odd",
                emptyList<String>() to "data has a short name",
            ),
            error.flatten().map { it.path to it.reason },
        )
        assertEquals(emptyList(), ran)
    }

    @Test
    fun directlyConstructedValidationsAcceptAdd() {
        val validations = Validations<Pet>()
        validations.add(Pet::name, Validator.length(min = 5))
        validations.add(Pet::age, "is even") { age -> if (age % 2 != 0) throw BasicValidationError("is odd") }
        validations.add("has a long name") { pet -> if (pet.name.length < 10) throw BasicValidationError("has a short name") }
        assertEquals("name: is of length at least 5\nage: is even\nhas a long name", validations.toString())
        val error = assertFailsWith<ValidateErrors> { validations.run(Pet(name = "Rex", age = 3)) }
        assertEquals(
            "name is less than required minimum of 5 characters, age is odd, data has a short name",
            error.reason,
        )
    }

    @Test
    fun basicValidationErrorHasNoStackTrace() {
        val error = assertFailsWith<BasicValidationError> { Validator.email.validate("not-an-email") }
        assertTrue(error.stackTrace.isEmpty())
        assertEquals(error.reason, error.message)
    }

    @Test
    fun validateErrorsHasNoStackTraceAndStillFlattens() {
        val user = User(name = "Bob", age = 12, pet = Pet(name = "Rex", age = 1), preferedColors = emptyList())
        val error = assertFailsWith<ValidateErrors> { user.validate() }
        assertTrue(error.stackTrace.isEmpty())
        assertEquals(
            listOf(listOf("name"), listOf("age"), listOf("pet", "name"), listOf("pet", "age"), listOf("preferedColors")),
            error.flatten().map { it.path },
        )
    }

    @Test
    fun customValidationErrorHasNoStackTrace() {
        val error = assertFailsWith<CustomValidationError> { throw CustomValidationError() }
        assertTrue(error.stackTrace.isEmpty())
    }

    @Test
    fun validationErrorIgnoresSuppressedExceptions() {
        val error = BasicValidationError("is invalid")
        error.addSuppressed(IllegalStateException("suppressed"))
        assertTrue(error.suppressed.isEmpty())
    }
}

class CustomValidationError(
    path: List<String> = emptyList(),
) : ValidationError(path) {
    override val reason: String get() = "custom failure"

    override fun prefixed(path: List<String>): CustomValidationError = CustomValidationError(path + this.path)
}

data class User(
    val id: Int? = null,
    val name: String,
    val age: Int,
    val email: String? = null,
    val pet: Pet,
    val luckyNumber: Int? = null,
    val profilePictureURL: String? = null,
    val preferedColors: List<String> = emptyList(),
) : Validatable<User> {
    override fun validations() =
        validations<User> {
            add(User::name, Validator.length(min = 5) and Validator.alphanumeric)
            add(User::age, Validator.range(min = 18))
            add(User::email, Validator.nil or Validator.email)
            add(User::email, Validator.email or Validator.nil)
            add(User::luckyNumber, Validator.nil or Validator.oneOf(5, 7))
            add(User::profilePictureURL, Validator.url or Validator.nil)
            add(User::pet, Validator.valid())
            add(User::preferedColors, !Validator.empty)
        }
}

data class Pet(
    val name: String,
    val age: Int,
) : Validatable<Pet> {
    override fun validations() =
        validations<Pet> {
            add(Pet::name, Validator.length(min = 5) and Validator.characterSet(CharacterSet.alphanumerics + CharacterSet.whitespaces))
            add(Pet::age, Validator.range(min = 3))
        }
}
