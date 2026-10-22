package com.tasktracker.sharedui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

/**
 * The Backlog tab's list-detail pair, side by side.
 *
 * ## Why this is a third name rather than a shared `TwoPanes`
 *
 * For the reason `AdminPanes` gives: the three arrangements are the same boxes and the same
 * hairline — which is why they share [SprintPaneDivider], [PANE_WEIGHT] and [PANE_GUTTER] rather
 * than restating a geometry — and they are deliberately three names. What differs is which two
 * screens a caller passes; a shared name would be honest only until one arrangement needed a
 * different split. The name also keeps the decision in [NavigationHost], which is the only place
 * that knows the Backlog stack's editor route is the right pane.
 *
 * ## Why the caller sometimes does not call this at all
 *
 * The detail is a selection, not a screen the list always has. With no item chosen,
 * [NavigationHost] renders the backlog alone at full width; this composable is reached only when
 * the stack holds an editor. That is deliberately *not* a two-pane layout with a placeholder on
 * the right: a permanent empty column would take half the window away from the only thing on
 * screen, and there is no second thing to say until the user picks a row.
 *
 * ## Why two lambdas and not two screens
 *
 * For the reason `SprintTwoPanes` gives: the caller already renders both from the router, which
 * is the only navigation state in this module. A component handed the screens directly would
 * either duplicate that wiring or hold a second answer to "which item is selected". Nothing here
 * is animated and nothing is remembered, so a tap, a deep link and a restored stack all land on
 * the same pair and differ only in where it is drawn.
 */
@Composable
fun BacklogTwoPanes(
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
