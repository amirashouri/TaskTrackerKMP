package com.tasktracker.featureprojectspresentation

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import com.tasktracker.core.ProjectID
import com.tasktracker.designsystem.TTBottomSheetScaffold
import com.tasktracker.designsystem.ThemeScheme
import com.tasktracker.designsystem.ttGlassButton
import com.tasktracker.designsystem.ttGlassCard
import com.tasktracker.designsystem.ttLabel
import com.tasktracker.designsystem.ttLabelSecondary
import com.tasktracker.designsystem.ttSpacing
import com.tasktracker.designsystem.ttText
import com.tasktracker.designsystem.ttTypography
import com.tasktracker.resources.Res
import com.tasktracker.resources.picker_empty
import com.tasktracker.resources.picker_title
import com.tasktracker.resources.project_new
import com.tasktracker.resources.projects_empty_body
import org.jetbrains.compose.resources.stringResource

/**
 * The list of projects to switch to, drawn over whatever opened it.
 *
 * ## Why it never writes
 *
 * [ProjectPickerModel] is a reader. Choosing a project is a navigation decision, not a
 * change to any project, so this sheet has no save and no confirm - a row is tapped and the
 * sheet is gone. The alternative, a picker with an OK button, asks the user to approve a
 * choice they have already made by tapping the row, and gives them a second chance to leave
 * the app in the state they were in before.
 *
 * The exception is [onCreateProject], which is a *different* action and is drawn as a
 * separate row at the end rather than as an option among the projects. A "+" that is
 * visually a project invites a user to tap it, discover it is not one, and tap again.
 *
 * ## Why the frame comes from [TTBottomSheetScaffold]
 *
 * This used to draw its own surface — a `ttGlassSurface` with no `base`, inside a hand-rolled
 * scrim — while the project and task editors took the shared scaffold. That put a translucent,
 * accent-tinted panel at 0.3025 alpha behind a list of project names, which was reported as
 * the sheet being too see-through. It was not a difference in the design system's intent; it
 * was this file opting out of it. The scaffold owns the frame now, so there is no second
 * spelling of "a modal sheet" left to drift.
 */
@Composable
fun ProjectPickerSheet(
    projects: List<PickableProject>,
    selectedProjectID: ProjectID?,
    onSelect: (ProjectID?) -> Unit,
    modifier: Modifier = Modifier,
    onCreateProject: (() -> Unit)? = null,
    visible: Boolean = true,
    onDismissRequest: () -> Unit = {},
    onDismissed: () -> Unit = {},
) {
    TTBottomSheetScaffold(
        visible = visible,
        onDismissRequest = onDismissRequest,
        modifier = modifier,
        onDismissed = onDismissed,
        // The list below is a `LazyColumn` and stays one. A project list is unbounded in
        // principle and long in practice, so it should not hold every row's composition the
        // way a `Column` in the scaffold's scroll would.
        scrollable = false,
    ) {
        ProjectPickerContent(
            projects = projects,
            selectedProjectID = selectedProjectID,
            onSelect = onSelect,
            onCreateProject = onCreateProject,
        )
    }
}

/**
 * The picker's body, and only its body.
 *
 * No surface and no padding here: the scaffold draws the frame and pads it at `xxl`, the same
 * arrangement the project and task editors get. Padding this as well is what would have kept
 * the sheets visually different after the surface was unified.
 */
@Composable
private fun ProjectPickerContent(
    projects: List<PickableProject>,
    selectedProjectID: ProjectID?,
    onSelect: (ProjectID?) -> Unit,
    onCreateProject: (() -> Unit)?,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .testTag(ProjectPickerSheetTag),
        verticalArrangement = Arrangement.spacedBy(ttSpacing.lg),
    ) {
        ttText(
            text = stringResource(Res.string.picker_title),
            style = ttTypography.title,
            color = ttLabel(),
        )
        if (projects.isEmpty()) {
            ProjectsEmptyState(
                title = stringResource(Res.string.picker_empty),
                body = stringResource(Res.string.projects_empty_body),
            )
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxWidth().weight(1f, fill = false).testTag(ProjectPickerListTag),
                verticalArrangement = Arrangement.spacedBy(ttSpacing.sm),
            ) {
                items(projects, key = { it.id.stringValue }) { project ->
                    PickerRow(
                        project = project,
                        isSelected = project.id == selectedProjectID,
                        onClick = { onSelect(project.id) },
                    )
                }
            }
        }
        onCreateProject?.let { create ->
            ttGlassButton(
                label = stringResource(Res.string.project_new),
                onClick = create,
                modifier = Modifier.fillMaxWidth().testTag(ProjectPickerNewTag),
            )
        }
    }
}

@Composable
private fun PickerRow(
    project: PickableProject,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .ttGlassCard()
            .clickable(onClick = onClick)
            .padding(ttSpacing.xl)
            .testTag(ProjectPickerRowTag(project.id)),
        verticalArrangement = Arrangement.spacedBy(ttSpacing.xxs),
    ) {
        ttText(
            text = project.name,
            style = ttTypography.callout,
            // The selection is carried by weight and the checkmark, not by colour alone.
            color = if (isSelected) ttLabel() else ttLabelSecondary(),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        ttText(
            text = project.keyPrefix,
            style = ttTypography.key,
            color = ttLabelSecondary(),
        )
    }
}

@Preview
@Composable
private fun ProjectPickerSheetPreview() = ProjectsPreview(ThemeScheme.Dark) {
    ProjectPickerSheet(
        projects = ViewPreviewData.pickable(),
        selectedProjectID = ViewPreviewData.seededProject.id,
        onSelect = {},
        onCreateProject = {},
    )
}

@Preview
@Composable
private fun ProjectPickerSheetEmptyPreview() = ProjectsPreview(ThemeScheme.Light) {
    ProjectPickerSheet(
        projects = emptyList(),
        selectedProjectID = null,
        onSelect = {},
    )
}

const val ProjectPickerSheetTag = "picker.sheet"
const val ProjectPickerListTag = "picker.rows"
const val ProjectPickerNewTag = "picker.new"

/** Suffixed with the id, because two projects can share a name and a key prefix is not shown here. */
fun ProjectPickerRowTag(projectID: ProjectID): String = "picker.row.${projectID.stringValue}"
