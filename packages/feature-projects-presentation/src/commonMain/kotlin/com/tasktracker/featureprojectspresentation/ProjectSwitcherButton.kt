package com.tasktracker.featureprojectspresentation

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.tasktracker.designsystem.ThemeScheme
import com.tasktracker.designsystem.ttAccent
import com.tasktracker.designsystem.ttGlassSurface
import com.tasktracker.designsystem.ttLabel
import com.tasktracker.designsystem.ttLabelSecondary
import com.tasktracker.designsystem.ttRadius
import com.tasktracker.designsystem.ttSpacing
import com.tasktracker.designsystem.ttText
import com.tasktracker.designsystem.ttTypography
import com.tasktracker.domain.Project

/**
 * The project name in the toolbar, with the key prefix beside it, and a chevron for the menu.
 *
 * ## Why it does not look like a button
 *
 * A toolbar button in this app is something you *press to do something*. This one names
 * where you are and opens a menu of places to go, which is a different relationship to the
 * user, and dressing it as an accent-filled button would make the board's own primary action
 * - adding a task - look like the less important of the two. It is a glass surface with a
 * hairline instead: tappable, visibly so, and not competing.
 *
 * ## Why the prefix is here at all
 *
 * Because it is the one fact about a project that is guaranteed unique, and the toolbar is
 * the one place a user looks when they cannot tell which project they are in. Two projects
 * called "Task Tracker" are a real possibility; `TT` and `GE` are not the same string.
 */
@Composable
fun ProjectSwitcherButton(
    project: Project?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val shape = RoundedCornerShape(ttRadius.control)

    Row(
        modifier = modifier
            .clip(shape)
            .ttGlassSurface(shape = shape)
            .clickable(onClick = onClick)
            .padding(horizontal = ttSpacing.lg, vertical = ttSpacing.sm)
            .testTag(ProjectSwitcherTag),
        horizontalArrangement = Arrangement.spacedBy(ttSpacing.sm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        project?.let {
            SwitcherMonogram(keyPrefix = it.keyPrefix)
        }
        Column(modifier = Modifier.weight(1f, fill = false)) {
            ttText(
                text = project?.name ?: noProjectLabel,
                style = ttTypography.callout,
                color = if (project == null) ttLabelSecondary() else ttLabel(),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        ttText(
            text = CHEVRON,
            style = ttTypography.footnote,
            color = ttLabelSecondary(),
            modifier = Modifier.testTag(ProjectSwitcherChevronTag),
        )
    }
}

@Composable
private fun SwitcherMonogram(
    keyPrefix: String,
    modifier: Modifier = Modifier,
) {
    val shape = CircleShape
    Column(
        modifier = modifier
            .size(switcherMonogramSize)
            .clip(shape)
            .background(ttAccent().copy(alpha = MONOGRAM_FILL_ALPHA))
            .border(1.dp, ttAccent().copy(alpha = MONOGRAM_RIM_ALPHA), shape),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        ttText(text = keyPrefix, style = ttTypography.captionEmphasised, color = ttLabel())
    }
}

@Preview
@Composable
private fun ProjectSwitcherButtonPreview() = ProjectsPreview(ThemeScheme.Dark) {
    Column(verticalArrangement = Arrangement.spacedBy(ttSpacing.sm)) {
        ProjectSwitcherButton(project = ViewPreviewData.seededProject, onClick = {})
    }
}

@Preview
@Composable
private fun ProjectSwitcherButtonEmptyPreview() = ProjectsPreview(ThemeScheme.Light) {
    ProjectSwitcherButton(project = null, onClick = {})
}

const val ProjectSwitcherTag = "board.switcher"
const val ProjectSwitcherChevronTag = "board.switcher.chevron"

/**
 * What the switcher says when no project is chosen.
 *
 * A hardcoded literal rather than a resource, and it is the only one in this module. The
 * reason is that this is not a label - it is the absence of one, the state before the
 * picker has run - and the strings file is for words the app chose to say. `ProjectsListModel`
 * is where a real no-project state is decided, and it decides it with a project list that
 * can be empty; the switcher is downstream of that and just has to not be blank.
 */
private const val noProjectLabel = "No project"

/** U+2303, not a Material icon: this module has no icon dependency. */
private const val CHEVRON = "⌄"

private val switcherMonogramSize = 22.dp
private const val MONOGRAM_FILL_ALPHA = 0.18f
private const val MONOGRAM_RIM_ALPHA = 0.36f
