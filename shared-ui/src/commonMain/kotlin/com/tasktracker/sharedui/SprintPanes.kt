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

/** Two-pane side-by-side layout container for split list and detail views. */
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

/** Hairline divider line placed between side-by-side panes. */
@Composable
fun SprintPaneDivider(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .width(PANE_DIVIDER_WIDTH)
            .fillMaxHeight()
            .background(ttSeparator()),
    )
}

internal const val PANE_WEIGHT = 1f
internal val PANE_GUTTER = 1.dp
private val PANE_DIVIDER_WIDTH = 1.dp