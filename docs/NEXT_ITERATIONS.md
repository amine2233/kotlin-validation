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
3. **Message without path on every leaf.** `flatten()` gives one error per failing rule with its
   full `path`, but only `BasicValidationError` exposes the path-less `detail`; an `or` failure only
   has a combined `reason`. Adding `detail` to the base class would let APIs answer
   `{"path":"pet.name","message":"is less than ..."}` without string surgery.
4. **Ktor `RequestValidation` integration.** A one-line `validate<T : Validatable<T>> { it.validate() }`
   helper for Ktor's built-in `RequestValidation` plugin, shipped as a tiny `validation-ktor` module
   so the core stays dependency-free.
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
10. **Freeze `Validations` after build.** `add` stays callable after `validations { }` returns; a
    concurrent `add`/`run` on a shared instance (e.g. one held in a companion object) races. Copying
    the storage to an immutable list on first `run` — or a builder that returns an immutable
    `Validations` — closes that.

## Build & tooling

11. **Publish to Maven Central** (Sonatype Central portal, signing, `-javadoc` jar via Dokka) and add
   `ktlint`/`detekt` to `mise run check`.
12. **CI.** GitHub Actions job running `mise run check` on Linux (JVM modules) and one with the
    Android SDK for `assembleDebug`. `settings.gradle.kts` already skips the Android sample when
    `ANDROID_HOME` is missing.
13. **Binary compatibility validator** (`org.jetbrains.kotlinx.binary-compatibility-validator`) once
    the public API is frozen — `explicitApi()` is already on.

## Samples

14. **Android instrumentation/UI test** for the Compose form (`createComposeRule` or Robolectric) —
    the review caught a `rememberSaveable` crash that no unit test composes the screen to catch.
15. **Ktor `RequestValidation` sample** once item 4 exists, and a malformed-JSON test case.
