package com.tasktracker.sharedui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
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
import com.tasktracker.routing.AppTab

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
 * destinations with their labels readable, and (in 8.2) the project switcher that every
 * project-scoped tab needs.
 *
 * ## One panel, not a rail plus something else
 *
 * This is a single glass surface with rows inside it, drawn from the same primitives the rail
 * uses: `ttGlassSurface` at `ttRadius.surface`, the same shadow and accent, the same two inks.
 * It is deliberately **not** a new design-system component: there is no travelling lens here
 * (a lens that slides down a list is the effect `ttSelectionWash` exists to avoid), no segment
 * arithmetic, and only one shell will ever draw it. What would be shared — the surface and the
 * row wash — already is.
 *
 * ## Why the panel fills the height and the rows do not
 *
 * The rail's height is owned by its weighted cells, and its own KDoc records two `fillMaxHeight`
 * calls that were provably redundant. Here the relationship is the opposite and the modifier is
 * load-bearing: five content-sized rows in a bounded parent would otherwise produce a ~250dp
 * panel floating under the window's top edge, and 8.2 puts a scrolling list in the space below
 * the destinations, which needs the height to exist. So the panel asks for the height and the
 * rows are content-sized — the inverse of the rail, for the inverse reason.
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
    modifier: Modifier = Modifier,
) {
    val tabs = AppTab.entries

    // The same two inks the rail's cells use, for the same reason: both put a label on glass with
    // an accent wash under the selected one. `TabLensContrastTest` holds the derivation and the
    // measured contrast on both rasterisers.
    val selectedInk = ttSelectionLabelInk(selected = true)
    val inactiveInk = ttSelectionLabelInk(selected = false)
    val accent = ttAccent()

    Box(
        modifier = modifier
            // The edge insets are the shell's, not the panel's, exactly as they are for the rail:
            // the component owns the surface and the caller owns where it sits in the tree. The
            // leading edge has no system inset to borrow, so it borrows the number the shell
            // already uses horizontally, and the panel reads as the same kind of floating object
            // as the bar and the rail.
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
                // Load-bearing here, unlike the rail's: the rows below are content-sized, so
                // without this the panel would wrap them rather than own the window's height.
                .fillMaxHeight()
                // The shadow sits outside the glass and the glass is drawn over it, so only the
                // ring around the silhouette survives — the rail's own note, unchanged, because
                // this is the same material.
                .shadow(
                    elevation = ttGlass.tabBarShadowElevation,
                    shape = RoundedCornerShape(ttRadius.surface),
                    ambientColor = accent.copy(alpha = ttGlass.TAB_BAR_SHADOW_ALPHA),
                    spotColor = accent.copy(alpha = ttGlass.TAB_BAR_SHADOW_ALPHA),
                )
                .ttGlassSurface(shape = RoundedCornerShape(ttRadius.surface))
                // Inside the material, after it: this is content inset, so shrinking the content
                // keeps the hairline the right distance from the rows.
                //
                // `ttRadius.surface` is the glass card's radius, not a new one: a panel and a
                // floating card are the same kind of surface at different sizes.
                .padding(ttSpacing.hairlineInset)
                .padding(horizontal = ttSpacing.md, vertical = ttSpacing.md),
            verticalArrangement = Arrangement.spacedBy(ttSpacing.xxs),
        ) {
            for (tab in tabs) {
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

// MARK: - Tags

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
 * a mixed-up query should find nothing rather than quietly drive the wrong chrome.
 */
fun SidebarButtonTag(tab: AppTab): String = "shell.sidebar.${tab.wireValue}"
