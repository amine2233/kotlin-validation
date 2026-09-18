package io.github.amine2233.validation

/**
 * A set of Unicode code points, described by [traits] for error messages. Union two sets with `+`.
 *
 *     Validator.characterSet(CharacterSet.alphanumerics + CharacterSet.whitespaces)
 *     Validator.characterSet(CharacterSet.alphanumerics + CharacterSet.of("_-"))
 */
public class CharacterSet(
    public val traits: List<String>,
    private val predicate: (Int) -> Boolean,
) {
    public constructor(trait: String, predicate: (Int) -> Boolean) : this(listOf(trait), predicate)

    /** Whether [codePoint] belongs to this set. */
    public operator fun contains(codePoint: Int): Boolean = predicate(codePoint)

    /** The union of both sets. */
    public operator fun plus(other: CharacterSet): CharacterSet =
        CharacterSet((traits + other.traits).distinct()) { it in this || it in other }

    public companion object {
        /** Bytes `0..<128`. */
        public val ascii: CharacterSet = CharacterSet("ASCII") { it < 128 }

        /** Letters, marks and numbers (Unicode categories L*, M*, N*), like Foundation's `alphanumerics`. */
        public val alphanumerics: CharacterSet = CharacterSet(listOf("A-Z", "a-z", "0-9")) { Character.getType(it) in alphanumericTypes }

        /** Letters in any script. */
        public val letters: CharacterSet = CharacterSet(listOf("A-Z", "a-z"), Character::isLetter)

        private val alphanumericTypes =
            setOf(
                Character.UPPERCASE_LETTER,
                Character.LOWERCASE_LETTER,
                Character.TITLECASE_LETTER,
                Character.MODIFIER_LETTER,
                Character.OTHER_LETTER,
                Character.NON_SPACING_MARK,
                Character.ENCLOSING_MARK,
                Character.COMBINING_SPACING_MARK,
                Character.DECIMAL_DIGIT_NUMBER,
                Character.LETTER_NUMBER,
                Character.OTHER_NUMBER,
            ).map(Byte::toInt).toSet()

        /** Decimal digits. */
        public val decimalDigits: CharacterSet = CharacterSet("0-9", Character::isDigit)

        /** Space, tab and other Unicode space separators, without newlines. */
        public val whitespaces: CharacterSet =
            CharacterSet("whitespace") {
                it == '\t'.code || Character.getType(it) == Character.SPACE_SEPARATOR.toInt()
            }

        /** `\n`, `\r`, and the other Unicode line and paragraph separators. */
        public val newlines: CharacterSet =
            CharacterSet("newlines") {
                it in 0x0A..0x0D || it == 0x85 || it == 0x2028 || it == 0x2029
            }

        /** Exactly the characters of [chars]. */
        public fun of(chars: String): CharacterSet {
            val codePoints = chars.codePoints().toArray().toSet()
            return CharacterSet(chars) { it in codePoints }
        }
    }
}

/**
 * Validates that every character of the data is ASCII.
 *
 *     validations.add(User::name, Validator.ascii)
 */
public val Validator.Companion.ascii: Validator<String?> get() = asciiValidator

/**
 * Validates that every character of the data is a letter or a digit.
 *
 *     validations.add(User::name, Validator.alphanumeric)
 */
public val Validator.Companion.alphanumeric: Validator<String?> get() = alphanumericValidator

/**
 * Validates that every character of the data is in [characterSet].
 *
 *     validations.add(User::name, Validator.characterSet(CharacterSet.alphanumerics + CharacterSet.whitespaces))
 */
public fun Validator.Companion.characterSet(characterSet: CharacterSet): Validator<String?> {
    val allowed = characterSet.traits.joinToString(", ").ifEmpty { null }
    return Validator<String>("in character set" + allowed?.let { " ($it)" }.orEmpty()) { data ->
        val invalid = data.codePoints().filter { it !in characterSet }.findFirst()
        if (invalid.isPresent) {
            val character = String(Character.toChars(invalid.asInt))
            throw BasicValidationError("contains an invalid character: '$character'" + allowed?.let { " (allowed: $it)" }.orEmpty())
        }
    }.ignoringNull()
}

private val asciiValidator = Validator.characterSet(CharacterSet.ascii)
private val alphanumericValidator = Validator.characterSet(CharacterSet.alphanumerics)
