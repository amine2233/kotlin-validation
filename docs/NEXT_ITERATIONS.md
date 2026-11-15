# Next iterations

Known gaps of the 0.1.0 port and candidate improvements, roughly in priority order. Nothing here
blocks using the library; each item is a deliberate simplification of the first cut.

## Library

1. **Kotlin Multiplatform.** The only JVM-specific code is `java.net.URI` (URL validator) and
   `String.codePoints()` (character sets). Replacing them with a small hand-written URL parser and
   code-point iteration would let the same artifact target iOS/JS/Wasm — and unify the Swift and
   Kotlin codebases into one KMP module.
2. **Compile-time typing for `count` / `empty`.** They dispatch on the value's shape at runtime, so
   `add(User::age, Validator.count(1..3))` compiles and fails with `IllegalArgumentException`. Typed
   variants (`length` for `CharSequence?`, `count` for `Collection<*>?`) would move that to compile
   time at the cost of diverging from the Swift names.
3. **Done.** `ValidationError.detail` now gives the path-less message on every error.
   **Message without path on every leaf.** `flatten()` gives one error per failing rule with its
   full `path`, but only `BasicValidationError` exposes the path-less `detail`; an `or` failure only
   has a combined `reason`. Adding `detail` to the base class would let APIs answer
   `{"path":"pet.name","message":"is less than ..."}` without string surgery.
4. **Ktor `RequestValidation` integration.** Ktor's `RequestValidationConfig.validate { }` block is
   itself `suspend`, so a tiny `validation-ktor` module can expose one helper for `Validatable` and
   one for `AsyncValidatable` with no extra machinery, keeping the core dependency-free.
5. **Form helpers in the library.** `FieldRule` / `FieldValidation` / `evaluate` currently live in
   the Android sample (mirroring the Swift DocC article). Promoting them to a `validation-forms`
   module would remove copy-paste between Android and Compose Multiplatform apps.
6. **Localizable messages.** Messages are English strings built inside each validator. A
   `MessageProvider` hook (or returning message keys + arguments) is needed before the messages can
   be shown to end users on Android. The per-field `FieldRule.message` pattern sidesteps this today.
7. **Nested property paths.** Swift's `\.pet.name` has no Kotlin equivalent; nested validation goes
   through `Validator.valid()` or a custom closure. A small `path(User::pet, Pet::name)` helper
   would restore single-rule nested paths.
8. **`or` readable string.** Like the Swift original, `a or b` reads "X or is Y" and its error joins
   both failures with "and". Worth revisiting the wording together with item 6.
9. **Stack-trace-free errors.** `ValidationError` is control flow, yet every throw fills a stack
   trace; a Compose form validates on each keystroke. Switching to the
   `RuntimeException(message, cause, false, false)` constructor removes that cost at the price of
   losing traces for genuinely unexpected validation errors.
10. **Done: freeze `Validations` after build.** `validations { }` and `asyncValidations { }` now
    return a frozen instance (both collections for `AsyncValidations`); `add` after build throws
    `IllegalStateException`. Directly constructed instances are not frozen.
11. **Parallel async rules.** `AsyncValidations.run` is sequential on purpose (no dependency, and
    the sync phase usually short-circuits it). Several independent remote checks would be faster
    with `coroutineScope` + `awaitAll` — but that needs `kotlinx-coroutines-core` at runtime, so it
    belongs in a separate `validation-coroutines` module, the same containment used for item 4.
12. **Done: nested async models.** The async `valid()` factory (`AsyncValidValidator.kt`) runs a
    nested `AsyncValidatable`'s sync and async rules under the property path, and
    `AsyncValidatorType` + `validator()` mirror `ValidatorType` for async.

## Build & tooling

13. **Publish to Maven Central** (Sonatype Central portal, signing, `-javadoc` jar via Dokka) and add
   `ktlint`/`detekt` to `mise run check`.
14. **CI coverage of the Android sample.** The shared CI runs `mise run test` on `ubuntu-latest`,
    whose preinstalled SDK makes `settings.gradle.kts` include `:samples:android`; if that proves
    slow or flaky, gate the sample behind an explicit `-PwithAndroid` flag instead of `ANDROID_HOME`.
15. **Binary compatibility validator** (`org.jetbrains.kotlinx.binary-compatibility-validator`) once
    the public API is frozen — `explicitApi()` is already on.

## Samples

16. **Android instrumentation/UI test** for the Compose form (`createComposeRule` or Robolectric) —
    the review caught a `rememberSaveable` crash that no unit test composes the screen to catch.
17. **Ktor `RequestValidation` sample** once item 4 exists, and a malformed-JSON test case.
