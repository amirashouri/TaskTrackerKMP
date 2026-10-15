package com.tasktracker.sharedui

import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import com.tasktracker.userdefaults.WindowGeometry
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The Desktop window's opening size, on the JVM.
 *
 * `jvmTest` rather than `commonTest` because `DesktopMain` is `jvmMain`: there is no iOS or
 * Android-host entry point to add, so the `commonTest`-body-per-host rule does not apply.
 *
 * The two things a window does that no test can observe — actually opening at this size, and
 * actually receiving the resource title — are compile-checked in the entry point; what is
 * pinned here is the *decision* about which size to ask for, which is a pure function.
 */
class DesktopWindowJvmTest {

    /**
     * The default is the point of the task: opening below the threshold would leave the wide
     * shell unreachable by hand, which is what the task exists to fix.
     */
    @Test
    fun theDefaultWindowOpensPastTheExpandedThreshold() {
        assertTrue(
            DefaultDesktopWindowSize.width >= ShellExpandedMinimumWidth,
            "the default Desktop window is ${DefaultDesktopWindowSize.width} wide, below the " +
                "${ShellExpandedMinimumWidth} expanded threshold, so a first launch would " +
                "draw the phone shell and a desktop layout would need the frame dragged",
        )
    }

    @Test
    fun aRememberedSizeIsOpenedAtRatherThanTheDefault() {
        assertEquals(
            DpSize(width = 1000.dp, height = 700.dp),
            desktopWindowSize(WindowGeometry(width = 1000, height = 700)),
            "a remembered window size was discarded in favour of the default, so the window " +
                "would reopen at the default every launch",
        )
    }

    @Test
    fun withNothingRememberedTheDefaultIsUsed() {
        assertEquals(
            DefaultDesktopWindowSize,
            desktopWindowSize(null),
            "an absent stored size should open at the default, not at some other size",
        )
    }
}
