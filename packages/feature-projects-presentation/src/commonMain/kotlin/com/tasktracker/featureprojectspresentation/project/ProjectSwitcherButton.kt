package com.tasktracker.featureprojectspresentation

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupPositionProvider
import androidx.compose.ui.window.PopupProperties
import com.tasktracker.core.ProjectID
import com.tasktracker.designsystem.ThemeScheme
import com.tasktracker.designsystem.ttActionIcons
import com.tasktracker.designsystem.ttGlass
import com.tasktracker.designsystem.ttGlassLensAlpha
import com.tasktracker.designsystem.ttGlassSurface
import com.tasktracker.designsystem.ttIcon
import com.tasktracker.designsystem.ttInitialsAvatar
import com.tasktracker.designsystem.ttLabel
import com.tasktracker.designsystem.ttLabelSecondary
import com.tasktracker.designsystem.ttRadius
import com.tasktracker.designsystem.ttSpacing
import com.tasktracker.designsystem.ttSurface
import com.tasktracker.designsystem.ttText
import com.tasktracker.designsystem.ttTypography
import com.tasktracker.domain.Project

/**
 * Toolbar control displaying the current project's key monogram and name.
 * Tapping toggles a dropdown menu to switch projects or invokes [onClick].
 */
@Composable
fun ProjectSwitcherButton(
    project: Project?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    projects: List<PickableProject> = emptyList(),
    onSelectProject: ((ProjectID) -> Unit)? = null,
    showName: Boolean = true,
) {
    val shape = RoundedCornerShape(ttRadius.control)
    var expanded by remember { mutableStateOf(false) }
    var switcherWidthPx by remember { mutableStateOf(0) }

    val chevronRotation by animateFloatAsState(
        targetValue = if (expanded) CHEVRON_ROTATION_CLOSED else 0f,
        animationSpec = ttGlass.selectionSpring,
        label = "switcherChevron",
    )

    Box(modifier = modifier) {
        Row(
            modifier = Modifier
                .widthIn(max = switcherWidthMax)
                .onGloballyPositioned { switcherWidthPx = it.size.width }
                .clip(shape)
                .ttGlassSurface(shape = shape)
                .clickable {
                    if (onSelectProject != null && projects.size > 1) expanded = !expanded
                    else onClick()
                }
                .padding(horizontal = ttSpacing.lg, vertical = ttSpacing.md)
                .testTag(ProjectSwitcherTag),
            horizontalArrangement = Arrangement.spacedBy(ttSpacing.sm),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            project?.let {
                ttInitialsAvatar(
                    initials = it.keyPrefix,
                    size = switcherMonogramSize,
                    modifier = Modifier.testTag(ProjectSwitcherMonogramTag),
                )
            }
            // Collapses to monogram and chevron when space is constrained by board sprint title
            if (showName || project == null) {
                ttText(
                    text = project?.name ?: noProjectLabel,
                    style = ttTypography.callout,
                    color = if (project == null) ttLabelSecondary() else ttLabel(),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false),
                )
            }
            ttIcon(
                image = ttActionIcons.chevronDown,
                contentDescription = null,
                tint = ttLabelSecondary(),
                modifier = Modifier
                    .size(switcherChevronSize)
                    .testTag(ProjectSwitcherChevronTag)
                    .graphicsLayer { rotationZ = chevronRotation },
            )
        }

        if (expanded && onSelectProject != null && switcherWidthPx > 0) {
            Popup(
                popupPositionProvider = ProjectSwitcherDropdownPositionProvider(
                    gapPx = with(LocalDensity.current) { ttSpacing.sm.roundToPx() }
                ),
                onDismissRequest = { expanded = false },
                properties = PopupProperties(focusable = true),
            ) {
                Column(
                    modifier = Modifier
                        .widthIn(
                            min = with(LocalDensity.current) { switcherWidthPx.toDp() },
                            max = switcherMenuWidthMax,
                        )
                        .heightIn(max = 240.dp)
                        .verticalScroll(rememberScrollState())
                        .ttGlassSurface(
                            shape = RoundedCornerShape(ttRadius.control),
                            base = ttSurface().copy(alpha = 1f - ttGlassLensAlpha()),
                        )
                        .padding(vertical = ttSpacing.xs)
                        .testTag(ProjectSwitcherMenuTag),
                ) {
                    projects.forEach { option ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    expanded = false
                                    onSelectProject(option.id)
                                }
                                .padding(horizontal = ttSpacing.lg, vertical = ttSpacing.md)
                                .testTag(ProjectSwitcherOptionTag(option.id)),
                            horizontalArrangement = Arrangement.spacedBy(ttSpacing.sm),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            ttInitialsAvatar(initials = option.keyPrefix, size = switcherMonogramSize)
                            ttText(
                                text = option.name,
                                style = ttTypography.callout,
                                color = ttLabel(),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f),
                            )
                        }
                    }
                }
            }
        }
    }
}

private class ProjectSwitcherDropdownPositionProvider(
    private val gapPx: Int,
) : PopupPositionProvider {
    override fun calculatePosition(
        anchorBounds: IntRect,
        windowSize: IntSize,
        layoutDirection: LayoutDirection,
        popupContentSize: IntSize,
    ): IntOffset {
        val maxX = (windowSize.width - popupContentSize.width).coerceAtLeast(0)
        val x = when (layoutDirection) {
            LayoutDirection.Ltr -> anchorBounds.left.coerceIn(0, maxX)
            LayoutDirection.Rtl -> (anchorBounds.right - popupContentSize.width).coerceIn(0, maxX)
        }
        val below = anchorBounds.bottom + gapPx
        val above = anchorBounds.top - popupContentSize.height - gapPx
        val y = if (below + popupContentSize.height <= windowSize.height) below else above.coerceAtLeast(0)
        return IntOffset(x, y)
    }
}

const val ProjectSwitcherTag = "board.switcher"
const val ProjectSwitcherChevronTag = "board.switcher.chevron"
const val ProjectSwitcherMonogramTag = "board.switcher.monogram"
const val ProjectSwitcherMenuTag = "board.switcher.menu"
fun ProjectSwitcherOptionTag(projectID: ProjectID): String = "board.switcher.option.${projectID.stringValue}"

private const val noProjectLabel = "No project"
private val switcherMonogramSize = 26.dp
private val switcherChevronSize = 13.dp
internal val switcherWidthMax = 240.dp
private val switcherMenuWidthMax = 320.dp
private const val CHEVRON_ROTATION_CLOSED = 180f