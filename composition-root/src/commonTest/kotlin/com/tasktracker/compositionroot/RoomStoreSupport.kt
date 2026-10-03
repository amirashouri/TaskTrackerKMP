package com.tasktracker.compositionroot

/**
 * Whether this host can open a Room store at all.
 *
 * True on the JVM and the iOS simulator, false on the Android host. The `Context` problem that
 * used to block every store-opening assertion here is **fixed** — `testPlatformHost()` now
 * returns a real one under a Robolectric sandbox, and four of the five hosted scenarios pass on
 * that target because of it. What remains is a second, unrelated limit: opening a store on the
 * Android host fails in `inTransaction` with
 *
 *     no sqliteJni in java.library.path: …/composition-root/src/androidHostTest/jniLibs
 *
 * `sqlite-bundled`'s native library is not on this target's classpath, and that is the same
 * limitation `:packages:database` documents when it disables `testAndroidHostTest` outright:
 * *every* test in that module opens a store, so the whole task is off there. It is not a wiring
 * fault and not something a test can substitute around — a store either opens or it does not.
 *
 * So the one scenario that genuinely needs a store asks this first, exactly as a Compose pixel
 * test asks `thisHostRasterisesText()` before rendering. The alternative — deleting the
 * assertion from the Android host — would lose it on the two targets where it *does* run, and
 * the alternative to that is leaving the target red for a reason no fix in this module can
 * address.
 *
 * Be aware of what the skip looks like in a report: `kotlin.test` has no portable skip, so this
 * registers as a **pass** on the Android host rather than as a skip. A reader of
 * `testAndroidHostTest` results alone would conclude the assertion ran. It did not; the JVM and
 * iOS copies did.
 */
internal expect fun thisHostOpensRoomStores(): Boolean
