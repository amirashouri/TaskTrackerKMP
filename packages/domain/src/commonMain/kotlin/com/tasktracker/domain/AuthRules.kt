package com.tasktracker.domain

import com.tasktracker.core.AppError
import com.tasktracker.core.graphemeClusterCount

/**
 * Input rules for the account, in one place so the sign-in screen, the use cases and the
 * tests all reject the same things for the same reasons.
 *
 * Every rule returns the *normalised* value it accepted, following [ProjectRules].
 * Normalising here rather than at each call site is what stops `"  Ada@Example.com  "`
 * becoming two different accounts, or a password silently losing its trailing space.
 */
object AuthRules {

    /** 254 is the longest address SMTP will accept, so anything longer is a typo. */
    const val MAX_EMAIL_LENGTH = 254

    /**
     * Generous upper bound, not a policy: the point is to refuse an accidental paste of
     * a document, and the server is what actually decides what a password may be.
     */
    const val MAX_PASSWORD_LENGTH = 256

    const val MAX_DISPLAY_NAME_LENGTH = 120

    /**
     * Lowercased and trimmed, so an address is a single identity regardless of how it
     * was typed or which device typed it. Case-sensitive email comparison is a reliable
     * source of duplicate accounts.
     */
    @Throws(AppError::class)
    fun normaliseEmail(raw: String): String {
        val trimmed = raw.trim().lowercase()
        if (trimmed.isEmpty()) {
            throw AppError.Validation("Enter your email address.")
        }
        if (graphemeClusterCount(trimmed) > MAX_EMAIL_LENGTH) {
            throw AppError.Validation("That email address is too long.")
        }
        // Exactly one `@`, something on both sides, and a dot in the domain.
        // Deliberately loose: the server owns validation, and a stricter rule here would
        // reject addresses that are legal (`a@b.c`) or catch typos the user is mid-way
        // through fixing.
        //
        // `split('@')` rather than `split("@")` because the latter would take the
        // whole address as the delimiter. Kotlin's split keeps empty segments, so
        // `"a@@b.c"` correctly yields three parts and is rejected on the count.
        val halves = trimmed.split('@')
        if (halves.size != 2 || halves[0].isEmpty() || halves[1].isEmpty()) {
            throw AppError.Validation("That does not look like an email address.")
        }
        val domain = halves[1]
        if (!domain.contains('.') || domain.startsWith('.') || domain.endsWith('.')) {
            throw AppError.Validation("That does not look like an email address.")
        }
        return trimmed
    }

    /**
     * **Not** trimmed, and no complexity rules.
     *
     * Whitespace is significant in a password, so stripping it would lock out anyone
     * whose password happens to have a trailing space. And a length or composition rule
     * here would be a registration policy leaking into sign-in, where the correct answer
     * to "too short" is still to ask the server — telling a returning user their
     * password is too short is both wrong and a small information leak about the
     * account.
     */
    @Throws(AppError::class)
    fun normalisePassword(raw: String): String {
        if (raw.isEmpty()) {
            throw AppError.Validation("Enter your password.")
        }
        if (graphemeClusterCount(raw) > MAX_PASSWORD_LENGTH) {
            throw AppError.Validation("That password is too long.")
        }
        return raw
    }

    /**
     * A blank display name is stored as `null` rather than `""`, so "no name" has exactly
     * one representation.
     */
    @Throws(AppError::class)
    fun normaliseDisplayName(raw: String?): String? {
        if (raw == null) return null
        val trimmed = raw.trim()
        if (graphemeClusterCount(trimmed) > MAX_DISPLAY_NAME_LENGTH) {
            throw AppError.Validation("Names are limited to $MAX_DISPLAY_NAME_LENGTH characters.")
        }
        return trimmed.ifEmpty { null }
    }
}
