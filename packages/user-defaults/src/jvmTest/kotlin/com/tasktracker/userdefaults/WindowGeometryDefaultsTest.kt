package com.tasktracker.userdefaults

import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * The Desktop window size's persistence, on the JVM.
 *
 * `jvmTest` rather than `commonTest` because the code under test is `jvmMain`: the window is
 * a Desktop-only concept and neither `iosMain` nor `androidHostTest` has anything to compile
 * against it.
 *
 * Each test builds its own [InMemoryPreferences] and never touches `platformPreferences()`,
 * so nothing here reads or writes a developer's real `preferences.properties`.
 */
class WindowGeometryDefaultsTest {

    @Test
    fun remembersTheSizeForTheNextLaunch() = runBlocking {
        val backend = InMemoryPreferences()
        WindowGeometryDefaults.save(WindowGeometry(width = 1280, height = 800), backend)

        // A fresh store over the same backend is what the next launch sees.
        assertEquals(
            WindowGeometry(width = 1280, height = 800),
            WindowGeometryDefaults.load(backend),
            "the size written by one launch was not read back by the next, so the window " +
                "would reopen at the default every time",
        )
    }

    /**
     * The key is deliberately outside `DefaultsKey`, and this is the test that would catch it
     * moving in: `removeAll` walks that enum on sign-out, so a member would be cleared with
     * the session. The window belongs to the machine, and a sign-out that forgot its size
     * would reopen the app at the default for the next person.
     */
    @Test
    fun theSizeSurvivesSignOutBecauseItIsNotADefaultsKey() = runBlocking {
        val backend = InMemoryPreferences()
        WindowGeometryDefaults.save(WindowGeometry(width = 1280, height = 800), backend)

        UserDefaultsStore(backend).removeAll()

        assertEquals(
            WindowGeometry(width = 1280, height = 800),
            WindowGeometryDefaults.load(backend),
            "sign-out cleared the window size, so ${WindowGeometryDefaults.KEY} is inside the " +
                "reach of DefaultsKey.removeAll and no longer outlives a session",
        )
    }

    @Test
    fun aSizeThatCannotBeParsedReadsBackAsAbsent() = runBlocking {
        val backend = InMemoryPreferences(seed = mapOf(WindowGeometryDefaults.KEY to "wide".encodeToByteArray()))

        assertNull(
            WindowGeometryDefaults.load(backend),
            "a stored window size that is not WxH should read back as absent, so the entry " +
                "point falls back to the default instead of throwing on launch",
        )
    }

    /**
     * The first launch, before anything has been written. This is the one path that reaches
     * [WindowGeometry.parse] with `null` *through the store*, which is what a real first run
     * does — the other absent case is fed straight to the size resolver and never touches the
     * `stored?.indexOf('x') ?: return null` fallback.
     */
    @Test
    fun nothingWrittenYetReadsBackAsAbsent() = runBlocking {
        assertNull(
            WindowGeometryDefaults.load(InMemoryPreferences()),
            "an untouched preferences file should read back as no size, not as a zero-sized " +
                "window and not as a crash on the first launch",
        )
    }
}
