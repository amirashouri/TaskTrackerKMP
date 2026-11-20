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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.dp
import com.tasktracker.core.ProjectID
import com.tasktracker.designsystem.AccentPalette
import com.tasktracker.designsystem.ThemeScheme
import com.tasktracker.designsystem.ttAccent
import com.tasktracker.designsystem.ttGlass
import com.tasktracker.designsystem.TTGlassSurface
import com.tasktracker.designsystem.ttGlassLens
import com.tasktracker.designsystem.ttGlassLensAlpha
import com.tasktracker.designsystem.ttIcon
import com.tasktracker.designsystem.ttIconSize
import com.tasktracker.designsystem.ttLensPlacement
import com.tasktracker.designsystem.ttRadius
import com.tasktracker.designsystem.ttSelectionLabelInk
import com.tasktracker.designsystem.ttSpacing
import com.tasktracker.designsystem.ttSurface
import com.tasktracker.designsystem.ttText
import com.tasktracker.designsystem.ttTypography
import com.tasktracker.featureprojectspresentation.PickableProject
import com.tasktracker.featureprojectspresentation.ProjectPickerModel
import com.tasktracker.featureprojectsdomain.ProjectRepositoryPort
import com.tasktracker.featuresettingspresentation.ThemeControlsPanel
import com.tasktracker.routing.AppTab
import kotlinx.coroutines.CoroutineScope
import kotlin.math.roundToInt

@Composable
internal fun NavigationSidebar(
    selected: AppTab,
    onSelect: (AppTab) -> Unit,
    coroutineScope: CoroutineScope,
    repository: ProjectRepositoryPort,
    activeProjectID: ProjectID?,
    onOpenProject: (ProjectID) -> Unit,
    themeScheme: ThemeScheme,
    accentPalette: AccentPalette,
    glassIntensity: Float,
    onThemeChange: (ThemeScheme) -> Unit,
    onAccentPaletteChange: (AccentPalette) -> Unit,
    onGlassIntensityPreview: (Float) -> Unit,
    onGlassIntensityCommit: (Float) -> Unit,
    modifier: Modifier = Modifier,
) {
    // The same two inks the rail's cells use, for the same reason: both put a label on glass with
    // the same selection lens under it. `TabLensContrastTest` holds the derivation and the
    // measured contrast on both rasterisers.
    val selectedInk = ttSelectionLabelInk(selected = true)
    val inactiveInk = ttSelectionLabelInk(selected = false)
    val accent = ttAccent()

    val projectPicker = remember(repository) { ProjectPickerModel(repository) }
    DisposableEffect(projectPicker) {
        projectPicker.observe(coroutineScope)
        onDispose { projectPicker.stopObserving() }
    }

    // The travelling lens needs two measurements and neither can be derived: where each
    // destination row sits inside the panel, and the panel itself to measure them against.
    //
    // A row's place is not a formula. The project list above the work destinations wraps to its
    // projects, so a lens positioned from an index would sit on the row above whatever was
    // selected. The space below Settings belongs to a spacer, and the theme panel is a measured
    // neighbour pinned underneath it; neither is a fixed offset from the panel's top.
    //
    // The rows hand over their `LayoutCoordinates`, but the lens reads immutable rectangles.
    // That distinction is load-bearing: Compose reuses the same `LayoutCoordinates` object for a
    // row across layout passes, so writing it into a state map again when only its *position*
    // moved is a no-op — the map still compares equal and no recomposition is scheduled. The
    // project list populating after the first pass moved every row down 31dp without the lens
    // following; a round trip through the rail left it parked on the pre-population rectangle.
    // Storing an IntRect makes the geometry change a real state change.
    val density = LocalDensity.current
    val rowCoordinates = remember { mutableStateMapOf<AppTab, LayoutCoordinates>() }
    val rowRects = remember { mutableStateMapOf<AppTab, IntRect>() }
    var panelCoordinates by remember { mutableStateOf<LayoutCoordinates?>(null) }
    val panel = panelCoordinates

    fun refreshRowRects() {
        val measuredPanel = panelCoordinates ?: return
        rowCoordinates.forEach { (tab, row) ->
            val topLeft = measuredPanel.localPositionOf(row, Offset.Zero)
            val left = topLeft.x.roundToInt()
            val top = topLeft.y.roundToInt()
            rowRects[tab] = IntRect(
                left = left,
                top = top,
                right = left + row.size.width,
                bottom = top + row.size.height,
            )
        }
    }

    val lens = ttLensPlacement(rowRects, selected, density)
    val lensShape = RoundedCornerShape(ttRadius.control)
    // `BoxWithConstraints` cannot bound the panel here: next to a weighted spacer it is measured
    // with an infinite main-axis constraint, so `maxHeight * fraction` would be infinity and the
    // panel would grow until it pushed the destinations off a short window. The panel's own
    // measured height is the stable bound, and arrives on the same frame that places the lens.
    val panelHeight = panel?.let { with(density) { it.size.height.toDp() } } ?: 0.dp

    Box(
        modifier = modifier
            .padding(
                start = ttSpacing.tabBarEdgeInset,
                top = ttSpacing.tabBarEdgeInset,
                bottom = ttSpacing.tabBarEdgeInset,
            )
            .testTag(SidebarTag),
    ) {
        TTGlassSurface(
            shape = RoundedCornerShape(ttRadius.surface),
            thickness = ttGlass.GlassThickness.THIN,
            base = ttSurface().copy(alpha = 1f - ttGlassLensAlpha()),
            // The shadow and the glass are the *same* node, in that order, and the content inset is
            // applied inside the panel rather than before both.
            //
            // Where the inset sits is not cosmetic. Both of these are draw modifiers on one node,
            // so anything laid out between them makes them different rectangles: the shadow was
            // cast by a panel 16dp larger than the glass, its unblurred middle was left uncovered
            // on the left, top and bottom, and the band that showed through read as a smear of
            // colour hugging a panel that otherwise floats. The tab bar has always cast its shadow
            // from the very node the glass paints, which is why only this chrome had the band.
            // `ttSpacing.hairlineInset` keeps the content clear of the rim; it belongs to the
            // content, not to the silhouette.
            modifier = Modifier
                .width(ShellSidebarWidth)
                .fillMaxHeight()
                .shadow(
                    elevation = ttGlass.tabBarShadowElevation,
                    shape = RoundedCornerShape(ttRadius.surface),
                    ambientColor = accent.copy(alpha = ttGlass.TAB_BAR_SHADOW_ALPHA),
                    spotColor = accent.copy(alpha = ttGlass.TAB_BAR_SHADOW_ALPHA),
                ),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .onGloballyPositioned { coordinates ->
                        panelCoordinates = coordinates
                        refreshRowRects()
                    },
            ) {
                // Drawn before the rows and behind them, which is the bar's arrangement: the lens
                // is a wash and a rim under the content it marks, not a chip on top of it. It is
                // only composed once the row it marks has been measured — a lens born at the
                // origin would slide in from the panel's top-left corner on the first frame.
                if (selected in rowRects) {
                    Box(
                        modifier = Modifier
                            .size(lens.width, lens.height)
                            .offset(x = lens.x, y = lens.y)
                            // The lens is addressable for the same reason the rail's is: a lens is
                            // positioned by arithmetic that nothing else re-measures, so a test
                            // that compares it with the row it marks is the only thing that can
                            // see it land somewhere else.
                            .testTag(SidebarLensTag)
                            .ttGlassLens(lensShape, velocityX = lens.velocityX),
                    )
                }

                // Two children under SpaceBetween: the destination stack at the top and the theme
                // panel at the floor. A spacer with a weight would have shared the leftover with
                // the project list's own `weight(1f, fill = false)`; because that list wraps to
                // its projects, its unused share stayed unused and the panel floated hundreds of
                // dp above the bottom. SpaceBetween gives the whole remainder to the gap between
                // the two children and leaves the stack's internal arrangement untouched.
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(ttSpacing.hairlineInset)
                        .padding(horizontal = ttSpacing.md, vertical = ttSpacing.md),
                    verticalArrangement = Arrangement.SpaceBetween,
                ) {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(ttSpacing.xxs),
                    ) {
                        // The index, at the head. It is the one destination that is not scoped to a
                        // project, so it opens the panel rather than sitting at the bottom with the
                        // work destinations. A heading here instead of a row was 8.1's shape, and it
                        // composed "Projects" twice — once as inert text, once as the row below —
                        // while hanging the same test tag on three nodes.
                        SidebarDestinationRow(
                            label = tabTitle(AppTab.Projects),
                            icon = tabIcon(AppTab.Projects),
                            isSelected = selected == AppTab.Projects,
                            selectedInk = selectedInk,
                            inactiveInk = inactiveInk,
                            onClick = { onSelect(AppTab.Projects) },
                            modifier = Modifier
                                .testTag(SidebarButtonTag(AppTab.Projects))
                                .onGloballyPositioned { rowCoordinates[AppTab.Projects] = it },
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
                                    modifier = Modifier
                                        .testTag(SidebarButtonTag(tab))
                                        .onGloballyPositioned { coordinates ->
                                            rowCoordinates[tab] = coordinates
                                            refreshRowRects()
                                        },
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
                            modifier = Modifier
                                .testTag(SidebarButtonTag(AppTab.Settings))
                                .onGloballyPositioned { coordinates ->
                                    rowCoordinates[AppTab.Settings] = coordinates
                                    refreshRowRects()
                                },
                        )
                    }

                    // Bounding the expanded content keeps the navigation rows fixed — and the
                    // travelling lens with them — on a short desktop window; the panel scrolls
                    // instead of pushing the rows away.
                    ThemeControlsPanel(
                        themeScheme = themeScheme,
                        accentPalette = accentPalette,
                        glassIntensity = glassIntensity,
                        onThemeChange = onThemeChange,
                        onAccentPaletteChange = onAccentPaletteChange,
                        onGlassIntensityPreview = onGlassIntensityPreview,
                        onGlassIntensityCommit = onGlassIntensityCommit,
                        modifier = Modifier.fillMaxWidth(),
                        contentMaxHeight = panelHeight * THEME_PANEL_HEIGHT_FRACTION,
                    )
                }
            }
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
    // The row draws no selection of its own. The panel's lens is one surface that travels between
    // the rows — see `NavigationSidebar` — which is the bar's arrangement: one material under a
    // moving selection, rather than a highlight that switches on in each cell in turn.
    //
    // The clip that used to bound the row's own wash went with it: with `indication = null` and
    // nothing drawn here, a clip had no pixels of ours to bound.
    Row(
        modifier = modifier
            .fillMaxWidth()
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

/** Share of the remaining sidebar height the expanded theme panel may occupy before scrolling. */
private const val THEME_PANEL_HEIGHT_FRACTION = 0.4f

/**
 * One destination's row in the sidebar, so a test taps by name rather than by position.
 *
 * Prefixed `shell.sidebar.` rather than reused from [NavigationRailButtonTag] or [TabButtonTag]:
 * a mixed-up query should find nothing rather than drive the wrong chrome.
 */
fun SidebarButtonTag(tab: AppTab): String = "shell.sidebar.${tab.wireValue}"

/**
 * The selection lens that travels between the sidebar's destinations.
 *
 * Tagged for the reason [NavigationRailLensTag] is: the lens is placed by arithmetic over measured
 * rectangles, so nothing but a comparison with the row it marks can tell that it landed somewhere
 * else. Distinct from the rail's and the bar's tags because exactly one of the three chromes is
 * composed at any width.
 */
const val SidebarLensTag = "shell.sidebar.lens"

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
