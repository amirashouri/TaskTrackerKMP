package com.tasktracker.sharedui

import com.tasktracker.core.isDesktopApp
import com.tasktracker.userdefaults.FilePropertiesBackend
import com.tasktracker.userdefaults.JvmPropertiesBackend
import com.tasktracker.userdefaults.PreferencesBackend
import com.tasktracker.userdefaults.platformPreferences
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

/**
 * [startDesktopProcess] on the JVM: the one thing the Desktop shell does that the JVM test
 * host does not.
 *
 * `main` opens a window and blocks until it closes, so this is the only way into the
 * sequence. Everything Desktop does differently hangs off the flag that sequence sets — both
 * JVM storage seams choose their backend from it when they are constructed — so a process
 * without it signs the user out on every restart and forgets the appearance, silently. That
 * is why these are assertions rather than a comment: before this file existed, deleting the
 * assignment left the whole suite byte-identical.
 *
 * No `commonTest` body and no iOS or Android-host entry point, for a source-set reason rather
 * than a runner one: the code under test is `jvmMain`, which is not on either of those
 * targets' compile paths.
 */
class DesktopEntryPointJvmTest {

    @Test
    fun theShellOpensInAProcessThatIsAlreadyTheDesktopApp() {
        var markedWhenTheShellOpened: Boolean? = null

        onTheTestHost {
            startDesktopProcess { markedWhenTheShellOpened = isDesktopApp }
        }

        assertEquals(
            true,
            markedWhenTheShellOpened,
            "the shell was opened with isDesktopApp still false, so the graph it builds would " +
                "come up on the test host's in-memory stores and Desktop would lose the " +
                "session and the appearance on every restart",
        )
    }

    @Test
    fun aDesktopProcessGetsTheFileBackedPreferencesRatherThanTheTestHostStub() {
        var backendWhenTheShellOpened: PreferencesBackend? = null

        onTheTestHost {
            startDesktopProcess { backendWhenTheShellOpened = platformPreferences() }
        }

        // Reading the type writes nothing: `FilePropertiesBackend` only stores the `File` it is
        // handed and loads it per call, so this asserts the routing without opening the
        // developer's preferences file.
        assertIs<FilePropertiesBackend>(
            backendWhenTheShellOpened,
            "the process is marked, but the preferences router handed the shell " +
                "${backendWhenTheShellOpened?.let { it::class.simpleName }} instead of " +
                "${FilePropertiesBackend::class.simpleName}",
        )
    }

    @Test
    fun theTestHostGetsTheInMemoryStub() {
        // Stated rather than assumed. A flag left set by an earlier test in this class would
        // make every later `commonTest` scenario in the module construct a real keychain and
        // read and write the developer's real `preferences.properties`; this asserts the entry
        // state rather than trusting it.
        assertEquals(
            false,
            isDesktopApp,
            "a previous test left isDesktopApp set, so every scenario after it in this JVM " +
                "constructs the Desktop app's real stores",
        )

        // The other half of the same decision: without this, all three tests would pass even if
        // every host got the file-backed backend.
        assertIs<JvmPropertiesBackend>(
            platformPreferences(),
            "the JVM test host must keep the in-memory backend, so a scenario that writes a " +
                "preference leaves nothing behind",
        )
    }

    /**
     * Runs [block] and restores [isDesktopApp] afterwards.
     *
     * The flag is process-global and this JVM runs every `commonTest` scenario in the module,
     * so a leaked `true` would have the shell scenarios construct a real keychain and read and
     * write the developer's real `preferences.properties` for the rest of the run.
     */
    private fun onTheTestHost(block: () -> Unit) {
        val wasDesktop = isDesktopApp
        try {
            block()
        } finally {
            isDesktopApp = wasDesktop
            // Checked here rather than trusted: the first test to leave the flag set fails
            // here, and a flag that was already set when a test started is what the stub test's
            // entry assertion catches. Between them a leak is red in every method order.
            assertEquals(
                wasDesktop,
                isDesktopApp,
                "this test left isDesktopApp at $isDesktopApp instead of $wasDesktop, so every " +
                    "scenario after it in this JVM constructs the Desktop app's real stores",
            )
        }
    }
}
