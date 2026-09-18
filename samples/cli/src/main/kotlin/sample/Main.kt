package sample

import io.github.amine2233.validation.*

data class Pet(
    val name: String,
    val age: Int,
) : Validatable<Pet> {
    override fun validations() =
        validations<Pet> {
            add(Pet::name, Validator.count(min = 2) and Validator.alphanumeric)
            add(Pet::age, Validator.range(min = 0))
        }
}

data class User(
    val name: String,
    val age: Int,
    val email: String? = null,
    val luckyNumber: Int? = null,
    val profilePictureURL: String? = null,
    val preferedColors: List<String> = emptyList(),
    val pet: Pet? = null,
) : Validatable<User> {
    override fun validations() = Companion.validations

    // ponytail: built once and shared; rebuild per call only if the rules depend on runtime state.
    companion object {
        val validations =
            validations<User> {
                add(User::name, Validator.count(min = 5) and Validator.alphanumeric)
                add(User::age, Validator.range(min = 18))
                add(User::email, Validator.nil or Validator.email)
                add(User::luckyNumber, Validator.nil or Validator.oneOf(5, 7))
                add(User::profilePictureURL, Validator.url or Validator.nil)
                add(User::preferedColors, !Validator.empty)
                add(User::pet, Validator.valid())
            }
    }
}

fun main() {
    val users =
        listOf(
            User(name = "Tanner", age = 23, email = "tanner@vapor.codes", luckyNumber = 7, preferedColors = listOf("blue")),
            User(name = "Bob", age = 12, email = "not-an-email", luckyNumber = 3, profilePictureURL = "bananas", pet = Pet("x", -1)),
        )
    for (user in users) {
        try {
            user.validate()
            println("✅ $user")
        } catch (error: ValidateErrors) {
            println("❌ ${user.name}: ${error.reason}")
            error.errors.forEach { println("   - ${it.reason}") }
        }
    }

    println("\nStandalone validators:")
    println("  ${Validator.email.readable}: ${runCatching { Validator.email.validate("user@example.com") }.isSuccess}")
    println("  ${Validator.range(1..10).readable}: ${runCatching { Validator.range(1..10).validate(42) }.exceptionOrNull()?.message}")
}
