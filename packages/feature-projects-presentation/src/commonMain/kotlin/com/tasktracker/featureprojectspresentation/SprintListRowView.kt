package com.tasktracker.featureprojectspresentation

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import com.tasktracker.core.SprintID
import com.tasktracker.designsystem.ttBodyLabel
import com.tasktracker.designsystem.ttDateRange
import com.tasktracker.designsystem.ttGlassCard
import com.tasktracker.designsystem.ttLabel
import com.tasktracker.designsystem.ttLabelSecondary
import com.tasktracker.designsystem.ttRadius
import com.tasktracker.designsystem.ttSpacing
import com.tasktracker.designsystem.ttText
import com.tasktracker.designsystem.ttTypography
import com.tasktracker.resources.Res
import com.tasktracker.resources.sprint_finished
import com.tasktracker.resources.sprint_open
import com.tasktracker.resources.sprint_progress
import org.jetbrains.compose.resources.stringResource

/**
 * One sprint, as a row: name, window, progress, and whether it is still open.
 *
 * ## The progress bar is a bar and the numbers are beside it
 *
 * The bar answers "how far along, roughly" at a glance and the two numbers answer it
 * exactly. Neither is redundant with the other: a bar that runs to 100% cannot say whether
 * that is 12 of 12 or 40 of 40, and a pair of numbers cannot be scanned down a column of
 * eight sprints in the time a bar takes.
 *
 * The fill is [ttAccent] and the track is [ttSeparator] — both existing tokens, no new
 * styling primitive. The bar carries no text, so the contrast requirement that applies to
 * the numerals beside it does not apply to it: it is a shape, read by length.
 *
 * [SprintListRow.progressFraction] being `null` — a sprint with no goal — draws the track
 * and no fill, which is the honest reading: there is no progress to show, and an empty bar
 * would look like a sprint that achieved nothing.
 */
@Composable
fun SprintListRowView(
    row: SprintListRow,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(ttRadius.row))
            .ttGlassCard()
            .clickable(onClick = onClick)
            .padding(ttSpacing.xl)
            .testTag(SprintRowTag(row.sprint.id)),
        verticalArrangement = Arrangement.spacedBy(ttSpacing.md),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(ttSpacing.md),
        ) {
            ttText(
                text = row.sprint.name,
                style = ttTypography.headline,
                color = ttLabel(),
                maxLines = SPRINT_NAME_MAX_LINES,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            SprintStatusBadge(isFinished = row.sprint.isFinished)
        }
        ttText(
            text = ttDateRange(row.startDate, row.endDate),
            style = ttTypography.captionSmall,
            color = ttLabelSecondary(),
        )
        SprintProgressBar(fraction = row.progressFraction)
        ttText(
            // The two numbers rather than a percentage: `sprint_progress`'s KDoc gives the
            // reason, and it is that 5 of 12 is not 42% of anything.
            text = stringResource(
                Res.string.sprint_progress,
                row.achievedStoryPoints,
                row.sprint.goalStoryPoints,
            ),
            style = ttTypography.caption,
            color = ttBodyLabel(),
        )
    }
}

/**
 * The open/finished badge.
 *
 * A word, not a colour or a dot. "Open" and "Finished" are the two states a sprint is
 * read by, and a coloured dot is a legend the user has to learn; a word is the state. It
 * takes [ttLabelSecondary] rather than an accent because it is metadata beside the name,
 * not an action, and an accent badge would read as something to tap.
 */
@Composable
private fun SprintStatusBadge(
    isFinished: Boolean,
    modifier: Modifier = Modifier,
) {
    ttText(
        text = stringResource(
            if (isFinished) Res.string.sprint_finished else Res.string.sprint_open,
        ),
        style = ttTypography.captionSmall,
        color = ttLabelSecondary(),
        modifier = modifier.testTag(SprintStatusBadgeTag),
    )
}

/** A row's own tag, so a test means *this* sprint and not the list. */
fun SprintRowTag(sprintID: SprintID): String = "sprints.row.${sprintID.stringValue}"

const val SprintStatusBadgeTag = "sprints.row.badge"

private const val SPRINT_NAME_MAX_LINES = 2
