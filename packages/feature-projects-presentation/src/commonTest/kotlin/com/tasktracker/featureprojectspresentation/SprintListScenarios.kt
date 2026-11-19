package com.tasktracker.featureprojectspresentation

/**
 * The sprint list's header, as scenarios.
 *
 * The Active/Archive picker used to keep the phone stack on every window: the project switcher
 * and the add action shared a row, and the picker sat under them on a row of its own. A wide
 * window now lays the picker between the two, the same arrangement as the backlog's section
 * picker, so both tabs change shape at the same width rather than one looking like a desktop
 * header and the other like a tablet that never grew up.
 *
 * Both arrangements are asserted directly. A single wide assertion would pass against a screen
 * that dropped the compact branch, and a single compact assertion would pass against the screen
 * as it was before the change.
 */
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.requiredWidth
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.tasktracker.domain.Sprint
import kotlinx.datetime.LocalDate
import kotlin.math.abs
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@OptIn(ExperimentalTestApi::class)
object SprintListScenarios {

    /**
     * A wide window puts the picker on the selector's row, between the selector and the action.
     *
     * Read as relationships between rectangles rather than absolute positions, so the assertion
     * says "between" instead of naming a coordinate that only holds at one window size. The
     * viewport is forced wider than the host on purpose: the Android and iOS hosts are
     * phone-sized, and a plain width would be coerced back to them and silently measure the
     * compact header.
     */
    val theWideHeaderPutsThePickerBetweenTheSelectorAndTheAction:
        suspend ComposeUiTest.() -> Unit = {
        sprintListShowing(width = SPRINT_WIDE_WIDTH)
        waitForIdle()

        val switcher = onNodeWithTag(ProjectSwitcherTag).fetchSemanticsNode().boundsInRoot
        val picker = onNodeWithTag(SprintListSectionsTag).fetchSemanticsNode().boundsInRoot
        val add = onNodeWithTag(SprintListNewTag).fetchSemanticsNode().boundsInRoot

        assertTrue(
            picker.left >= switcher.right - EDGE_TOLERANCE_PX,
            "the section picker starts at ${picker.left}px and the project selector ends at " +
                "${switcher.right}px, so the picker is not on the selector's row to its right: " +
                "picker=$picker switcher=$switcher",
        )
        assertTrue(
            picker.top < switcher.bottom && switcher.top < picker.bottom,
            "the section picker (${picker.top}..${picker.bottom}) and the project selector " +
                "(${switcher.top}..${switcher.bottom}) do not overlap vertically, so they are " +
                "stacked rather than sharing a row",
        )
        assertTrue(
            add.left >= picker.right - EDGE_TOLERANCE_PX,
            "the add action is at ${add.left}px, left of the picker's right edge " +
                "${picker.right}px, so the picker is not between the selector and the add " +
                "action: picker=$picker add=$add",
        )
        val gapCentre = (switcher.right + add.left) / 2f
        val pickerCentre = (picker.left + picker.right) / 2f
        assertTrue(
            abs(pickerCentre - gapCentre) <= EDGE_TOLERANCE_PX * 2,
            "the picker's centre is at ${pickerCentre}px where the gap between the selector " +
                "(${switcher.right}px) and the add action (${add.left}px) is centred at " +
                "${gapCentre}px, so the picker is not in the middle of that space: " +
                "picker=$picker switcher=$switcher add=$add",
        )
    }

    /**
     * A compact header keeps the picker on its own row, below the switcher and the action.
     *
     * This is the arrangement the screen had before the change, and it is asserted rather than
     * inferred from the wide branch: squeezing the picker into a phone-width row would leave
     * the project name a few characters and each segment too narrow for its label.
     */
    val theCompactHeaderKeepsThePickerBelowTheActions: suspend ComposeUiTest.() -> Unit = {
        sprintListShowing(width = SPRINT_COMPACT_WIDTH)
        waitForIdle()

        val switcher = onNodeWithTag(ProjectSwitcherTag).fetchSemanticsNode().boundsInRoot
        val picker = onNodeWithTag(SprintListSectionsTag).fetchSemanticsNode().boundsInRoot
        val add = onNodeWithTag(SprintListNewTag).fetchSemanticsNode().boundsInRoot

        assertTrue(
            picker.top >= add.bottom - EDGE_TOLERANCE_PX,
            "the section picker (${picker.top}..${picker.bottom}) starts above the add action's " +
                "bottom edge (${add.bottom}px), so the two share a row on a compact header",
        )
        assertTrue(
            picker.top >= switcher.bottom - EDGE_TOLERANCE_PX,
            "the section picker starts at ${picker.top}px and the project selector ends at " +
                "${switcher.bottom}px, so the picker is on the selector's row rather than " +
                "under it",
        )
        assertTrue(
            abs(picker.left - switcher.left) <= EDGE_TOLERANCE_PX,
            "the picker starts at ${picker.left}px and the selector at ${switcher.left}px, so " +
                "the stacked picker is not on the header's leading edge",
        )
    }

    /**
     * On a desktop-wide viewport the header and the sprint rows are capped together and centred.
     *
     * The cap is read against the list rather than the window: the list is the widest thing the
     * section column draws, so a cap applied to the header alone or to the window without
     * centring shows up as either an over-wide list or a list flush to one edge.
     */
    val wideLayoutClampsContentWidthAndCenters: suspend ComposeUiTest.() -> Unit = {
        sprintListShowing(width = SPRINT_DESKTOP_WIDTH)
        waitForIdle()

        val screen = onNodeWithTag(SprintListScreenTag).fetchSemanticsNode()
        val list = onNodeWithTag(SprintListTag).fetchSemanticsNode()
        val switcher = onNodeWithTag(ProjectSwitcherTag).fetchSemanticsNode()

        val maxAllowedPx = densityPixels(SprintMaxContentWidth) + EDGE_TOLERANCE_PX
        assertTrue(
            list.size.width <= maxAllowedPx,
            "the sprint rows should be clamped to at most $maxAllowedPx: actual=${list.size.width}",
        )

        // Centred against the screen rather than the host window: the scenario asks for a
        // desktop-width screen, which can be wider than the test host.
        val expectedLeft = screen.boundsInRoot.left + (screen.size.width - list.size.width) / 2f
        assertEquals(
            expectedLeft,
            list.boundsInRoot.left,
            2f,
            "the sprint list should be horizontally centered on wide viewports",
        )
        assertEquals(
            list.boundsInRoot.left,
            switcher.boundsInRoot.left,
            2f,
            "the header's leading control should share the list's left edge: " +
                "list=${list.boundsInRoot} switcher=${switcher.boundsInRoot}",
        )
    }

    /**
     * Half a pixel of slack for a bounds comparison.
     *
     * Layout works in integers at the host's density, but the comparison is in floating-point
     * pixels; touching edges are a correct layout, so they must not be reported as an overlap.
     */
    private const val EDGE_TOLERANCE_PX = 0.5f
}

/** A header width under the screen's wide threshold, so the stacked arrangement is in force. */
private val SPRINT_COMPACT_WIDTH = 400.dp

/** A header width over the screen's wide threshold. */
private val SPRINT_WIDE_WIDTH = 900.dp

/** A desktop width above the content cap, so both the clamp and the centring are observable. */
private val SPRINT_DESKTOP_WIDTH = 1400.dp

/** Converts a token width to the host's pixels, so a bounds assertion compares in one unit. */
@OptIn(ExperimentalTestApi::class)
private fun ComposeUiTest.densityPixels(dp: Dp): Float = with(density) { dp.toPx() }

/**
 * One active sprint, which is enough to keep the list out of its empty state — the scenarios
 * here are about the header, and an empty state has no header controls over it besides these.
 */
private fun activeSprintRows(sprint: Sprint = ViewPreviewData.openSprint): List<SprintListRow> =
    listOf(
        SprintListRow(
            sprint = sprint,
            startDate = LocalDate(2026, 3, 2),
            endDate = LocalDate(2026, 3, 16),
            committedStoryPoints = 8,
            achievedStoryPoints = 3,
        ),
    )

/**
 * The sprint list at a fixed width.
 *
 * `requiredWidth`, not `width`: a preferred width is coerced back to a phone-sized host, so the
 * wide arrangement could never be rendered on the Android and iOS hosts. Only the left edge has
 * to be on screen, and every control measured starts there.
 */
@OptIn(ExperimentalTestApi::class)
private fun ComposeUiTest.sprintListShowing(width: Dp? = null) {
    setContent {
        Themed {
            Box(
                modifier = if (width == null) {
                    Modifier.fillMaxSize()
                } else {
                    Modifier.requiredWidth(width).fillMaxHeight()
                },
            ) {
                SprintListScreen(
                    inProgressRows = activeSprintRows(),
                    scheduledRows = emptyList(),
                    finishedRows = emptyList(),
                    archivedRows = emptyList(),
                    activeIsEmpty = false,
                    archivedIsEmpty = true,
                    errorMessage = null,
                    project = ViewPreviewData.seededProject,
                    onOpen = {},
                    onNewSprint = {},
                    onDismissError = {},
                    onOpenSwitcher = {},
                    projects = ViewPreviewData.pickable(),
                    onSelectProject = {},
                )
            }
        }
    }
}
