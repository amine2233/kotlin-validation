# kotlin-validation

A lightweight, declarative validation library for Kotlin models — a port of
[amine2233/Validation](https://github.com/amine2233/Validation) (Swift, itself derived from
Vapor's Validation). Plain JVM, no dependencies: works in a CLI, in a Ktor server and in an
Android app.

Describe how a model's properties should be validated with composable validators, then call
`validate()`. **All** failures are collected in one pass and reported with dotted key paths.

## Requirements

- The library targets JVM 11 / Kotlin API 2.2 (Android `minSdk 26`); the samples use JDK 17
- Build: Gradle 9.7.1 + Kotlin 2.4.20, pinned through `mise.toml` and the Gradle wrapper

```bash
mise install        # JDK + Gradle
mise run test       # every module's unit tests
mise run cli        # run the CLI sample
mise run ktor       # run the Ktor sample on :8080
mise run android    # assemble the Android sample APK
mise run publish_local   # publish io.github.amine2233:validation:0.1.0 to ~/.m2
```

## Installation

Until it is on Maven Central, publish locally (`mise run publish_local`) and depend on it:

```kotlin
repositories { mavenLocal(); mavenCentral() }
dependencies { implementation("io.github.amine2233:validation:0.1.0") }
```

Inside this repository, samples use `implementation(project(":validation"))`.

## Usage

Implement `Validatable<Self>`, declare the validations, call `validate()`:

```kotlin
import io.github.amine2233.validation.*

data class User(
    val name: String,
    val age: Int,
    val email: String? = null,
    val luckyNumber: Int? = null,
    val profilePictureURL: String? = null,
    val preferedColors: List<String> = emptyList(),
    val pet: Pet? = null,
) : Validatable<User> {
    override fun validations() = validations<User> {
        add(User::name, Validator.count(min = 5) and Validator.alphanumeric)  // 5+ chars AND alphanumeric
        add(User::age, Validator.range(min = 18))                              // 18 or older
        add(User::email, Validator.nil or Validator.email)                     // null, or a valid email
        add(User::luckyNumber, Validator.nil or Validator.oneOf(5, 7))         // null, or 5 / 7
        add(User::profilePictureURL, Validator.url or Validator.nil)           // null, or a valid URL
        add(User::preferedColors, !Validator.empty)                            // not empty
        add(User::pet, Validator.valid())                                      // run Pet's own validations
    }
}

try {
    user.validate()
} catch (error: ValidateErrors) {
    println(error.reason)   // "name is less than required minimum of 5 characters, age is less than 18, pet.name ..."
    error.flatten()         // one ValidationError per failing rule, each with its full `path`
}
```

Validators are plain values and can be used on their own:

```kotlin
Validator.email.validate("user@example.com")
Validator.range(-5..5).validate(4)
(Validator.count(min = 8) and Validator.alphanumeric).validate("hunter2hunter2")
```

### Swift → Kotlin cheat sheet

| Swift | Kotlin |
|-------|--------|
| `struct User: Validatable, Reflectable` | `data class User(...) : Validatable<User>` |
| `static func validations() throws -> Validations<User>` | `override fun validations() = validations<User> { ... }` |
| `try validations.add(\.name, .count(5...))` | `add(User::name, Validator.count(min = 5))` |
| `try validations.add(\.name, at: ["first_name"], .ascii)` | `add(User::name, Validator.ascii, at = listOf("first_name"))` |
| `try validations.add(\.name, "is vapor") { ... }` | `add(User::name, "is vapor") { ... }` |
| `.count(5...)` / `.count(...10)` / `.count(5..<10)` | `count(min = 5)` / `count(max = 10)` / `count(5..<10)` |
| `.range(18...)` / `.range(-5...5)` | `range(min = 18)` / `range(-5..5)` |
| `.in(5, 7)` | `oneOf(5, 7)` |
| `.nil` | `Validator.nil` |
| `a && b`, `a \|\| b`, `!a` | `a and b`, `a or b`, `!a` |
| `.characterSet(.alphanumerics + .whitespaces)` | `characterSet(CharacterSet.alphanumerics + CharacterSet.whitespaces)` |
| `catch let error as any ValidationError` | `catch (error: ValidationError)` |
| Key paths reflected via `Reflectable`/`Codable` | Property name read from `KProperty1.name` — no reflection library |

Two things to know that differ from Swift:

- **Null handling.** `Validator` is contravariant and the built-ins skip `null`, so `Validator.email`
  is a `Validator<String?>` that passes for `null` (and so does `!Validator.email`). Say
  `!Validator.nil and Validator.email` when a value is required — Swift's type system forced
  `!.nil && .email`; Kotlin's does not. Lift your own non-null validators the same way with
  `myValidator.ignoringNull()` (Swift's `NilIgnoringValidator`).
- **Precedence.** `and`/`or` are infix functions with equal, left-to-right precedence — use
  parentheses where Swift relied on `&&` binding tighter than `||`.

### Nested models

`Validator.valid()` runs the nested model's own validations and prefixes every failure with the
property name, giving `pet.name is less than required minimum of 5 characters`. Any custom
closure that throws a `ValidationError` is prefixed the same way.

### Custom validators

Throw `BasicValidationError` (or your own `ValidationError` subclass) so paths compose:

```kotlin
object IsVapor : ValidatorType<String> {
    override val validatorReadable = "vapor"
    override fun validate(data: String) {
        if (data != "vapor") throw BasicValidationError("is not vapor")
    }
}
add(User::name, IsVapor.validator())

// or inline
add(User::name, Validator("vapor") { if (it != "vapor") throw BasicValidationError("is not vapor") })

// or on the whole model
add("passwords match") { if (it.password != it.confirmation) throw BasicValidationError("has mismatching passwords") }
```

## Built-in validators

| Validator | Applies to | Description |
|-----------|------------|-------------|
| `Validator.email` | `String?` | Valid email address |
| `Validator.url` | `String?` | Valid URL (`file:` or scheme + host) |
| `Validator.ascii` | `String?` | ASCII characters only |
| `Validator.alphanumeric` | `String?` | Letters and digits only |
| `Validator.characterSet(set)` | `String?` | Characters within the given `CharacterSet` |
| `Validator.count(range)` / `count(min=, max=)` | text, `Collection`, `Map`, `Array` | User-perceived character count (`"🤠"` is 1) or element count within bounds |
| `Validator.range(range)` / `range(min=, max=)` | `Comparable` | Value within bounds |
| `Validator.empty` | text, `Collection`, `Map`, `Array` | Value is empty (`null` counts as empty) |
| `Validator.oneOf(values)` | any | Value is one of the supplied values |
| `Validator.nil` | any | Value is `null` |
| `Validator.valid()` | `Validatable` | Nested model passes its own validations |

Combinators: `a and b` (both run, both failures reported), `a or b`, `!a`, `a.ignoringNull()`.
`CharacterSet.alphanumerics + CharacterSet.of("_-")` unions sets; `alphanumerics` follows Foundation
(Unicode letters, marks and numbers).

## Forms: field by field, and all at once

The same `Validator` values drive a live Compose form and a server-side payload check — only the
reporting strategy differs. `samples/android` shows the full pattern (`FieldRule`, `FieldValidation`,
`evaluate`), condensed here:

```kotlin
class FieldRule<in T>(val validator: Validator<T>, val message: String)

fun <T> evaluate(value: T, vararg rules: FieldRule<T>): String? =
    rules.firstOrNull { runCatching { it.validator.validate(value) }.isFailure }?.message

// Front end: first failure per field, one message per text field
evaluate(password, FieldRule(Validator.count(min = 8), "At least 8 characters."), FieldRule(Validator.alphanumeric, "Letters and digits only."))

// Back end: every failure in one shot
request.validate() // throws ValidateErrors listing every field
```

## Ktor

`samples/ktor` maps every `ValidationError` to a `400` with `StatusPages`, using `flatten()` for a
per-field list next to the combined reason:

```kotlin
install(StatusPages) {
    exception<ValidationError> { call, error ->
        call.respond(
            HttpStatusCode.BadRequest,
            ErrorResponse(error.reason, error.flatten().map { FieldError(it.path.joinToString("."), it.reason) }),
        )
    }
}
routing {
    post("/signup") {
        val request = call.receive<SignUpRequest>()
        request.validate()
        call.respond(HttpStatusCode.Created, request)
    }
}
```

```bash
mise run ktor
curl -X POST localhost:8080/signup -H 'content-type: application/json' -d '{"email":"nope","password":"short!","age":12}'
# {"error":"email is not a valid email address, password is less than ..., age is less than 18",
#  "errors":[{"path":"email","reason":"email is not a valid email address"}, {"path":"password","reason":"..."}, ...]}
```

## Android

The library is a plain JVM jar (Kotlin stdlib + `java.net.URI` only), so it is consumed like any
other dependency; property names come from compiled `KProperty1` references, so no `kotlin-reflect`
and no ProGuard rules are needed. `samples/android` is a Compose sign-up form with per-field
messages and a submit that runs the whole-model validation.

## Project layout

```
validation/          the library (io.github.amine2233.validation)
samples/cli/         plain main(): validates two users, prints reasons
samples/ktor/        POST /signup with StatusPages error mapping + testApplication tests
samples/android/     Compose form using shared rules (included only when ANDROID_HOME points to an SDK)
docs/NEXT_ITERATIONS.md   known gaps and candidate improvements
```

## License

MIT, like the original.
