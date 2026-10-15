package com.tasktracker.featureprojectspresentation

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import com.tasktracker.designsystem.ThemeScheme
import com.tasktracker.designsystem.ttBodyLabel
import com.tasktracker.designsystem.ttLabel
import com.tasktracker.designsystem.ttSpacing
import com.tasktracker.designsystem.ttText
import com.tasktracker.designsystem.ttTypography
import com.tasktracker.resources.Res
import com.tasktracker.resources.sprint_detail_placeholder_body
import com.tasktracker.resources.sprint_detail_placeholder_title
import org.jetbrains.compose.resources.stringResource

/**
 * The Sprints tab's second pane on a wide window, before a sprint has been chosen.
 *
 * ## Why a placeholder rather than the list again
 *
 * The two-pane arrangement draws the tab root in the left pane and the pushed route in the
 * right. The Sprints tab's *root* is itself a route (`AppRoute.SprintList`), so with nothing
 * selected the right pane's route is the list — and rendering it there drew the sprint list
 * twice, one copy beside the other. A pane that is empty because nothing is selected needs a
 * sentence saying so, not a second answer to the question the left pane already answers.
 *
 * ## Why this is not the list's own empty state
 *
 * [ProjectsEmptyState] means "there are no sprints"; this means "there are sprints, none
 * chosen". They are the same layout and different claims, and one sentence for both would
 * tell a user with three sprints that they have none. It is also `internal` to this module,
 * so the shell cannot borrow it anyway.
 */
@Composable
fun SprintDetailPlaceholder(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            modifier = Modifier
                .padding(ttSpacing.xxl)
                .testTag(SprintDetailPlaceholderTag),
            verticalArrangement = Arrangement.spacedBy(ttSpacing.sm),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            ttText(
                text = stringResource(Res.string.sprint_detail_placeholder_title),
                style = ttTypography.titleSmall,
                color = ttLabel(),
                textAlign = TextAlign.Center,
            )
            ttText(
                // `ttBodyLabel`, not `ttLabelSecondary()`, for the reason `ProjectsEmptyState`
                // states: this is the only text on the pane and carries its whole meaning, and
                // the secondary label falls under the 4.5:1 bar on the light canvas.
                text = stringResource(Res.string.sprint_detail_placeholder_body),
                style = ttTypography.subheadline,
                color = ttBodyLabel(),
                textAlign = TextAlign.Center,
            )
        }
    }
}

@Preview(name = "Sprint detail placeholder", showBackground = true, widthDp = 380, heightDp = 400)
@Composable
private fun SprintDetailPlaceholderPreview() = ProjectsPreview(ThemeScheme.Light) {
    SprintDetailPlaceholder()
}

const val SprintDetailPlaceholderTag = "sprints.detail.placeholder"
