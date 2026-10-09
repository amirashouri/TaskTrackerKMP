package com.tasktracker.featureprojectspresentation

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.tasktracker.designsystem.ttAccent
import com.tasktracker.designsystem.ttSeparator

/**
 * A progress bar, or an empty track when there is no goal.
 *
 * ## Shared by two screens, which is why it is its own file
 *
 * [SprintListRowView] draws it for a sprint in the list and [SprintDetailScreen] draws it
 * for the sprint on its own page, on the same rule and with the same tokens. That is the
 * one piece those two screens have in common, so it lives here rather than being private
 * in one of them — a private copy in either would be a second bar to keep in step with the
 * first, and the two would disagree the first time the fill's colour changed.
 *
 * [fraction] is `null` for "no goal set" and may exceed `1f` for a sprint that beat its
 * target — see [SprintListRow.progressFraction] for why it is not clamped. The fill's
 * width is therefore allowed past the track's end, and the track is drawn first so the
 * fill reads as sitting *on* it rather than being cut off by it.
 */
@Composable
fun SprintProgressBar(
    fraction: Float?,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(SPRINT_PROGRESS_BAR_HEIGHT)
            .clip(RoundedCornerShape(SPRINT_PROGRESS_BAR_RADIUS))
            .background(ttSeparator())
            .testTag(SprintProgressBarTag),
    ) {
        if (fraction != null && fraction > 0f) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(fraction.coerceAtMost(SPRINT_PROGRESS_BAR_MAX_WIDTH))
                    .height(SPRINT_PROGRESS_BAR_HEIGHT)
                    .clip(RoundedCornerShape(SPRINT_PROGRESS_BAR_RADIUS))
                    .background(ttAccent()),
            )
        }
    }
}

const val SprintProgressBarTag = "sprints.row.progress"

/**
 * The bar's dimensions. Not `const`, because `dp` is a value class over a `Float` and
 * `const val` accepts only primitives and `String` — a progress bar's height as a plain
 * `6.dp` at each call site is two copies waiting to differ, so it is named here even
 * though the binding is not compile-time.
 */
private val SPRINT_PROGRESS_BAR_HEIGHT = 6.dp
private val SPRINT_PROGRESS_BAR_RADIUS = 3.dp

/** `fillMaxWidth` takes a fraction of 0..1; past 1 the layout would throw. */
private const val SPRINT_PROGRESS_BAR_MAX_WIDTH = 1f
