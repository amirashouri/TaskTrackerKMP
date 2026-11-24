package com.tasktracker.featureprojectspresentation

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.tasktracker.core.ProjectID
import com.tasktracker.designsystem.ttActionIcons
import com.tasktracker.designsystem.ttGlassButton
import com.tasktracker.designsystem.ttGlassSurface
import com.tasktracker.designsystem.ttIcon
import com.tasktracker.designsystem.ttIconSize
import com.tasktracker.designsystem.ttLabel
import com.tasktracker.designsystem.ttLabelSecondary
import com.tasktracker.designsystem.ttRadius
import com.tasktracker.designsystem.ttSpacing
import com.tasktracker.designsystem.ttSurface
import com.tasktracker.designsystem.ttText
import com.tasktracker.designsystem.ttTypography
import com.tasktracker.domain.Project
import com.tasktracker.resources.Res
import com.tasktracker.resources.epic_projects_hint
import com.tasktracker.resources.epic_projects_label
import com.tasktracker.resources.epic_projects_short
import com.tasktracker.resources.projects_empty_title
import org.jetbrains.compose.resources.stringResource

/**
 * The epic's project set, opened as an inline multi-select list rather than a sheet.
 *
 * The list expands directly under the Projects row because picking projects is part of filling
 * in the form, not a separate errand. It stays open across taps so several projects can be
 * chosen in one visit, and the trigger closes it. The two-project minimum is *stated* here and
 * enforced by the use case: a tap on a checkbox never refuses, because removing one project from
 * a two-project epic has to pass through one selected project.
 */
@Composable
fun EpicProjectDropdown(
    projects: List<Project>,
    selected: List<ProjectID>,
    onToggle: (ProjectID) -> Unit,
    modifier: Modifier = Modifier,
) {
    var expanded by remember { mutableStateOf(false) }

    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(ttSpacing.md),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            ttText(
                text = stringResource(Res.string.epic_projects_label),
                style = ttTypography.captionEmphasised,
                color = ttLabelSecondary(),
                modifier = Modifier.weight(1f),
            )
            ttGlassButton(
                label = stringResource(Res.string.epic_projects_label),
                onClick = { expanded = !expanded },
                modifier = Modifier.testTag(EpicEditorPickProjectsTag),
            )
            ttIcon(
                image = ttActionIcons.chevronDown,
                contentDescription = null,
                tint = ttLabelSecondary(),
                modifier = Modifier
                    .size(CHEVRON_SIZE)
                    .rotate(if (expanded) 180f else 0f),
            )
        }

        if (expanded) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = ttSpacing.xs)
                    .ttGlassSurface(
                        shape = RoundedCornerShape(ttRadius.control),
                        base = ttSurface(),
                    )
                    .testTag(EpicProjectPickerTag),
            ) {
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
                    modifier = Modifier.padding(
                        horizontal = ttSpacing.md,
                        vertical = ttSpacing.sm,
                    ),
                )
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
                            modifier = Modifier.padding(
                                horizontal = ttSpacing.sm,
                                vertical = ttSpacing.md,
                            ),
                        )
                    }
                    projects.forEach { project ->
                        val isSelected = project.id in selected
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .selectable(
                                    selected = isSelected,
                                    role = Role.Checkbox,
                                    onClick = { onToggle(project.id) },
                                )
                                .padding(horizontal = ttSpacing.md, vertical = ttSpacing.sm)
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
                                    color = ttLabel(),
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

const val EpicProjectPickerTag = "projects.epic.picker.projects"
const val EpicProjectPickerRowsTag = "projects.epic.picker.projects.rows"

fun EpicProjectPickerRowTag(projectID: String): String = "projects.epic.picker.projects.row.$projectID"

private val CHEVRON_SIZE = ttIconSize.close
