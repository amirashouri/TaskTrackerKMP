package com.tasktracker.sharedui

import com.tasktracker.featureauthdomain.UserIdentity
import com.tasktracker.featureauthdomain.UserRole
import com.tasktracker.featuresettingsdomain.UserDetails
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
 * Nothing here composes. The subject is one `StateFlow` fed by two `store.value` reads, so it
 * runs as an ordinary `commonTest` class on all three targets — no per-host entry points,
 * because there is no composition for the Android host's Robolectric runner to wrap.
 *
 * ## Why it exists at all
 *
 * `docs/TEST-HOSTS.md` records that no test seeded a `CachedProfile` and read it back, so the
 * body of [ShellPreferences.refreshCurrentUser] could be deleted with the suite unchanged. The
 * admin-only Settings section now takes its gate from that read, which makes the gap the
 * difference between "an admin sees the management screens" and "nobody ever does".
 *
 * The same read also feeds the shell's greeting, from the name Settings edits rather than the
 * sign-in snapshot — see [thePersistedProfileNameWinsOverTheCachedIdentity].
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

    /**
     * The name Settings edits is the name the shell shows.
     *
     * `CachedProfile` is the sign-in snapshot and a rename never rewrites it, so a greeting that
     * read only that key would keep the pre-rename name forever after the Settings row changed.
     * `UserProfile` is what `LiveSettingsService` persists on every save, and it wins.
     */
    @Test
    fun thePersistedProfileNameWinsOverTheCachedIdentity() = runTest {
        val store = UserDefaultsStore(InMemoryPreferences())
        store.set(
            UserIdentity(id = shellTestUserID, email = "ada@example.com"),
            DefaultsKey.CachedProfile,
        )
        store.set(
            UserDetails(fullName = "Ada Lovelace", email = "ada@example.com"),
            DefaultsKey.UserProfile,
        )

        val preferences = ShellPreferences(store, Router())
        preferences.refreshCurrentUser()

        assertEquals(
            "Ada Lovelace",
            preferences.currentUserName.value,
            "the shell reads \"${preferences.currentUserName.value}\" instead of the name the " +
                "Settings row shows — a rename would change one and not the other",
        )
    }

    /**
     * A profile left on the device by another account cannot rename this one.
     *
     * The settings service is app-scoped and its flow outlives a sign-out, so the first value
     * after signing in as somebody else can still be the previous person's profile. The email
     * is the only field the two records share, and it is what the fallback turns on.
     */
    @Test
    fun aProfileForAnotherAccountDoesNotRenameTheSignedInPerson() = runTest {
        val store = UserDefaultsStore(InMemoryPreferences())
        store.set(
            UserIdentity(id = shellTestUserID, email = "grace@example.com"),
            DefaultsKey.CachedProfile,
        )
        store.set(
            UserDetails(fullName = "Ada Lovelace", email = "ada@example.com"),
            DefaultsKey.UserProfile,
        )

        val preferences = ShellPreferences(store, Router())
        preferences.refreshCurrentUser()

        assertEquals(
            "grace",
            preferences.currentUserName.value,
            "grace is signed in, so the greeting must be grace's, not the name left in the " +
                "store by ada's session",
        )
    }

    /**
     * A live emission renames without a root change, and clearing the name restores the fallback.
     *
     * This is the shape `RootScreen` hands [ShellPreferences.applyUserDetails] from
     * `SettingsPort.userDetailsUpdates()`: the committed value first, then the confirmed value
     * a failed PATCH reverts to. Re-reading the store inside this path would race the service's
     * flow-then-write order; the emitted value is the one the screen is showing.
     */
    @Test
    fun aLiveProfileRenamesImmediatelyAndFallsBackWhenCleared() = runTest {
        val store = UserDefaultsStore(InMemoryPreferences())
        store.set(
            UserIdentity(id = shellTestUserID, email = "ada@example.com"),
            DefaultsKey.CachedProfile,
        )

        val preferences = ShellPreferences(store, Router())
        preferences.refreshCurrentUser()
        assertEquals(
            "ada",
            preferences.currentUserName.value,
            "with no settings profile yet, the fallback is the email prefix",
        )

        preferences.applyUserDetails(
            UserDetails(fullName = "Ada Lovelace", email = "ada@example.com"),
        )
        assertEquals(
            "Ada Lovelace",
            preferences.currentUserName.value,
            "a committed rename must reach the greeting before the root changes",
        )

        preferences.applyUserDetails(UserDetails(fullName = "", email = "ada@example.com"))
        assertEquals(
            "ada",
            preferences.currentUserName.value,
            "a cleared name must fall back to the identity rather than render an empty greeting",
        )
    }
}
