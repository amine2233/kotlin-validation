package io.github.amine2233.validation

import java.net.URI
import java.net.URISyntaxException

/**
 * Validates that the data is a valid URL: either a `file:` URL, or one with at least a scheme and a host.
 *
 *     validations.add(User::profilePictureURL, Validator.url)
 *     validations.add(User::profilePictureURL, Validator.url or Validator.nil)
 */
public val Validator.Companion.url: Validator<String?> get() = urlValidator

private val urlValidator: Validator<String?> = Validator<String>("a valid URL") { data ->
        val uri = try {
            URI(data)
        } catch (_: URISyntaxException) {
            throw BasicValidationError("is not a valid URL")
        }
        // rawAuthority covers hosts java.net.URI refuses to parse (underscores, IDN) but Foundation accepts.
        val valid = uri.scheme.equals("file", ignoreCase = true) || (uri.scheme != null && uri.rawAuthority != null)
        if (!valid) throw BasicValidationError("is not a valid URL")
    }.ignoringNull()
