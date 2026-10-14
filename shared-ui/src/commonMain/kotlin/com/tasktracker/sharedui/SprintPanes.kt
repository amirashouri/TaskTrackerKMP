package com.tasktracker.sharedui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.tasktracker.designsystem.ttSeparator

/**
 * The Sprints tab's list-detail pair, side by side.
 *
 * ## Why this takes two lambdas and not two screens
 *
 * The obvious signature is one that takes the sprint list and the sprint detail directly, and it
 * is wrong here. [NavigationHost] already renders exactly these two screens — the tab root for the
 * list and the pushed route for the detail — and it renders them from the router, which is the
 * only navigation state in this module. A component that took the screens would need them handed
 * to it, which means either duplicating [NavigationHost]'s wiring for the detail or introducing a
 * second piece of state holding "the sprint the detail pane is showing".
 *
 * Both are the shape [NavigationHost] rejects for the session root and the editor overlay: two
 * answers to one question. It is how a selection ends up applied to the list pane and not to the
 * detail, or the other way round — and the symptom is invisible, because each pane renders
 * perfectly well on its own. So this takes the panes as *content*, and the caller passes the same
 * two expressions the single-pane layout renders. Nothing here knows what a sprint is.
 *
 * ## Why it is only reached under `Expanded`
 *
 * The caller decides. A phone keeps today's arrangement — the detail replaces the list — because
 * a 390dp window split in half gives neither pane a readable measure, and because the push it
 * replaces is also what system back and deep links already act on. Nothing in this file branches on
 * the platform, and nothing branches on the width either: the layout class is read once by the
 * caller and the answer arrives as which of these calls happens.
 */
@Composable
fun SprintTwoPanes(
    listPane: @Composable () -> Unit,
    detailPane: @Composable () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.fillMaxSize(),
        horizontalArrangement = Arrangement.spacedBy(PANE_GUTTER),
    ) {
        Box(modifier = Modifier.weight(PANE_WEIGHT).fillMaxHeight()) { listPane() }
        SprintPaneDivider()
        Box(modifier = Modifier.weight(PANE_WEIGHT).fillMaxHeight()) { detailPane() }
    }
}

/**
 * The divider drawn between the two panes.
 *
 * A hairline rather than a gap of nothing, because on a two-pane layout the panes' edges are the
 * only thing saying "these are one screen with two halves" rather than two screens that happen to
 * be adjacent. It is drawn by the caller's own background showing through [PANE_GUTTER], so there
 * is no second surface to keep in step with the glass one — which is the same reason
 * [TTGlassTabBar] is told to float rather than fill, and the same reason the rail draws one glass
 * surface rather than one per destination.
 */
@Composable
fun SprintPaneDivider(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .width(PANE_DIVIDER_WIDTH)
            .fillMaxHeight()
            .background(ttSeparator()),
    )
}

/**
 * How the two panes divide the width, as a `weight` each rather than two fixed widths.
 *
 * Equal, and that is a decision rather than a default. The list is a set of short rows and the
 * detail is a form with a column of fields and a task list, so unequal widths are defensible on
 * paper — but the widths are two magic numbers that then have to be kept in step with every
 * breakpoint, and the one thing this layout has to get right is that *both* panes are usable.
 * Equal weights put that first: at the 840dp threshold the shell is already past the point where
 * the list's natural width and the detail's natural width are both comfortable, so there is
 * nothing to gain from weighting them differently.
 *
 * A `weight` rather than a width also means the split follows the window instead of the window
 * needing to know the split.
 */
private const val PANE_WEIGHT = 1f

/**
 * The gap the divider lives in, in dp.
 *
 * Wide enough that the hairline is not flush against either pane's content, narrow enough that it
 * does not read as a column the panes are floating in.
 */
private val PANE_GUTTER = 1.dp

/** The divider's own width, in dp. */
private val PANE_DIVIDER_WIDTH = 1.dp
