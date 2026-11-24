package com.tasktracker.featureprojectspresentation

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.tasktracker.designsystem.ttAccent
import com.tasktracker.designsystem.ttLabelSecondary
import com.tasktracker.designsystem.ttRadius
import com.tasktracker.designsystem.ttSeparator
import com.tasktracker.designsystem.ttText
import com.tasktracker.designsystem.ttTypography
import com.tasktracker.domain.EpicStatus
import com.tasktracker.resources.Res
import com.tasktracker.resources.status_backlog
import com.tasktracker.resources.status_done
import com.tasktracker.resources.status_in_progress
import com.tasktracker.resources.status_todo
import org.jetbrains.compose.resources.stringResource

/**
 * The status an epic shows, in the app's own words.
 *
 * The four coarse states reuse the item status strings rather than inventing a second
 * vocabulary: an epic is "In Progress" for the same reason a task is, and a distinct set of
 * epic-only labels would make the same state read two ways on one screen.
 */
@Composable
fun epicStatusLabel(status: EpicStatus): String = stringResource(
    when (status) {
        EpicStatus.Backlog -> Res.string.status_backlog
        EpicStatus.Todo -> Res.string.status_todo
        EpicStatus.InProgress -> Res.string.status_in_progress
        EpicStatus.Done -> Res.string.status_done
    },
)

/**
 * A thin progress bar for an epic's finished subtasks.
 *
 * The track is [ttSeparator] and the fill [ttAccent], so the bar carries no colour the rest of
 * the app has not already taught: it is the same pair a focused field uses. Zero of zero draws
 * an empty track rather than a full one — "no visible tasks" is not "complete", which is the
 * same rule [deriveEpicStatus] states.
 */
@Composable
fun EpicProgressBar(
    done: Int,
    total: Int,
    modifier: Modifier = Modifier,
) {
    val fraction = if (total <= 0) 0f else (done.toFloat() / total).coerceIn(0f, 1f)
    val shape = RoundedCornerShape(ttRadius.control)
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(EPIC_PROGRESS_HEIGHT)
            .clip(shape)
            .background(ttSeparator())
            .testTag(EpicProgressTrackTag),
    ) {
        if (fraction > 0f) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(fraction)
                    .fillMaxHeight()
                    .background(ttAccent())
                    .testTag(EpicProgressFillTag),
            )
        }
    }
}

/** The derived status line, used where the card has no room for a pill. */
@Composable
fun EpicStatusCaption(
    status: EpicStatus,
    modifier: Modifier = Modifier,
) {
    ttText(
        text = epicStatusLabel(status),
        style = ttTypography.captionSmall,
        color = ttLabelSecondary(),
        modifier = modifier.testTag(EpicStatusCaptionTag),
    )
}

const val EpicProgressTrackTag = "projects.epic.progress.track"
const val EpicProgressFillTag = "projects.epic.progress.fill"
const val EpicStatusCaptionTag = "projects.epic.status"

private val EPIC_PROGRESS_HEIGHT = 6.dp
