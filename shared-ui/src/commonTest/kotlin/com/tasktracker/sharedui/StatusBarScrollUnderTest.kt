package com.tasktracker.sharedui

import com.tasktracker.routing.AppRoute
import com.tasktracker.routing.AppTab
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Which destination the shell lets run to the window's top edge.
 *
 * The decision rather than its effect, because the effect is a safe-area inset the JVM host
 * reports as `0.dp` — a padded destination and an unpadded one lay out identically there, so a
 * shell-level scenario cannot tell them apart. These four cases are the whole truth table, and
 * the first one is the bug they exist for: the settings root's path is *empty*, so an earlier
 * `size == 1` test answered "no" and the list was inset like every other screen.
 */
class StatusBarScrollUnderTest {

    @Test
    fun `the settings root draws under the status bar`() {
        assertTrue(
            drawsUnderStatusBar(
                tab = AppTab.Settings,
                path = emptyList(),
                hasSettingsPort = true,
            ),
            "the settings root's path is empty, and the list is the surface that asked to scroll " +
                "under the notch — answering false here puts it back below the safe area",
        )
    }

    @Test
    fun `a pushed admin route on the settings stack keeps the inset`() {
        assertFalse(
            drawsUnderStatusBar(
                tab = AppTab.Settings,
                path = listOf(AppRoute.Settings, AppRoute.AdminMembers),
                hasSettingsPort = true,
            ),
            "the members list has a back header of its own and nothing asked for it to run " +
                "under the notch",
        )
    }

    @Test
    fun `no other tab draws under the status bar`() {
        for (tab in AppTab.entries.filterNot { it == AppTab.Settings }) {
            assertFalse(
                drawsUnderStatusBar(tab = tab, path = emptyList(), hasSettingsPort = true),
                "$tab would lose its safe-area inset, moving every screen on it up under the notch",
            )
        }
    }

    @Test
    fun `the missing settings fallback keeps the inset`() {
        assertFalse(
            drawsUnderStatusBar(
                tab = AppTab.Settings,
                path = emptyList(),
                hasSettingsPort = false,
            ),
            "the fallback is one sentence on a blank page, not a list — it has nothing to scroll " +
                "under the notch and would only move text into it",
        )
    }
}
