package com.tasktracker.featuresettingsdomain

/**
 * What the Server field has to say: the address the app is talking to, and the address it will
 * talk to on the next launch.
 *
 * ## Why these are two values and not one
 *
 * A save publishes the confirmed address to the clients that are already running, so [inUse]
 * follows it immediately; [savedOverride] is the preference the *next* process resolves, and it
 * is what Reset clears. They can differ before a save in this run — an override written by a
 * previous launch — and collapse into one value after it, so the row must read [inUse] and the
 * sheet must edit [savedOverride].
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
