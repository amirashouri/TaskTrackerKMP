package com.tasktracker.sharedui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.tasktracker.core.ProjectID
import com.tasktracker.designsystem.ttAccent
import com.tasktracker.designsystem.ttGlass
import com.tasktracker.designsystem.ttGlassSurface
import com.tasktracker.designsystem.ttIcon
import com.tasktracker.designsystem.ttIconSize
import com.tasktracker.designsystem.ttRadius
import com.tasktracker.designsystem.ttSelectionLabelInk
import com.tasktracker.designsystem.ttSelectionWash
import com.tasktracker.designsystem.ttSpacing
import com.tasktracker.designsystem.ttText
import com.tasktracker.designsystem.ttTypography
import com.tasktracker.featureprojectspresentation.PickableProject
import com.tasktracker.featureprojectspresentation.ProjectPickerModel
import com.tasktracker.featureprojectsdomain.ProjectRepositoryPort
import com.tasktracker.routing.AppTab
import kotlinx.coroutines.CoroutineScope

@Composable
internal fun NavigationSidebar(
    selected: AppTab,
    onSelect: (AppTab) -> Unit,
    coroutineScope: CoroutineScope,
    repository: ProjectRepositoryPort,
    activeProjectID: ProjectID?,
    onOpenProject: (ProjectID) -> Unit,
    modifier: Modifier = Modifier,
) {
    // The same two inks the rail's cells use, for the same reason: both put a label on glass with
    // an accent wash under the selected one. `TabLensContrastTest` holds the derivation and the
    // measured contrast on both rasterisers.
    val selectedInk = ttSelectionLabelInk(selected = true)
    val inactiveInk = ttSelectionLabelInk(selected = false)
    val accent = ttAccent()

    val projectPicker = remember(repository) { ProjectPickerModel(repository) }
    DisposableEffect(projectPicker) {
        projectPicker.observe(coroutineScope)
        onDispose { projectPicker.stopObserving() }
    }

    Box(
        modifier = modifier
            .padding(
                start = ttSpacing.tabBarEdgeInset,
                top = ttSpacing.tabBarEdgeInset,
                bottom = ttSpacing.tabBarEdgeInset,
            )
            .testTag(SidebarTag),
    ) {
        Column(
            modifier = Modifier
                .width(ShellSidebarWidth)
                .fillMaxHeight()
                .shadow(
                    elevation = ttGlass.tabBarShadowElevation,
                    shape = RoundedCornerShape(ttRadius.surface),
                    ambientColor = accent.copy(alpha = ttGlass.TAB_BAR_SHADOW_ALPHA),
                    spotColor = accent.copy(alpha = ttGlass.TAB_BAR_SHADOW_ALPHA),
                )
                .ttGlassSurface(shape = RoundedCornerShape(ttRadius.surface))
                .padding(ttSpacing.hairlineInset)
                .padding(horizontal = ttSpacing.md, vertical = ttSpacing.md),
            verticalArrangement = Arrangement.spacedBy(ttSpacing.xxs),
        ) {
            // The index, at the head. It is the one destination that is not scoped to a project,
            // so it opens the panel rather than sitting at the bottom with the work destinations.
            // A heading here instead of a row was 8.1's shape, and it composed "Projects" twice —
            // once as inert text, once as the row below — while hanging the same test tag on
            // three nodes.
            SidebarDestinationRow(
                label = tabTitle(AppTab.Projects),
                icon = tabIcon(AppTab.Projects),
                isSelected = selected == AppTab.Projects,
                selectedInk = selectedInk,
                inactiveInk = inactiveInk,
                onClick = { onSelect(AppTab.Projects) },
                modifier = Modifier.testTag(SidebarButtonTag(AppTab.Projects)),
            )

            if (projectPicker.projects.isNotEmpty()) {
                LazyColumn(
                    modifier = Modifier
                        .weight(1f, fill = false)
                        .testTag(SidebarProjectListTag),
                    contentPadding = PaddingValues(vertical = ttSpacing.xs),
                    verticalArrangement = Arrangement.spacedBy(ttSpacing.xs),
                ) {
                    items(projectPicker.projects, key = { it.id.stringValue }) { project ->
                        SidebarProjectRow(
                            project = project,
                            isActive = activeProjectID == project.id,
                            onClick = { onOpenProject(project.id) },
                            selectedInk = selectedInk,
                            inactiveInk = inactiveInk,
                        )
                    }
                }
            }

            Spacer(Modifier.height(ttSpacing.xl))

            Column(
                modifier = Modifier.padding(start = ttSpacing.lg),
                verticalArrangement = Arrangement.spacedBy(ttSpacing.xxs),
            ) {
                for (tab in AppTab.entries) {
                    if (!tab.isProjectScoped) continue
                    SidebarDestinationRow(
                        label = tabTitle(tab),
                        icon = tabIcon(tab),
                        isSelected = tab == selected,
                        selectedInk = selectedInk,
                        inactiveInk = inactiveInk,
                        onClick = { onSelect(tab) },
                        modifier = Modifier.testTag(SidebarButtonTag(tab)),
                    )
                }
            }

            Spacer(Modifier.height(ttSpacing.xl))

            SidebarDestinationRow(
                label = tabTitle(AppTab.Settings),
                icon = tabIcon(AppTab.Settings),
                isSelected = selected == AppTab.Settings,
                selectedInk = selectedInk,
                inactiveInk = inactiveInk,
                onClick = { onSelect(AppTab.Settings) },
                modifier = Modifier.testTag(SidebarButtonTag(AppTab.Settings)),
            )
        }
    }
}

@Composable
private fun SidebarDestinationRow(
    label: String,
    icon: ImageVector,
    isSelected: Boolean,
    selectedInk: Color,
    inactiveInk: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(ttRadius.control))
            .ttSelectionWash(selected = isSelected)
            .clickable(
                indication = null,
                interactionSource = remember { MutableInteractionSource() },
                role = Role.Tab,
                onClick = onClick,
            )
            .semantics { selected = isSelected }
            .padding(horizontal = ttSpacing.md, vertical = ttSpacing.sm),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(ttSpacing.md),
    ) {
        ttIcon(
            image = icon,
            contentDescription = null,
            tint = if (isSelected) selectedInk else inactiveInk,
            modifier = Modifier.size(ttIconSize.tab),
        )
        ttText(
            text = label,
            style = ttTypography.callout,
            color = if (isSelected) selectedInk else inactiveInk,
            maxLines = 1,
        )
    }
}

@Composable
private fun SidebarProjectRow(
    project: PickableProject,
    isActive: Boolean,
    onClick: () -> Unit,
    selectedInk: Color,
    inactiveInk: Color,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .testTag(SidebarProjectTag(project.id))
            .clip(RoundedCornerShape(ttRadius.control))
            .clickable(
                indication = null,
                interactionSource = remember { MutableInteractionSource() },
                role = Role.Button,
                onClick = onClick,
            )
            .semantics { selected = isActive }
            .padding(horizontal = ttSpacing.md, vertical = ttSpacing.sm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier.size(ttSpacing.sm),
            contentAlignment = Alignment.Center,
        ) {
            if (isActive) {
                Box(
                    modifier = Modifier
                        .size(ttSpacing.sm)
                        .clip(CircleShape)
                        .background(selectedInk),
                )
            }
        }
        ttText(
            text = project.name,
            style = ttTypography.subheadline,
            color = if (isActive) selectedInk else inactiveInk,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier
                .weight(1f)
                .padding(start = ttSpacing.md),
        )
        ttText(
            text = project.keyPrefix,
            style = ttTypography.key,
            color = if (isActive) selectedInk else inactiveInk,
            maxLines = 1,
            modifier = Modifier.padding(start = ttSpacing.md),
        )
    }
}

/**
 * The sidebar as a whole.
 *
 * Distinct from [NavigationRailTag] and [TabBarTag] for the reason [NavigationRailTag] gives:
 * exactly one of the three is composed at any width, and a test that asked for one shared tag
 * could not tell which chrome it was looking at.
 */
const val SidebarTag = "shell.sidebar"

/**
 * One destination's row in the sidebar, so a test taps by name rather than by position.
 *
 * Prefixed `shell.sidebar.` rather than reused from [NavigationRailButtonTag] or [TabButtonTag]:
 * a mixed-up query should find nothing rather than drive the wrong chrome.
 */
fun SidebarButtonTag(tab: AppTab): String = "shell.sidebar.${tab.wireValue}"

/**
 * The project switcher's scroll container.
 *
 * Separate from [SidebarProjectTag] because two different questions are asked of it: a row is
 * tapped by project id, and the *list* is the node a scenario scrolls to its last project to
 * check that the destinations did not move.
 *
 * Deliberately not `shell.sidebar.projects`: that is [SidebarButtonTag]'s tag for the Projects
 * destination, and sharing it made every query for either of them match two nodes.
 */
const val SidebarProjectListTag = "shell.sidebar.projectList"

/**
 * One project row's tag in the sidebar, so a test taps by project id rather than by position.
 *
 * Prefixed `shell.sidebar.project.` rather than reused from any other tag: a mixed-up query
 * should find nothing rather than drive the wrong chrome.
 */
fun SidebarProjectTag(projectID: ProjectID): String = "shell.sidebar.project.${projectID.stringValue}"
