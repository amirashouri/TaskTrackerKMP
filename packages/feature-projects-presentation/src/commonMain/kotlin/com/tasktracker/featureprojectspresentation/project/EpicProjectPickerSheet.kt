package com.tasktracker.featureprojectspresentation

import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import com.tasktracker.core.ProjectID
import com.tasktracker.designsystem.TTBottomSheetScaffold
import com.tasktracker.designsystem.TTGlassButtonStyle
import com.tasktracker.designsystem.ttAccent
import com.tasktracker.designsystem.ttGlassButton
import com.tasktracker.designsystem.ttLabel
import com.tasktracker.designsystem.ttLabelSecondary
import com.tasktracker.designsystem.ttSpacing
import com.tasktracker.designsystem.ttText
import com.tasktracker.designsystem.ttTypography
import com.tasktracker.domain.Project
import com.tasktracker.resources.Res
import com.tasktracker.resources.common_done
import com.tasktracker.resources.epic_projects_hint
import com.tasktracker.resources.epic_projects_label
import com.tasktracker.resources.epic_projects_short
import com.tasktracker.resources.projects_empty_title
import org.jetbrains.compose.resources.stringResource

/**
 * The multi-select sheet behind an epic's project set.
 *
 * The two-project minimum is **stated** here and enforced by the use case: the button that
 * saves stays disabled until the draft satisfies it, but a tap on the checkbox never refuses,
 * because a user removing one project from a two-project epic has to pass through one selected
 * project and should not be blocked from doing so.
 */
@Composable
fun EpicProjectPickerSheet(
    projects: List<Project>,
    selected: List<ProjectID>,
    onToggle: (ProjectID) -> Unit,
    visible: Boolean,
    onDismissRequest: () -> Unit,
    onDismissed: () -> Unit,
    modifier: Modifier = Modifier,
) {
    TTBottomSheetScaffold(
        visible = visible,
        onDismissRequest = onDismissRequest,
        modifier = modifier,
        onDismissed = onDismissed,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .testTag(EpicProjectPickerTag)
                .padding(ttSpacing.formStack),
            verticalArrangement = Arrangement.spacedBy(ttSpacing.md),
        ) {
            ttText(
                text = stringResource(Res.string.epic_projects_label),
                style = ttTypography.titleSmall,
                color = ttLabel(),
            )
            ttText(
                text = stringResource(
                    if (selected.distinct().size < 2) {
                        Res.string.epic_projects_short
                    } else {
                        Res.string.epic_projects_hint
                    },
                ),
                style = ttTypography.footnote,
                color = ttLabelSecondary(),
            )
            // The sheet scaffold already scrolls its card; a second verticalScroll here is
            // measured with unbounded height and throws as soon as the picker opens, even when
            // there are no projects to list. The rows simply grow and the sheet scrolls.
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag(EpicProjectPickerRowsTag),
                verticalArrangement = Arrangement.spacedBy(ttSpacing.xs),
            ) {
                if (projects.isEmpty()) {
                    ttText(
                        text = stringResource(Res.string.projects_empty_title),
                        style = ttTypography.footnote,
                        color = ttLabelSecondary(),
                        modifier = Modifier.padding(horizontal = ttSpacing.sm, vertical = ttSpacing.md),
                    )
                }
                projects.forEach { project ->
                    val isSelected = project.id in selected
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable(
                                indication = null,
                                interactionSource = remember { MutableInteractionSource() },
                                role = Role.Checkbox,
                                onClick = { onToggle(project.id) },
                            )
                            .padding(horizontal = ttSpacing.sm, vertical = ttSpacing.md)
                            .testTag(EpicProjectPickerRowTag(project.id.stringValue)),
                        horizontalArrangement = Arrangement.spacedBy(ttSpacing.sm),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        ttText(
                            text = project.name,
                            style = ttTypography.callout,
                            color = ttLabel(),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f),
                        )
                        if (isSelected) {
                            ttText(
                                text = "\u2713",
                                style = ttTypography.callout,
                                color = ttAccent(),
                            )
                        }
                    }
                }
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(ttSpacing.sm, Alignment.End),
            ) {
                ttGlassButton(
                    label = stringResource(Res.string.common_done),
                    onClick = onDismissRequest,
                    style = TTGlassButtonStyle(prominent = true),
                    modifier = Modifier.testTag(EpicProjectPickerDoneTag),
                )
            }
        }
    }
}

const val EpicProjectPickerTag = "projects.epic.picker.projects"
const val EpicProjectPickerRowsTag = "projects.epic.picker.projects.rows"
const val EpicProjectPickerDoneTag = "projects.epic.picker.projects.done"

fun EpicProjectPickerRowTag(projectID: String): String = "projects.epic.picker.projects.row.$projectID"
