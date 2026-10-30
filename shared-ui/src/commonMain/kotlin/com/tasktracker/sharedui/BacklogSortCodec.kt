package com.tasktracker.sharedui

import com.tasktracker.domain.BacklogSortBasis
import com.tasktracker.domain.BacklogSortKey
import com.tasktracker.domain.BacklogSortOrder

/**
 * The stored form of the backlog's ordering: one `basis:direction` token per key, leading key
 * first.
 *
 * ## Why tokens rather than the domain type
 *
 * [BacklogSortOrder] is a domain rule, and the domain module has no serialization dependency
 * (its tests run with nothing standing in, which is the point). Annotating it `@Serializable`
 * to make it storable would put a wire concern on a type whose whole job is a comparator —
 * and it would freeze the enum names into a plist, so renaming a basis would silently turn
 * everyone's saved order into the default.
 *
 * So the preference is stored as `["priority:descending", "createdAt:ascending"]` — a
 * `List<String>` that the store JSON-encodes like any other value — and this file is the only
 * place that knows the vocabulary. The tokens are spelled out here rather than taken from
 * `BacklogSortBasis.name` for the same reason [com.tasktracker.userdefaults.DefaultsKey]
 * spells its keys out: a stored string that a rename can change is a bug with no error
 * message.
 *
 * ## Decoding never fails
 *
 * A stored preference is read on the first frame of a launch, so a throw there is a crash on
 * every subsequent start until the plist is cleared. Everything unrecognised is therefore
 * dropped rather than rejected: an unknown basis, a duplicated basis, a missing direction.
 * What is left is either a valid selection or, if nothing survives, the default — and the
 * default is the same value a device that never chose anything reads.
 */
internal object BacklogSortCodec {

    private const val SEPARATOR = ':'
    private const val DESCENDING = "descending"
    private const val ASCENDING = "ascending"

    /** The tokens for [order], leading key first. */
    fun encode(order: BacklogSortOrder): List<String> = order.keys.map { key ->
        "${token(key.basis)}$SEPARATOR${if (key.descending) DESCENDING else ASCENDING}"
    }

    /**
     * The selection [tokens] describe, or the default when they describe nothing usable.
     *
     * `null` is the "key holds nothing" case [UserDefaultsStore.value] reports for an absent
     * entry, and it decodes to the same default as a corrupt one: from the screen's side both
     * mean "this device has not chosen an order".
     */
    fun decode(tokens: List<String>?): BacklogSortOrder {
        val keys = tokens.orEmpty()
            .mapNotNull(::key)
            // First occurrence wins: a stored pair that names the same basis twice would
            // otherwise violate `BacklogSortOrder`'s `require` and throw on launch.
            .distinctBy { it.basis }
        return if (keys.isEmpty()) BacklogSortOrder() else BacklogSortOrder(keys)
    }

    private fun key(token: String): BacklogSortKey? {
        val basis = basis(token.substringBefore(SEPARATOR)) ?: return null
        val direction = token.substringAfter(SEPARATOR, missingDelimiterValue = "")
        // An unrecognised or absent direction reads as the basis's own default rather than
        // dropping the key: the basis is the decision the user made, the direction is the
        // half that a future build might spell differently.
        return BacklogSortKey(basis = basis, descending = direction != ASCENDING)
    }

    private fun basis(token: String): BacklogSortBasis? = when (token.trim().lowercase()) {
        "priority" -> BacklogSortBasis.Priority
        "createdat" -> BacklogSortBasis.CreatedAt
        else -> null
    }

    private fun token(basis: BacklogSortBasis): String = when (basis) {
        BacklogSortBasis.Priority -> "priority"
        BacklogSortBasis.CreatedAt -> "createdAt"
    }
}
