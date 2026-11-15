package io.github.amine2233.validation

/**
 * A validation failure that knows the key [path] it happened at. Nested validations and combinators
 * prepend their own segment through [prefixed], producing dotted paths such as `pet.name` in [reason].
 *
 * Errors are immutable; see [BasicValidationError] for the default implementation to throw from custom
 * validators.
 *
 * Instances capture no stack trace and accept no suppressed exceptions, because they are used as control flow.
 */
public abstract class ValidationError(
    /** Key path the failure happened at, outermost segment first. Empty means the root value. */
    public val path: List<String> = emptyList(),
) : RuntimeException(null, null, false, false) {
    /** Human-readable description of the failure, including the key path. */
    public abstract val reason: String

    /**
     * Human-readable description of the failure without this error's own key path, meant to pair with [path]
     * (for example on entries of [flatten]). Defaults to [reason].
     */
    public open val detail: String get() = reason

    /** A copy of this error whose path starts with [path]. */
    public abstract fun prefixed(path: List<String>): ValidationError

    /**
     * The individual failures behind this error, each carrying its full path — one per failing rule for
     * [ValidateErrors] and `and`; a single entry otherwise. Use it to build per-field error responses.
     */
    public open fun flatten(): List<ValidationError> = listOf(this)

    override val message: String get() = reason
}

/** The default [ValidationError]: a [detail] phrased to follow the key path, e.g. `"is not empty"`. */
public class BasicValidationError(
    override val detail: String,
    path: List<String> = emptyList(),
) : ValidationError(path) {
    override val reason: String get() = "${path.joinToString(".").ifEmpty { "data" }} $detail"

    override fun prefixed(path: List<String>): BasicValidationError = BasicValidationError(detail, path + this.path)
}

/** Every failure collected by [Validations.run], joined with `", "` in [reason]. */
public class ValidateErrors(
    public val errors: List<ValidationError>,
    path: List<String> = emptyList(),
) : ValidationError(path) {
    override val reason: String get() = errors.joinToString(", ") { it.prefixed(path).reason }

    override val detail: String get() = errors.joinToString(", ") { it.relativeDetail() }

    override fun prefixed(path: List<String>): ValidateErrors = ValidateErrors(errors, path + this.path)

    override fun flatten(): List<ValidationError> = errors.flatMap { it.prefixed(path).flatten() }
}

/** The text of a child inside a composite's [ValidationError.detail]: keeps the child's relative path, if any. */
internal fun ValidationError.relativeDetail(): String = if (path.isEmpty()) detail else reason
