package com.tasktracker.featuresettingsdomain

/**
 * What the Server field has to say: the address the app is talking to, and the address it will
 * talk to on the next launch.
 *
 * ## Why these are two values and not one
 *
 * The API root is resolved once, while the composition root binds the app-scoped HTTP clients,
 * so a change saved from Settings cannot reconfigure the clients already running. [inUse] is
 * therefore the honest thing to display — the address actually on the wire — and [savedOverride]
 * is what the *next* process will read. Collapsing them into a single "current" value would make
 * the row claim the change is live when it is not.
 *
 * [inUse] is nullable because the offline demo graph has no backend at all: `testBindings` builds
 * it with no root, and the row has to say so rather than show a URL nothing is listening on.
 *
 * A blank stored value reads as no override, so [isCustom] is false for a key written empty.
 */
data class ServerAddressSettings(
    /** The API root this process bound its clients to, or `null` when there is no backend. */
    val inUse: String?,
    /** The address saved for the next launch, or `null` when the build-time default applies. */
    val savedOverride: String?,
) {
    /** Whether a saved override exists — and therefore whether Reset has anything to do. */
    val isCustom: Boolean get() = savedOverride != null
}
