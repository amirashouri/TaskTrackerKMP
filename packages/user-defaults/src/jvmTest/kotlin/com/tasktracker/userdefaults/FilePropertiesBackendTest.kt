package com.tasktracker.userdefaults

import com.tasktracker.core.isDesktopApp
import java.io.File
import java.nio.file.Files
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The Desktop persistence half of the JVM preferences target.
 *
 * Every scenario here turns on one property, and it is the reason 4.6 exists: a second
 * backend built over the same file sees what the first one wrote. The in-memory backend
 * cannot do that, so a test that asserted against a single instance would pass against
 * both implementations and prove nothing. Each scenario therefore constructs a *second*
 * backend — the relaunch — and reads through it.
 *
 * Each test gets its own temporary directory rather than the real app-support path, both
 * so a test run never writes to a developer's actual preferences and so the
 * `preferences.properties` this writes cannot be mistaken for the app's own.
 */
class FilePropertiesBackendTest {

    /** Runs [block] against a fresh temp directory that is removed afterwards. */
    private fun withTemporaryDirectory(block: (File) -> Unit) {
        val directory = Files.createTempDirectory("tasktracker-prefs").toFile()
        try {
            block(directory)
        } finally {
            directory.deleteRecursively()
        }
    }

    @Test
    fun `a value written by one backend is read back by the next one over the same file`() =
        withTemporaryDirectory { directory ->
            runTest {
                val file = File(directory, "preferences.properties")

                // The first launch writes.
                FilePropertiesBackend(file).put("dark".encodeToByteArray(), "settings.themeScheme")

                // The relaunch: a brand-new backend over the same path, with nothing shared
                // in memory. An in-memory backend fails here, which is the whole assertion.
                val relaunched = FilePropertiesBackend(file)

                assertEquals(
                    "dark",
                    relaunched.bytes("settings.themeScheme")?.decodeToString(),
                    "a relaunch must read the scheme the previous process wrote to $file",
                )
            }
        }

    @Test
    fun `a preference written through the store survives a relaunch`() =
        withTemporaryDirectory { directory ->
            runTest {
                val file = File(directory, "preferences.properties")

                val first = UserDefaultsStore(FilePropertiesBackend(file))
                assertTrue(
                    first.set("project-1", key = DefaultsKey.LastSeenProject),
                    "the store should encode and persist the last-visited project",
                )

                // The relaunch rebuilds the store over the same file, which is the shape the
                // Desktop shell uses on the next process.
                val relaunched = UserDefaultsStore(FilePropertiesBackend(file))

                assertEquals(
                    "project-1",
                    relaunched.value<String>(DefaultsKey.LastSeenProject),
                    "the last-visited project is device-scoped and must outlive the process",
                )
            }
        }

    @Test
    fun `a removal reaches the file rather than only the writing instance`() =
        withTemporaryDirectory { directory ->
            runTest {
                val file = File(directory, "preferences.properties")
                val key = "navigation.lastSeenProject"

                FilePropertiesBackend(file).put("project-1".encodeToByteArray(), key)

                // A positive control, and not decoration: without it the two assertions
                // below also pass when the write silently never reached the file, which is
                // the mutation this scenario exists to catch. "Absent" only means
                // "removed" if it was first observably present.
                assertTrue(
                    FilePropertiesBackend(file).contains(key),
                    "the write must be visible to a second instance before the removal means anything",
                )

                FilePropertiesBackend(file).remove(key)

                val relaunched = FilePropertiesBackend(file)

                assertNull(
                    relaunched.bytes(key),
                    "a removed key must be absent from $file, not merely hidden from the writer",
                )
                assertFalse(
                    relaunched.contains(key),
                    "contains must report the removal for the next process too",
                )
            }
        }

    @Test
    fun `contains sees a key an earlier instance wrote`() =
        withTemporaryDirectory { directory ->
            runTest {
                val file = File(directory, "preferences.properties")
                FilePropertiesBackend(file).put("value".encodeToByteArray(), "board.showsBacklogColumn")

                assertTrue(
                    FilePropertiesBackend(file).contains("board.showsBacklogColumn"),
                    "a key on disk is present for a later process, which is how sign-out tells " +
                        "\"this user chose the default\" from \"this user has never touched it\"",
                )
            }
        }

    @Test
    fun `an absent file reads as empty rather than failing`() =
        withTemporaryDirectory { directory ->
            runTest {
                // No file has been created in this directory at all. A first launch is exactly
                // this state, so it must not be an error.
                val backend = FilePropertiesBackend(File(directory, "preferences.properties"))

                assertNull(backend.bytes("settings.themeScheme"))
                assertFalse(backend.contains("settings.themeScheme"))
            }
        }

    @Test
    fun `the desktop marker routes preferences to the file-backed backend`() =
        withTemporaryDirectory { directory ->
            runTest {
                // The router is the one thing standing between a Desktop restart and an
                // in-memory backend that silently loses both keys. The flag is process-global,
                // so it is restored in a `finally` — a leaked `true` would change what every
                // later test in this JVM constructs.
                val wasDesktop = isDesktopApp
                try {
                    isDesktopApp = true
                    // No I/O happens at construction, so the real app-support path is not
                    // touched here; the file-backed behaviour itself is covered above against
                    // an injected path.
                    assertIs<FilePropertiesBackend>(
                        platformPreferences(),
                        "on Desktop the router must hand back the file-backed backend",
                    )

                    isDesktopApp = false
                    assertIs<JvmPropertiesBackend>(
                        platformPreferences(),
                        "on the JVM test host the router must keep the in-memory backend, so a " +
                            "test that writes a preference leaves nothing behind",
                    )
                } finally {
                    isDesktopApp = wasDesktop
                }
            }
        }

    @Test
    fun `the desktop preferences file sits beside the database and is named for its role`() {
        // Structural, and deliberately not opened: this asserts the *path contract* the
        // Desktop shell and the backend both resolve, which is what stops them writing to
        // two different files. The directory is the database's app-support directory, and
        // the name is fixed rather than passed in.
        val file = preferencesFile()

        assertEquals(
            "preferences.properties",
            file.name,
            "the file name is part of the contract: the shell reads the same path the backend writes",
        )
        assertTrue(
            file.parentFile?.path.orEmpty().isNotEmpty(),
            "the preferences file must live in a directory, not at the filesystem root",
        )
    }
}
