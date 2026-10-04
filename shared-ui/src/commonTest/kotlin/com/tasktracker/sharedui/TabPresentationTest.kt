package com.tasktracker.sharedui

import com.tasktracker.designsystem.ttTabIcons
import com.tasktracker.routing.AppTab
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * The shell's choice of glyph per tab, which is a shell decision and not the design system's.
 *
 * ## Why this is not a screenshot test
 *
 * [ttTabIcons] owns the *drawings* and asserts them itself; what lives here is the mapping
 * from tab to drawing, which is a different question with a different owner. It is also the
 * question a screenshot cannot answer: two 22dp glyphs that differ by one bar are the same
 * blob of pixels at review size, and the failure reads as "the icon looks a bit off" rather
 * than as a name.
 *
 * It existed because mutation U1 handed the Sprints tab the Projects glyph and **every test
 * stayed green** - the design system's geometry tests still passed because they never ask
 * which tab a glyph belongs to, and the shell's scenarios assert labels and roles but not
 * drawings. A green mutation is a missing assertion, so this is that assertion.
 *
 * ## Why the assertions are a set, not a table
 *
 * Restating the mapping as `AppTab.Sprints to ttTabIcons.sprints` in a test would be a second
 * copy of the thing being checked, and it would keep passing after the production mapping
 * changed — the exact failure `ttTabIcons.all` exists to prevent one layer up. Asserting the
 * two properties that actually matter instead:
 *
 *  - **every tab has its own glyph**, so no two tabs are the same picture; and
 *  - **the set of glyphs handed out is the design system's whole set**, so a tab cannot be
 *    given a glyph that is not one of ours.
 *
 * Together those pin the mapping without naming it. The first catches a duplicated glyph (U1);
 * the second catches a tab drawing something from outside `ttTabIcons`.
 */
class TabPresentationTest {

    @Test
    fun `no two tabs share a glyph`() {
        val perTab = AppTab.entries.map { tabIcon(it) }

        val duplicates = perTab.groupingBy { it }.eachCount().filterValues { it > 1 }.keys
        assertEquals(
            emptyList(),
            duplicates.toList(),
            "these glyphs are handed to more than one tab, so the tab bar shows the same " +
                "picture twice: ${duplicates.map { it.name }}",
        )
    }

    @Test
    fun `the glyphs handed out are the design system's whole set`() {
        val handedOut = AppTab.entries.map { tabIcon(it) }.toSet()
        val designed = ttTabIcons.all.toSet()

        assertEquals(
            designed,
            handedOut,
            "the shell hands out ${handedOut.map { it.name }}, which is not the set the " +
                "design system drew (${designed.map { it.name }})",
        )
    }
}