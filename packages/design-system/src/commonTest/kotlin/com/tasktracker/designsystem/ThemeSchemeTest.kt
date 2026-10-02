package com.tasktracker.designsystem

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The behaviour of [ThemeScheme] itself.
 *
 * These are the original's `ThemeSchemeTests` plus the two properties the port added, and
 * they are all pure logic — no composition, no platform. That is the point of the split:
 * whether `System` means "no opinion" is a question about a value, and a question about a
 * value should not need a rendering pipeline to answer. The *other* half — that a pinned
 * scheme actually changes what gets painted — is `PinnedAppearanceTest`, which has to
 * compose.
 */
class ThemeSchemeTest {

    /**
     * `System` is the absence of an opinion, not a pinned appearance.
     *
     * The load-bearing assertion in the whole suite. A design system that resolved `System`
     * to light "as a sensible default" would make the third case unreachable while still
     * passing every test about it: the enum would have three members, the picker would show
     * three options, and the app would ignore one of them.
     */
    @Test
    fun `system has no colour scheme`() {
        assertNull(ThemeScheme.System.colorScheme)
    }

    /** The other two *do* pin, and they pin opposite ways. */
    @Test
    fun `light and dark pin the appearance`() {
        assertEquals(ttColorScheme.Light, ThemeScheme.Light.colorScheme)
        assertEquals(ttColorScheme.Dark, ThemeScheme.Dark.colorScheme)
    }

    /**
     * Every case round-trips through its stored form.
     *
     * The failure this catches is a rename: a scheme whose `wireValue` no longer matches
     * what [fromStoredValue] accepts, which would silently reset every existing install to
     * `System` on upgrade with nothing in the logs to say why.
     */
    @Test
    fun `a stored value round-trips`() {
        for (scheme in ThemeScheme.all) {
            assertEquals(scheme, ThemeScheme.fromStoredValue(scheme.wireValue))
        }
    }

    /**
     * Unreadable stored values fall back to following the system.
     *
     * Every one of these is a real way the value goes wrong in the field: absent (a fresh
     * install), empty (a backend that wrote a zero length), a value from a build that had a
     * scheme this one does not, a case-mangled one from a hand-edited plist, and something
     * that was never a theme name at all. They are all indistinguishable to the app and
     * they all mean the same thing.
     *
     * `"DARK"` is the interesting one. A case-insensitive parse would be *more* forgiving
     * and would be wrong: it would make two different byte strings resolve to one value, so
     * a plist edited by hand would work while nothing told the user their edit had been
     * interpreted rather than honoured.
     */
    @Test
    fun `unreadable stored values fall back to following the system`() {
        for (stored in listOf(null, "", "sepia", "DARK", "Dark", "0", " light", "light ")) {
            assertEquals(
                ThemeScheme.System,
                ThemeScheme.fromStoredValue(stored),
                "\"$stored\" should have fallen back to System",
            )
        }
    }

    /**
     * A missing *case* is an error, not a fallback.
     *
     * The deliberate asymmetry with the test above, and the reason it is tested at all: an
     * unrecognised stored value is a fact about the world and has a right answer, while a
     * call site naming a scheme that does not exist is a bug in the code. Silently
     * resolving that to `System` would turn a compile error into a user whose appearance
     * preference quietly stops applying.
     */
    @Test
    fun `naming a scheme that does not exist fails rather than falling back`() {
        assertFailsWith<IllegalArgumentException> { ThemeScheme.of("sepia") }
        assertFailsWith<IllegalArgumentException> { ThemeScheme.of("DARK") }
    }

    /** A picker cannot show duplicates, so the titles have to be distinct. */
    @Test
    fun `every case has a distinct title`() {
        val titles = ThemeScheme.all.map { it.title }
        assertEquals(titles.size, titles.toSet().size, "two schemes share the title $titles")
    }

    /**
     * The wire values are distinct too, which is what makes [of] and [fromStoredValue]
     * well-defined. Two cases sharing a `wireValue` would make the round-trip test above
     * pass while leaving one of the two unreachable in storage.
     */
    @Test
    fun `every case has a distinct wire value`() {
        val wires = ThemeScheme.all.map { it.wireValue }
        assertEquals(wires.size, wires.toSet().size, "two schemes share the wire value $wires")
    }

    /**
     * The stored key is the original's, exactly.
     *
     * A literal, deliberately. This is the one value in the file that cannot be derived from
     * the enum, and it is the one that would break every existing install if it changed:
     * nothing in the code would fail to compile, and every user would silently get the
     * default appearance. The comment on [ThemeScheme.defaultsKey] is where the reasoning
     * lives; this is the assertion that it was not retyped.
     */
    @Test
    fun `the stored key matches the original so installs keep their choice`() {
        assertEquals("com.tasktracker.settings.themeScheme", ThemeScheme.defaultsKey)
    }

    /**
     * `System` is the default, and it is the *first* case.
     *
     * Order matters: a picker renders [all] in order, and a build that put `Light` first
     * would present "Light" as the default selection while `System` — the actual default —
     * sat second. Nothing else in the suite would notice, because both would still be
     * reachable.
     */
    @Test
    fun `system is the default and leads the list`() {
        assertEquals(ThemeScheme.System, ThemeScheme.fromStoredValue(null))
        assertEquals(ThemeScheme.System, ThemeScheme.all.first())
    }

    /**
     * `opposite` really is the other one, for both.
     *
     * Trivial-looking, and it is the kind of helper that gets written once as `if (this ==
     * Light) Dark else Light` and then reused somewhere that only ever means "the other
     * one *relative to the system*". A one-line `else` that returned the input would pass a
     * smoke test.
     */
    @Test
    fun `opposite flips the appearance`() {
        assertEquals(ttColorScheme.Dark, ttColorScheme.Light.opposite)
        assertEquals(ttColorScheme.Light, ttColorScheme.Dark.opposite)
    }
}
