# kotlin-validation

A lightweight, declarative validation library for Kotlin models — a port of
[amine2233/Validation](https://github.com/amine2233/Validation) (Swift, itself derived from
Vapor's Validation). Plain JVM, no dependencies: works in a CLI, in a Ktor server and in an
Android app. Rules that need a network call (`suspend`) are supported without pulling in a
coroutines runtime. An optional `validation-ktor` module plugs models into Ktor's
`RequestValidation`.

Describe how a model's properties should be validated with composable validators, then call
`validate()`. **All** failures are collected in one pass and reported with dotted key paths.

## Requirements

- The library targets JVM 11 / Kotlin API 2.2 (Android `minSdk 26`); the samples use JDK 17
- Build: Gradle 9.7.1 + Kotlin 2.4.20, pinned through `mise.toml` and the Gradle wrapper

```bash
mise install             # JDK, Node (release tooling), ktlint
mise run test            # every module's unit tests
mise run lint            # ktlint (mise run format fixes what it can)
mise run cli             # run the CLI sample
mise run ktor            # run the Ktor sample on :8080
mise run android         # assemble the Android sample APK
mise run publish_local   # publish io.github.amine2233:validation:0.1.0-SNAPSHOT to ~/.m2
```

## Installation

Releases are published to GitHub Packages:

```kotlin
repositories {
    maven {
        url = uri("https://maven.pkg.github.com/amine2233/kotlin-validation")
        credentials {
            username = providers.gradleProperty("gpr.user").orElse(System.getenv("GITHUB_ACTOR")).get()
            password = providers.gradleProperty("gpr.key").orElse(System.getenv("GITHUB_TOKEN")).get()
        }
    }
}
dependencies { implementation("io.github.amine2233:validation:<version>") }
```

For Ktor's `RequestValidation` plugin, add `implementation("io.github.amine2233:validation-ktor:<version>")`
instead; it brings `validation` and `ktor-server-request-validation` with it (see [Ktor `RequestValidation`](#ktor-requestvalidation)).

For local work, `mise run publish_local` puts a `0.1.0-SNAPSHOT` in `~/.m2` (`repositories { mavenLocal() }`).

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
        add(User::name, Validator.length(min = 5) and Validator.alphanumeric) // 5+ chars AND alphanumeric
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
    error.flatten().map { it.path to it.detail }  // [name] to "is less than required minimum of 5 characters", ...
}
```

Validation errors carry no stack trace and accept no suppressed exceptions, because they are thrown as
control flow (for example on every keystroke in a form). Use `path`, `reason`, `detail` (the
message without the key path) and `flatten()` to find a failure.

Validators are plain values and can be used on their own:

```kotlin
Validator.email.validate("user@example.com")
Validator.range(-5..5).validate(4)
(Validator.length(min = 8) and Validator.alphanumeric).validate("hunter2hunter2")
```

### Swift → Kotlin cheat sheet

| Swift | Kotlin |
|-------|--------|
| `struct User: Validatable, Reflectable` | `data class User(...) : Validatable<User>` |
| `static func validations() throws -> Validations<User>` | `override fun validations() = validations<User> { ... }` |
| `try validations.add(\.name, .count(5...))` | `add(User::name, Validator.length(min = 5))` |
| `try validations.add(\.name, at: ["first_name"], .ascii)` | `add(User::name, Validator.ascii, at = listOf("first_name"))` |
| `try validations.add(\.name, "is vapor") { ... }` | `add(User::name, "is vapor") { ... }` |
| `try validations.add(\.pet.name, .count(5...))` | `add(path(User::pet, Pet::name), Validator.length(min = 5))` |
| `.count(5...)` / `.count(...10)` / `.count(5..<10)` | `length(min = 5)` / `length(max = 10)` / `length(5..<10)` for text, `size(...)` for collections |
| `.range(18...)` / `.range(-5...5)` | `range(min = 18)` / `range(-5..5)` |
| `.in(5, 7)` | `oneOf(5, 7)` |
| `.nil` | `Validator.nil` |
| `a && b`, `a \|\| b`, `!a` | `a and b`, `a or b`, `!a` |
| `.characterSet(.alphanumerics + .whitespaces)` | `characterSet(CharacterSet.alphanumerics + CharacterSet.whitespaces)` |
| `catch let error as any ValidationError` | `catch (error: ValidationError)` |
| — (no equivalent in the Swift port) | `AsyncValidator` / `asyncValidations { }` / `suspend validate()` |
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

To check a single nested field with one rule, without running the nested model's own
validations, join the two properties with `path(...)`:

```kotlin
add(User::pet, Validator.valid())                             // all of Pet's own rules
add(path(User::pet, Pet::name), Validator.length(min = 5))     // just this one
```

Failures read `pet.name is less than required minimum of 5 characters` (path `["pet", "name"]`),
`at = listOf(...)` overrides the path as usual, and when `pet` is `null` the rule is skipped.

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

### Async validators (rules that call an API)

Some rules cannot be answered in memory — *"is this email already registered?"*. Those are
`AsyncValidator`s: the same thing with a `suspend` check. `suspend` is a language feature, so the
library still has no runtime dependency; `kotlinx-coroutines` only appears on the test classpath.

```kotlin
val emailIsFree =
    AsyncValidator<String>("not already registered") { email ->
        if (accountApi.isRegistered(email)) throw BasicValidationError("is already registered")
    }

data class SignUpRequest(val email: String, val age: Int) : AsyncValidatable<SignUpRequest> {
    override fun validations() =
        asyncValidations<SignUpRequest> {
            add(SignUpRequest::email, Validator.email)   // phase 1: local, synchronous
            add(SignUpRequest::age, Validator.range(min = 18))
            add(SignUpRequest::email, emailIsFree)       // phase 2: remote
        }
}

request.validate()   // suspends; same ValidateErrors, same paths, same flatten()
```

**Two phases, in this order.** Every synchronous rule runs first and all of their failures are
reported at once; the async rules run afterwards, one at a time, and **only if every sync rule
passed** — a malformed email never costs a request. The visible consequence: a failure message
never mixes the two phases.

**A failed request is not a failed validation.** `run` collects `ValidationError`s and nothing
else, so an `IOException` or a timeout propagates to your caller (in Ktor: a 500, not a 400), and a
`CancellationException` cancels as it should. If "could not check" should read as "invalid", decide
that inside your rule:

```kotlin
AsyncValidator<String>("not already registered") { email ->
    val registered = try { accountApi.isRegistered(email) } catch (_: IOException) {
        throw BasicValidationError("could not be verified")
    }
    if (registered) throw BasicValidationError("is already registered")
}
```

Apart from `AsyncValidator.valid()` below, there are no *built-in* async validators — an async rule
is by definition a call to your own API.
Put a built-in next to one with `Validator.email.async()`, and use `and` / `or` / `!` /
`ignoringNull()` exactly as in the sync world. A custom closure added to `asyncValidations { }`
runs in the async phase even when its body never suspends.

#### Nested async models and reusable async validators

`AsyncValidator.valid()` is the async counterpart of `Validator.valid()`: a nested
`AsyncValidatable` runs its own sync and async rules, and every failure is prefixed with the
property name. It is itself an async rule, so it runs in the parent's async phase, only after all
of the parent's sync rules have passed. `null` is skipped.

```kotlin
data class Order(val buyer: SignUpRequest?) : AsyncValidatable<Order> {
    override fun validations() = asyncValidations<Order> { add(Order::buyer, AsyncValidator.valid()) }
}

order.validate()   // "buyer.email is already registered"
```

Implement `AsyncValidatorType` to define a reusable async validator as an `object`, the same idiom
as `ValidatorType`:

```kotlin
object IsAvailable : AsyncValidatorType<String> {
    override val validatorReadable = "available"
    override suspend fun validate(data: String) {
        if (accountApi.isRegistered(data)) throw BasicValidationError("is already registered")
    }
}
add(SignUpRequest::email, IsAvailable.validator())
```

## Built-in validators

| Validator | Applies to | Description |
|-----------|------------|-------------|
| `Validator.email` | `String?` | Valid email address |
| `Validator.url` | `String?` | Valid URL (`file:` or scheme + host) |
| `Validator.ascii` | `String?` | ASCII characters only |
| `Validator.alphanumeric` | `String?` | Letters and digits only |
| `Validator.characterSet(set)` | `String?` | Characters within the given `CharacterSet` |
| `Validator.length(range)` / `length(min=, max=)` | `CharSequence?` | User-perceived character count (`"🤠"` is 1) within bounds |
| `Validator.size` / `mapSize` / `arraySize` (`range` or `min=, max=`) | `Collection?` / `Map?` / `Array?` | Element count within bounds |
| `Validator.range(range)` / `range(min=, max=)` | `Comparable` | Value within bounds |
| `Validator.empty` | text, `Collection`, `Map`, `Array` | Value is empty (`null` counts as empty) |
| `Validator.oneOf(values)` | any | Value is one of the supplied values |
| `Validator.nil` | any | Value is `null` |
| `Validator.valid()` | `Validatable` | Nested model passes its own validations |
| `AsyncValidator.valid()` | `AsyncValidatable` | Nested model passes its own sync and async validations |

`Validator.count(...)` is deprecated: it accepts any type and only fails at runtime on the wrong one. Use
`length`, `size`, `mapSize` or `arraySize`, which reject a wrong property type at compile time.

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
evaluate(password, FieldRule(Validator.length(min = 8), "At least 8 characters."), FieldRule(Validator.alphanumeric, "Letters and digits only."))

// Back end: every failure in one shot
request.validate() // throws ValidateErrors listing every field
```

## Ktor

`samples/ktor` validates `SignUpRequest` with Ktor's `RequestValidation` plugin through
`validation-ktor`, so the route only calls `call.receive`. `StatusPages` maps the resulting
`RequestValidationException` to a `400`, listing one reason per failing rule next to the combined
reason, and maps a malformed body (`BadRequestException`) to a `400` of the same shape.
`SignUpRequest` is an `AsyncValidatable`, so the "email already registered" rule runs in the
request's coroutine, and an `IOException` from that lookup is deliberately *not* a
`ValidationError`, so it surfaces as a 500 rather than telling the user their input is wrong:

```kotlin
install(RequestValidation) { asyncValidatable<SignUpRequest>() }
install(StatusPages) {
    exception<RequestValidationException> { call, cause ->
        call.respond(HttpStatusCode.BadRequest, ErrorResponse(cause.reasons))
    }
    exception<BadRequestException> { call, _ ->
        call.respond(HttpStatusCode.BadRequest, ErrorResponse(listOf("malformed request body")))
    }
}
routing {
    post("/signup") {
        val request = call.receive<SignUpRequest>()
        call.respond(HttpStatusCode.Created, request)
    }
}
```

```bash
mise run ktor
curl -X POST localhost:8080/signup -H 'content-type: application/json' -d '{"email":"nope","password":"short!","age":12}'
# {"error":"email is not a valid email address, password is less than ..., age is less than 18",
#  "errors":["email is not a valid email address","password is less than required minimum of 8 characters", ...]}

curl -X POST localhost:8080/signup -H 'content-type: application/json' \
  -d '{"email":"taken@vapor.codes","password":"hunter2hunter2","age":30}'
# {"error":"email is already registered","errors":["email is already registered"]}

curl -X POST localhost:8080/signup -H 'content-type: application/json' -d '{"email":'
# {"error":"malformed request body","errors":["malformed request body"]}
```

### Ktor `RequestValidation`

`io.github.amine2233:validation-ktor:<version>` registers models with Ktor's `RequestValidation`
plugin:

```kotlin
install(RequestValidation) {
    validatable<User>()                 // runs User's sync validations
    asyncValidatable<SignUpRequest>()   // runs SignUpRequest's sync, then async validations
}
```

`call.receive<T>()` then throws `RequestValidationException` when the model is invalid, with one
reason per failing rule from `flatten()`, in rule order and including the key path
(`"age is less than 18"`). Only a `ValidationError` becomes a `400`; any other exception (an
`IOException` from an async rule, say) propagates as usual. The module ships no response type and
no `StatusPages` wiring: map `RequestValidationException` yourself, as the sample does.

## Android

The library is a plain JVM jar (Kotlin stdlib + `java.net.URI` only), so it is consumed like any
other dependency; property names come from compiled `KProperty1` references, so no `kotlin-reflect`
and no ProGuard rules are needed. `samples/android` is a Compose sign-up form with per-field
messages and a submit that runs the whole-model validation.

## Project layout

```
validation/          the library (io.github.amine2233.validation)
validation-ktor/     Ktor RequestValidation helpers (io.github.amine2233.validation.ktor)
samples/cli/         plain main(): validates two users, prints reasons
samples/ktor/        POST /signup with RequestValidation + StatusPages error mapping + testApplication tests
samples/android/     Compose form using shared rules (included only when ANDROID_HOME points to an SDK)
docs/NEXT_ITERATIONS.md   known gaps and candidate improvements
```

## CI/CD

CI and releases come from [kotlin-ci-shared](https://github.com/amine2233/kotlin-ci-shared).
All logic lives in `mise.toml`:

- `mise run test` / `mise run lint` — what the `CI` workflow runs on pull requests.
- `mise run test` also checks the libraries' public API against `validation/api/validation.api`
  and `validation-ktor/api/validation-ktor.api` (binary-compatibility-validator). For an
  intentional API change, run `./gradlew apiDump` and commit the updated `.api` files.
- **Actions → Release → Run workflow** runs semantic-release (or automatically
  on every push to `main` when the repo variable `AUTO_RELEASE=true` is set): it tags
  `vX.Y.Z`, updates `CHANGELOG.md`, publishes `io.github.amine2233:validation` and
  `io.github.amine2233:validation-ktor` to GitHub Packages and creates the GitHub release
  with the jars attached.
- `mise run release --dry-run` previews the next version locally.

## License

MIT, like the original.
