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

/**
 * The very wide shell's chrome: a labelled sidebar down the leading edge.
 *
 * ## The rail's argument, taken one step further
 *
 * [NavigationRail] is correct for the band above [ShellExpandedMinimumWidth], where the width is
 * being spent on a second pane and a ~100dp column of glyphs is the right amount of chrome. At
 * [ShellSidebarMinimumWidth] that stops being true: the rail is still as wide as its widest
 * caption, so it cannot say more than five words, and its cells divide the window height between
 * them — 146dp each in a 736dp window. The sidebar is what that space is for: the same five
 * destinations with their labels readable, and the project switcher that every project-scoped
 * tab needs.
 *
 * ## One stack, ordered by scope
 *
 * The panel is a single top-aligned column: the **Projects** index, the project list it opens,
 * the three views of the active project, then **Settings**, with the remaining glass empty below
 * them. The order is the hierarchy — the four work destinations sit next to the project context
 * they act on, and only the one app-level destination comes last.
 *
 * It used to be `heading → list(weight(1f)) → five destinations`, and that was wrong for a
 * reason worth recording: the weight was the only flexible child, so the *primary* navigation
 * was pinned to the bottom edge by accident and separated from the project list by however much
 * space a short list left. A desktop sidebar's bottom is for secondary items, not for the four
 * destinations a user taps all day. Here the list is the only flexible child again, but
 * `fill = false` makes it wrap when it is short, so two projects no longer open a 400dp void
 * between the list and the rows that act on it — and the empty glass ends up after the last row,
 * where nothing is being separated.
 *
 * ## Two selection signals, because there are two states
 *
 * "Which view am I on" and "which project am I in" are different questions, and drawing both
 * with [ttSelectionWash] made two rows look selected for unrelated reasons. The wash now marks
 * the current destination only; the active project is marked by the accent glyph and the
 * selected label ink, and carries `selected` semantics for accessibility. The indent on
 * Board/Backlog/Sprints is the third signal: they are children of the project above them.
 *
 * ## Why the panel fills the height
 *
 * The rail's height is owned by its weighted cells. Here the panel asks for the height and its
 * rows are content-sized, because the glass is a floating surface down the window's leading edge
 * and the empty room belongs below the last row rather than as a band between two groups. The
 * one flexible child is the project list, and `weight(1f, fill = false)` is load-bearing twice:
 * the weight stops twenty projects from measuring the list unbounded and pushing the destinations
 * off the panel, and `fill = false` lets a short list take only the height it needs.
 *
 * ## Why the tag is on the wrapper
 *
 * The same call [NavigationRail] makes: the wrapper carries the edge inset, and [leadingChromeInset]
 * is read from *this* node's size, so the tag and the measurement have to agree about what is
 * being measured — the chrome plus the room it takes, because that is the space the content has
 * to start after.
 */
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

            // The switcher: the projects the three rows below act on.
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
            // If no projects, nothing is rendered here; the five destinations are still there.

            // A break, not a divider: the list above is context and the rows below act on it.
            // `xl` rather than a small step, because a row already carries 6dp of its own
            // vertical padding: the edge-to-edge distance across a group has to be roughly
            // twice the distance between two rows inside one, or the two groups read as a
            // single list. That is exactly what the first pass did with `sm`.
            Spacer(Modifier.height(ttSpacing.xl))

            // The active project's views, indented under the list that chose it. Order comes from
            // `AppTab.entries` (Board, Backlog, Sprints) so the bar, the rail and the sidebar all
            // keep the same left-to-right/down-the-list order.
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

            // The only app-level destination. Last in the stack rather than glued to the floor:
            // it is the one row that is not part of the project's work, and the empty glass after
            // it separates nothing that belongs together.
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

/**
 * One destination in the sidebar: glyph beside label, on the selection wash when it is current.
 *
 * The rail's [RailCell] is the same idea on its side, and the differences are deliberate: this
 * one is a row rather than a column, and it selects with [ttSelectionWash] rather than with a
 * travelling lens. `Role.Tab` and the `selected` semantics are the rail's, unchanged — the label
 * carries the state because a tint and a translucent wash are not states.
 */
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
            // The wash is clipped to the row's own shape so the accent cannot square off the
            // panel's rounded interior — the same defect `TabLensCornerTest` found on the bar,
            // caught here by construction rather than by a pixel.
            .clip(RoundedCornerShape(ttRadius.control))
            .ttSelectionWash(selected = isSelected)
            .clickable(
                // No indication, for the rail's reason and with the rail's measurement: the
                // default one paints the pressed cell as a rectangle the cell has no clip to
                // bound, and on glass this light it darkens the cell more than twice what the
                // selection wash contributes. The wash is what confirms the selection.
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
            // `callout` rather than the rail's `caption`: this label sits beside its glyph on a
            // 280dp panel, not under it in a ~100dp column, so it is read as body copy rather
            // than as a caption.
            style = ttTypography.callout,
            color = if (isSelected) selectedInk else inactiveInk,
            maxLines = 1,
        )
    }
}

/**
 * One project row in the sidebar project switcher: name, then the project's key prefix.
 *
 * ## Why the row is text, not a glyph and a label
 *
 * The first pass drew the same `ttTabIcons.projects` glyph on every project at the destination
 * rows' own size and type, so two projects read as `Projects, Projects` — duplicate navigation
 * rather than a list of records. Nothing about a project is carried by that glyph; every row
 * showed the same one. The key prefix is carried by the data and is the identifier the rest of
 * the app already uses (`AP` on `AP-12`), so it is the leading edge of recognition rather than
 * decoration.
 *
 * It trails the name rather than leading it because a prefix is 2–10 characters
 * (`ProjectRules.KEY_PREFIX_LENGTH`): a fixed leading column has to be sized for ten, which
 * leaves a 280dp panel with a ~66dp gutter in the common two-character case. Trailing, the key
 * takes only its own width and the *name* holds the aligned left edge, which is the column a
 * reader actually scans.
 *
 * The name is one type step down from a destination ([ttTypography.subheadline] against
 * `callout`), which is the hierarchy: destinations are navigation, project rows are the
 * collection that navigation acts on. The active row is the mark in the reserved leading slot,
 * with both labels in the selected ink — a shape as well as a colour, which the wash alone was
 * not.
 *
 * ## Why this is not a second kind of selected row
 *
 * Being *in* a project and being *on* a view are different states, so they get different
 * signals: the active project takes the marker dot and the selected label ink, and the current
 * destination keeps the wash. Drawing the wash on both is what made the panel look like two
 * unrelated lists with two unrelated selections. `selected` semantics still carries the project
 * state for accessibility, because a tint is not a state.
 *
 * ## Why the role is a button
 *
 * The row switches context and then lands on Board; it does not become the current `AppTab`.
 * `Role.Tab` described it wrongly — [SidebarDestinationRow] is the row that is a tab.
 */
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
        // The marker's slot is reserved on every row, so the names share one leading edge
        // whether the row is active or not; only the current project fills it. A dot that
        // appeared and disappeared would shift every name one slot to the left.
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
            // Fills the leftover width so the key sits on the trailing edge and a long name
            // ellipsises instead of pushing the identifier out of the panel.
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
