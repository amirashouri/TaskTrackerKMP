package com.tasktracker.featureprojectspresentation

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import com.tasktracker.designsystem.TTStoryPoints
import com.tasktracker.designsystem.ThemeScheme
import com.tasktracker.designsystem.ttAccent
import com.tasktracker.designsystem.ttActionIcons
import com.tasktracker.designsystem.ttBodyLabel
import com.tasktracker.designsystem.ttGlassIconButton
import com.tasktracker.designsystem.ttLabel
import com.tasktracker.designsystem.ttLabelSecondary
import com.tasktracker.designsystem.ttSpacing
import com.tasktracker.designsystem.ttText
import com.tasktracker.designsystem.ttTypography
import com.tasktracker.domain.SprintSummary
import com.tasktracker.domain.WorkItem
import com.tasktracker.domain.sprintSummary
import com.tasktracker.resources.Res
import com.tasktracker.resources.common_cancel
import com.tasktracker.resources.sprint_achieved
import com.tasktracker.resources.sprint_committed
import com.tasktracker.resources.sprint_summary_done
import com.tasktracker.resources.sprint_summary_empty
import com.tasktracker.resources.sprint_summary_hit
import com.tasktracker.resources.sprint_summary_incomplete
import com.tasktracker.resources.sprint_summary_miss
import com.tasktracker.resources.sprint_summary_no_goal
import com.tasktracker.resources.sprint_summary_none
import com.tasktracker.resources.sprint_summary_title
import org.jetbrains.compose.resources.stringResource

/**
 * What a finished sprint came to.
 *
 * ## Every number here is read off [SprintSummary]
 *
 * This screen takes the domain's value and renders it. It does not compare
 * [SprintSummary.achievedStoryPoints] to [Sprint.sprint]'s goal to decide whether the goal
 * was hit — [SprintSummary.didHitGoal] is that answer, computed once in `:packages:domain`,
 * and `SprintGoal.didHitGoal`'s own KDoc names exactly this as the mistake it exists to
 * prevent ("that is the case a `>` gets wrong").
 *
 * **Three outcomes, not two.** `didHitGoal` is `null` when no goal was set, and that is a
 * third state rather than a `false`: a sprint nobody set a target for has not missed it.
 * A two-way rendering would have to claim one of the two, and the claim would be invented.
 */
@Composable
fun SprintSummaryScreen(
    summary: SprintSummary,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = ttSpacing.xxl, vertical = ttSpacing.screenGutterLarge)
            .testTag(SprintSummaryScreenTag),
        verticalArrangement = Arrangement.spacedBy(ttSpacing.lg),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(ttSpacing.md),
        ) {
            ttGlassIconButton(
                image = ttActionIcons.chevronLeft,
                contentDescription = stringResource(Res.string.common_cancel),
                onClick = onBack,
                modifier = Modifier.testTag(SprintSummaryBackTag),
            )
            ttText(
                text = stringResource(Res.string.sprint_summary_title, summary.sprint.name),
                style = ttTypography.largeTitle,
                color = ttLabel(),
                modifier = Modifier.weight(1f),
            )
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(ttSpacing.md),
        ) {
            SprintTotal(
                label = stringResource(Res.string.sprint_committed),
                points = summary.committedStoryPoints,
                modifier = Modifier.weight(1f),
            )
            SprintTotal(
                label = stringResource(Res.string.sprint_achieved),
                points = summary.achievedStoryPoints,
                modifier = Modifier.weight(1f),
            )
        }
        ttText(
            // The verdict, read from the value via [sprintVerdict] — see that function for
            // why the mapping is not written inline here. `ttOnAccent` would be wrong: it
            // is the ink *for* the accent fill, and this is ink on the canvas, so the
            // accent is only used as a tint for the "hit" case.
            text = when (sprintVerdict(summary.didHitGoal)) {
                SprintVerdict.Hit -> stringResource(Res.string.sprint_summary_hit)
                SprintVerdict.Miss -> stringResource(Res.string.sprint_summary_miss)
                SprintVerdict.NoGoal -> stringResource(Res.string.sprint_summary_no_goal)
            },
            style = ttTypography.titleSmall,
            color = when (sprintVerdict(summary.didHitGoal)) {
                SprintVerdict.Hit -> ttAccent()
                else -> ttLabelSecondary()
            },
            modifier = Modifier.testTag(SprintSummaryVerdictTag),
        )
        if (summary.isEmpty) {
            ttText(
                text = stringResource(Res.string.sprint_summary_empty),
                style = ttTypography.footnote,
                color = ttBodyLabel(),
                modifier = Modifier.testTag(SprintSummaryEmptyTag),
            )
        } else {
            SprintSummarySection(
                title = stringResource(Res.string.sprint_summary_done),
                items = summary.done,
                modifier = Modifier.testTag(SprintSummaryDoneTag),
            )
            SprintSummarySection(
                title = stringResource(Res.string.sprint_summary_incomplete),
                items = summary.incomplete,
                modifier = Modifier.testTag(SprintSummaryIncompleteTag),
            )
        }
    }
}

/** One of the summary's two lists. Renders its own "none" rather than an empty column. */
@Composable
private fun SprintSummarySection(
    title: String,
    items: List<WorkItem>,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(ttSpacing.sm),
    ) {
        ttText(text = title, style = ttTypography.titleSmall, color = ttLabel())
        if (items.isEmpty()) {
            ttText(
                text = stringResource(Res.string.sprint_summary_none),
                style = ttTypography.footnote,
                color = ttLabelSecondary(),
            )
        } else {
            items.forEach { item ->
                SprintItemRow(
                    key = item.key.toString(),
                    title = item.title,
                    storyPoints = item.storyPoints,
                )
            }
        }
    }
}

/**
 * What a sprint's summary says about its goal, as a decision rather than a sentence.
 *
 * ## Why this is a function and not a `when` inside the screen
 *
 * 23.5.4's whole promise is that the hit/miss is **read from [SprintSummary.didHitGoal]** and
 * never recomputed in the view. A `when` written inline in a composable states that
 * promise in a way only a rendered frame can check — and a rendered frame can only check
 * the wording, not the *source* of the verdict. So the mapping is a pure function here and
 * the test below asserts it directly.
 *
 * It is also where the third state has to be named. `didHitGoal` is `Boolean?`, and a
 * two-way rendering has to invent an answer for the `null`: claiming "missed" reports a
 * failure nobody committed to, and claiming "met" reports a success. Neither is true, so
 * [NoGoal] is a distinct outcome with its own string.
 */
internal enum class SprintVerdict {

    /** The sprint reached or passed its goal. */
    Hit,

    /** The sprint finished short of a goal it did set. */
    Miss,

    /** The sprint set no goal, so it neither met nor missed one. */
    NoGoal,
}

/**
 * Reads the verdict out of the summary's answer.
 *
 * The input is [SprintSummary.didHitGoal] and nothing else — no `achievedStoryPoints`, no
 * `goalStoryPoints`. That is the point: a function that took the two numbers as well would
 * be free to compare them, and the `>` that gets "exactly at the goal is a miss" wrong
 * would be back. `SprintGoal.didHitGoal`'s KDoc names that as the mistake the named
 * function exists to prevent.
 */
internal fun sprintVerdict(didHitGoal: Boolean?): SprintVerdict = when (didHitGoal) {
    true -> SprintVerdict.Hit
    false -> SprintVerdict.Miss
    null -> SprintVerdict.NoGoal
}

/** One task inside the sprint: its key, its title, and its estimate. */
@Composable
private fun SprintItemRow(
    key: String,
    title: String,
    storyPoints: Int?,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = ttSpacing.xs),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(ttSpacing.md),
    ) {
        ttText(
            text = key,
            style = ttTypography.key,
            color = ttLabelSecondary(),
        )
        ttText(
            text = title,
            style = ttTypography.callout,
            color = ttLabel(),
            maxLines = SPRINT_ITEM_TITLE_MAX_LINES,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        TTStoryPoints(points = storyPoints)
    }
}

private const val SPRINT_ITEM_TITLE_MAX_LINES = 2

/** The summary screen. */
const val SprintSummaryScreenTag = "sprints.summary"
const val SprintSummaryBackTag = "sprints.summary.back"
const val SprintSummaryVerdictTag = "sprints.summary.verdict"
const val SprintSummaryEmptyTag = "sprints.summary.empty"
const val SprintSummaryDoneTag = "sprints.summary.done"
const val SprintSummaryIncompleteTag = "sprints.summary.incomplete"

// ---------------------------------------------------------------------------------------------
// Previews
// ---------------------------------------------------------------------------------------------

// Pinned schemes, not `ThemeScheme.System`, and fixtures from `ViewPreviewData` rather than
// `seedSampleDataIfNeeded` — see `ProjectsPreview`'s KDoc and 23.8.

@Preview(name = "Sprint summary", showBackground = true, widthDp = 380, heightDp = 720)
@Composable
private fun SprintSummaryScreenPreview() = ProjectsPreview(ThemeScheme.Light) {
    val sprint = ViewPreviewData.finishedSprint
    SprintSummaryScreen(
        summary = sprintSummary(sprint, ViewPreviewData.sprintWorkItems(sprint)),
        onBack = {},
    )
}

@Preview(name = "Sprint summary, empty", showBackground = true, widthDp = 380, heightDp = 420)
@Composable
private fun SprintSummaryScreenEmptyPreview() = ProjectsPreview(ThemeScheme.Dark) {
    SprintSummaryScreen(summary = sprintSummary(ViewPreviewData.finishedSprint, emptyList()), onBack = {})
}
