package com.tasktracker.sharedui

import com.tasktracker.featureauthdomain.UserIdentity
import com.tasktracker.featureauthdomain.UserRole
import com.tasktracker.routing.Router
import com.tasktracker.userdefaults.DefaultsKey
import com.tasktracker.userdefaults.InMemoryPreferences
import com.tasktracker.userdefaults.UserDefaultsStore
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlinx.coroutines.test.runTest

/**
 * The shell's read of the signed-in profile.
 *
 * ## Why this is a plain test and not a shell scenario
 *
 * Nothing here composes. The subject is one `StateFlow` fed by one `store.value` call, so it
 * runs as an ordinary `commonTest` class on all three targets — no per-host entry points,
 * because there is no composition for the Android host's Robolectric runner to wrap.
 *
 * ## Why it exists at all
 *
 * `docs/TEST-HOSTS.md` records that no test seeded a `CachedProfile` and read it back, so the
 * body of [ShellPreferences.refreshCurrentUser] could be deleted with the suite unchanged. The
 * admin-only Settings section now takes its gate from that read, which makes the gap the
 * difference between "an admin sees the management screens" and "nobody ever does".
 */
class ShellPreferencesTest {

    /** The role the profile carries is the role the shell reports. */
    @Test
    fun theCachedProfileSuppliesTheAccountRole() = runTest {
        val store = UserDefaultsStore(InMemoryPreferences())
        // Seeded through the store's own writer rather than a raw map, so the value the read
        // gets back is the encoded form a real sign-in would have written.
        store.set(
            UserIdentity(id = shellTestUserID, email = "admin@example.com", role = UserRole.Admin),
            DefaultsKey.CachedProfile,
        )

        val preferences = ShellPreferences(store, Router())
        preferences.refreshCurrentUser()

        assertEquals(
            UserRole.Admin,
            preferences.currentUserRole.value,
            "an admin profile must read back as Admin, or the admin-only Settings section " +
                "never opens for the one person entitled to it",
        )
    }

    /**
     * Nobody signed in is *no* role, not [UserRole.Member].
     *
     * The distinction is what lets the gate answer "not yet" without inventing an identity: a
     * missing profile that read back as `Member` would be a signed-out device answering a
     * question about an account it does not have.
     */
    @Test
    fun noSignedInProfileIsNoRoleRatherThanAMember() = runTest {
        val preferences = ShellPreferences(UserDefaultsStore(InMemoryPreferences()), Router())
        preferences.refreshCurrentUser()

        assertNull(
            preferences.currentUserRole.value,
            "a missing profile must read as no role at all, not as a member",
        )
    }
}
