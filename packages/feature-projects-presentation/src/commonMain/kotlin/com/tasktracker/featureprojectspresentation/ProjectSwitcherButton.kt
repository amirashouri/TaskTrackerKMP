package com.tasktracker.featureprojectspresentation

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.animation.core.animateFloatAsState
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupPositionProvider
import androidx.compose.ui.window.PopupProperties
import com.tasktracker.designsystem.ThemeScheme
import com.tasktracker.designsystem.ttGlass
import com.tasktracker.designsystem.ttGlassLensAlpha
import com.tasktracker.designsystem.ttActionIcons
import com.tasktracker.designsystem.ttIcon
import com.tasktracker.designsystem.ttInitialsAvatar
import com.tasktracker.designsystem.ttGlassSurface
import com.tasktracker.designsystem.ttLabel
import com.tasktracker.designsystem.ttLabelSecondary
import com.tasktracker.designsystem.ttRadius
import com.tasktracker.designsystem.ttSpacing
import com.tasktracker.designsystem.ttSurface
import com.tasktracker.designsystem.ttText
import com.tasktracker.designsystem.ttTypography
import com.tasktracker.domain.Project
import com.tasktracker.core.ProjectID

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
    projects: List<PickableProject> = emptyList(),
    onSelectProject: ((ProjectID) -> Unit)? = null,
    showName: Boolean = true,
) {
    val shape = RoundedCornerShape(ttRadius.control)
    var expanded by remember { mutableStateOf(false) }
    var switcherWidthPx by remember { mutableStateOf(0) }

    // 0 open, 180 closed. A spring rather than a tween because the pill's own press already
    // moves on [ttGlass.pressSpring], and a chevron that eased in over a different curve than
    // the surface it sits on reads as two controls disagreeing about the same tap.
    val chevronRotation by animateFloatAsState(
        targetValue = if (expanded) CHEVRON_ROTATION_CLOSED else 0f,
        animationSpec = ttGlass.selectionSpring,
        label = "switcherChevron",
    )

    Box(modifier = modifier) {
        Row(
            modifier = Modifier
                // Content-width, and capped rather than filled.
                //
                // It used to be `fillMaxWidth`, which made a ~358dp glass slab holding 22dp of
                // content: the name is ellipsised, so the extra width bought nothing and the
                // control read as an empty bar rather than as something to press. A control that
                // opens a menu should look like the size of the thing it names.
                //
                // The cap is what keeps a long project name from pushing the board's trailing
                // content off the header, and it is the reason the row below carries a weight:
                // without one, `widthIn` alone would let a 60-character name take the row.
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
            // The board's header has a sprint/Kanban title of its own to make room for, so it
            // collapses this pill to the monogram and the chevron. A null project still keeps the
            // words: "a project is selected" is the one state this pill identifies, and an
            // unselected pill of two meaningless marks (instead of the gap that "no project"
            // leaves) would misname the screen. The dropdown always renders full names, whatever
            // the pill does.
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
                    .graphicsLayer {
                        // Rotated rather than swapped for a second glyph, because the two
                        // directions are the same mark and a mark that changes shape when a menu
                        // opens reads as the control becoming something else.
                        rotationZ = chevronRotation
                    },
            )
        }

        if (expanded && onSelectProject != null && switcherWidthPx > 0) {
            Popup(
                popupPositionProvider = ProjectSwitcherDropdownPositionProvider(
                    gapPx = with(androidx.compose.ui.platform.LocalDensity.current) {
                        ttSpacing.sm.roundToPx()
                    }
                ),
                onDismissRequest = { expanded = false },
                properties = PopupProperties(focusable = true),
            ) {
                Column(
                    modifier = Modifier
                        // At least the pill's width so the menu lines up with it, and up to a
                        // cap so a short project name does not produce a menu too narrow to read
                        // the other projects' names in. A menu narrower than its widest row is
                        // the ellipsis, and an ellipsis in a menu of names is useless.
                        .widthIn(
                            min = with(androidx.compose.ui.platform.LocalDensity.current) {
                                switcherWidthPx.toDp()
                            },
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
        layoutDirection: androidx.compose.ui.unit.LayoutDirection,
        popupContentSize: IntSize,
    ): IntOffset {
        val maxX = (windowSize.width - popupContentSize.width).coerceAtLeast(0)
        val x = when (layoutDirection) {
            androidx.compose.ui.unit.LayoutDirection.Ltr -> anchorBounds.left.coerceIn(0, maxX)
            androidx.compose.ui.unit.LayoutDirection.Rtl ->
                (anchorBounds.right - popupContentSize.width).coerceIn(0, maxX)
        }
        val below = anchorBounds.bottom + gapPx
        val above = anchorBounds.top - popupContentSize.height - gapPx
        val y = if (below + popupContentSize.height <= windowSize.height) below else above.coerceAtLeast(0)
        return IntOffset(x, y)
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
const val ProjectSwitcherMonogramTag = "board.switcher.monogram"
const val ProjectSwitcherMenuTag = "board.switcher.menu"
fun ProjectSwitcherOptionTag(projectID: ProjectID): String = "board.switcher.option.${projectID.stringValue}"

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

/**
 * The key prefix's disc, at 26dp rather than [ttAvatarSize.avatar]'s 24dp.
 *
 * The switcher's monogram and a stack's avatar are the same shape, and they are deliberately
 * not the same size: this one is alone in a pill with a name beside it, where 24dp reads as an
 * afterthought, and the stack is four of them at once, where 26dp would crowd the header. One
 * primitive, two sizes, and the reason written down - because "make these the same" is the
 * obvious next change and it would be wrong.
 */
private val switcherMonogramSize = 26.dp

/** The chevron, at half [switcherMonogramSize] so it reads as an affordance and not a peer. */
private val switcherChevronSize = 13.dp

/**
 * How far the pill may grow before it starts ellipsising the project name.
 *
 * 240dp is about two thirds of a phone and about half of a tablet's portrait width: past that a
 * project name is long enough that the board's own trailing content matters more than reading
 * the rest of it.
 */
internal val switcherWidthMax = 240.dp

/** The menu's own cap, so a long project name does not make every row an ellipsis. */
private val switcherMenuWidthMax = 320.dp

private const val CHEVRON_ROTATION_CLOSED = 180f
