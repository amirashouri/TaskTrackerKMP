package com.tasktracker.featureprojectspresentation

import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
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
 * Bottom sheet displaying available projects for switching.
 * Tapping a project immediately invokes [onSelect] without an intermediate confirmation step.
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
        // Disable scaffold outer scroll so nested LazyColumn manages its own scroll and recycling
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
            .clickable(
                indication = null,
                interactionSource = remember { MutableInteractionSource() },
                onClick = onClick,
            )
            .padding(ttSpacing.xl)
            .testTag(ProjectPickerRowTag(project.id)),
        verticalArrangement = Arrangement.spacedBy(ttSpacing.xxs),
    ) {
        ttText(
            text = project.name,
            style = ttTypography.callout,
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

const val ProjectPickerSheetTag = "picker.sheet"
const val ProjectPickerListTag = "picker.rows"
const val ProjectPickerNewTag = "picker.new"

fun ProjectPickerRowTag(projectID: ProjectID): String = "picker.row.${projectID.stringValue}"