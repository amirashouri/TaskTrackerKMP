package com.tasktracker.domain

import com.tasktracker.core.UserID
import kotlin.text.Regex
import kotlin.text.matches

/**
 * Mention parsing rules.
 *
 * A mention is either:
 * - `@username` — a display name mention, resolved to a user by the directory
 * - `@<userID>` — a stable UserID mention, unambiguous and directory-independent
 *
 * Both forms are recognised so the editor can highlight them and the sync sink can
 * agree on what constitutes a mention. The rule lives in `:packages:domain` so the
 * parsing logic is shared by every caller (editor, pull-side processor, tests)
 * without depending on presentation or data modules.
 *
 * The regex deliberately avoids greedy matching across whitespace:
 * - `@alice` matches `alice`
 * - `@alice bob` matches `alice` (stops at space)
 * - `@<user-123>` matches `user-123` as a UserID
 * - `@<user-123> more` matches `user-123` (stops at `>`)
 *
 * A mention at the end of a string or followed by punctuation is valid:
 * `@alice!` matches `alice`, `@<user-123>.` matches `user-123`.
 */
object MentionRules {

    /** A parsed mention: either a username (display name) or a stable UserID. */
    sealed interface Mention {
        /** The raw matched text including the `@` prefix (e.g. `@alice`, `@<user-123>`). */
        val raw: String

        /** Start index of the mention in the source text (0-based). */
        val start: Int

        /** End index (exclusive) of the mention in the source text. */
        val end: Int

        /** A mention by display name — resolved to a UserID by the directory at render time. */
        data class ByUsername(
            override val raw: String,
            override val start: Int,
            override val end: Int,
            /** The username without the `@` prefix. */
            val username: String,
        ) : Mention

        /** A mention by stable UserID — unambiguous, no directory lookup needed. */
        data class ByUserID(
            override val raw: String,
            override val start: Int,
            override val end: Int,
            /** The stable user identifier. */
            val userID: UserID,
        ) : Mention
    }

    // Username: @ followed by alphanumerics, dots, hyphens, underscores
    // UserID: @< followed by UUID (36 chars with dashes) followed by >
    // Negative lookbehind (?<!\S) ensures @ is at string start or preceded by whitespace
    private const val USERNAME_PATTERN = """(?<!\S)@([a-zA-Z0-9_.-]+)"""
    private const val USER_ID_PATTERN = """(?<!\S)@<([0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12})>"""

    private val mentionRegex = Regex("(?:$USER_ID_PATTERN|$USERNAME_PATTERN)")

    /**
     * Extracts all mentions from [text].
     *
     * Returns mentions in the order they appear, each with its position in the
     * original string so callers can highlight or linkify them.
     */
    fun extractMentions(text: String): List<Mention> {
        val mentions = mutableListOf<Mention>()
        mentionRegex.findAll(text).forEach { match ->
            val raw = match.value
            val start = match.range.first
            val end = match.range.last + 1
            // Group 1 is the UserID pattern, group 2 is the username pattern.
            // groupValues returns "" for non-matching groups, not null.
            val userIdGroup = match.groupValues?.getOrNull(1)
            val usernameGroup = match.groupValues?.getOrNull(2)
            if (userIdGroup != null && userIdGroup.isNotEmpty()) {
                mentions.add(Mention.ByUserID(raw, start, end, UserID(userIdGroup)))
            } else if (usernameGroup != null && usernameGroup.isNotEmpty()) {
                mentions.add(Mention.ByUsername(raw, start, end, usernameGroup))
            }
        }
        return mentions
    }

    /**
     * Extracts only the UserID mentions from [text].
     *
     * Convenience for callers that only care about unambiguous UserID references.
     */
    fun extractUserIDMentions(text: String): List<Mention.ByUserID> =
        extractMentions(text).filterIsInstance<Mention.ByUserID>()

    /**
     * Extracts only the username mentions from [text].
     *
     * Convenience for callers that want to resolve display names through the directory.
     */
    fun extractUsernameMentions(text: String): List<Mention.ByUsername> =
        extractMentions(text).filterIsInstance<Mention.ByUsername>()
}