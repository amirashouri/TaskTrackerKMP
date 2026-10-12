package com.tasktracker.featureprojectspresentation

import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupPositionProvider
import androidx.compose.ui.window.PopupProperties
import androidx.compose.ui.tooling.preview.Preview
import com.tasktracker.core.UserID
import com.tasktracker.designsystem.ThemeScheme
import com.tasktracker.designsystem.ttAccent
import com.tasktracker.designsystem.ttActionIcons
import com.tasktracker.designsystem.ttAvatarSize
import com.tasktracker.designsystem.ttGlassCard
import com.tasktracker.designsystem.ttGlassSurface
import com.tasktracker.designsystem.ttIcon
import com.tasktracker.designsystem.ttIconSize
import com.tasktracker.designsystem.ttInitialsAvatar
import com.tasktracker.designsystem.ttInitialsFor
import com.tasktracker.designsystem.ttLabel
import com.tasktracker.designsystem.ttLabelSecondary
import com.tasktracker.designsystem.ttRadius
import com.tasktracker.designsystem.ttSpacing
import com.tasktracker.designsystem.ttSurface
import com.tasktracker.designsystem.ttText
import com.tasktracker.designsystem.ttTypography
import com.tasktracker.featureteamsdomain.User
import com.tasktracker.resources.Res
import com.tasktracker.resources.member_picker_remove
import kotlinx.coroutines.delay
import org.jetbrains.compose.resources.stringResource

/**
 * Search a member directory and pick from what comes back, as a dropdown under the field.
 *
 * ## The list is a popup, and it opens with the field
 *
 * The field is a closed control until it is tapped: the results render in a [Popup]
 * anchored beneath it, the way the project switcher's menu anchors under its pill — same
 * `PopupPositionProvider` shape, same 240dp scroll cap, same glass surface. The list does
 * *not* take space in the sheet while closed, which is the whole point of a dropdown in a
 * form that would otherwise grow a permanent eight-row list under a search box.
 *
 * ## Why the popup is not focusable, and what that costs
 *
 * `PopupProperties(focusable = false)`, because a focusable popup takes focus from the
 * search field the moment it opens — which dismisses the IME and makes the box the user
 * just tapped deaf to further typing. AndroidX's own [androidx.compose.material3]
 * `ExposedDropdownMenu` documents the same rule: "if typing on the IME is required, the
 * menu should not be focusable in order to prevent stealing focus from the input method",
 * and makes its editable anchors non-focusable for exactly this reason.
 *
 * The cost is dismissal: a non-focusable popup never receives an outside tap or the back
 * key, so `onDismissRequest` only fires on platforms that route it anyway. The dismiss
 * paths this control actually offers are the ones it owns — tapping the field again
 * toggles the menu (the editable-anchor contract), and a single-select choice closes it.
 * That is fewer paths than a focusable menu has, and it is the price of a search box that
 * keeps working while the menu is open.
 *
 * ## Two selection modes, one component
 *
 * Project members accumulate ([multiSelect]); a sprint's owner and a work item's assignee
 * are single-valued, so there [multiSelect] is false, choosing a row replaces the previous
 * choice, and [clearLabel] offers the way back to nothing. Two components would have been
 * two copies of the row rendering and the filter — and the row is the part that has to
 * stay identical for the three fields to feel like one feature.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun MemberPicker(
    title: String,
    selectedIDs: List<UserID>,
    onSelectedIDsChanged: (List<UserID>) -> Unit,
    modifier: Modifier = Modifier,
    multiSelect: Boolean = true,
    clearLabel: String? = null,
    placeholder: String = "",
    searchMembers: suspend (String) -> List<User> = { emptyList() },
    /**
     * Resolves the *selected* ids to profiles, for the pills above the field.
     *
     * Deliberately separate from [searchMembers]: that one may be scoped to the current
     * project's members, and a selection that predates a membership change has to render
     * anyway. Hosts should pass the directory's unfiltered id lookup here — see
     * [com.tasktracker.featureteamsdomain.TeamPort.users].
     *
     * Defaults to searching the directory with a blank query, which is the unfiltered listing
     * and therefore correct wherever [searchMembers] is *not* scoped.
     */
    resolveSelected: suspend (List<UserID>) -> List<User> = { ids ->
        val directory = searchMembers("")
        ids.mapNotNull { id -> directory.firstOrNull { it.id == id } }
    },
    /**
     * The root test tag, and the prefix for every tag this picker draws beneath it.
     *
     * The root carries [tagPrefix] verbatim rather than a wrapped form of it, because the
     * assignee row's callers assert on the row's own tag (`sprints.editor.owner`) and a
     * wrapper would silently move the node out from under them — which is exactly how the
     * sprint editor's two window scenarios went red when this picker landed.
     */
    tagPrefix: String = title,
) {
    var query by remember { mutableStateOf("") }
    var results by remember { mutableStateOf<List<User>>(emptyList()) }
    var expanded by remember { mutableStateOf(false) }
    var anchorWidthPx by remember { mutableStateOf(0) }
    var selectedUsers by remember { mutableStateOf<List<User>>(emptyList()) }
    val density = LocalDensity.current

    // Debounced so a four-letter name costs one port call, not four. 250ms is the usual
    // typing gap between keystrokes; shorter and the debounce never bites, longer and the
    // list feels broken while somebody is typing quickly.
    LaunchedEffect(query) {
        if (query.isNotEmpty()) delay(SEARCH_DEBOUNCE_MILLIS)
        results = searchMembers(query)
    }

    // The pills need names, and the call sites only hold IDs and the search seam — so the
    // picker resolves them itself, once per selection change rather than per keystroke.
    //
    // Through [resolveSelected], not through [searchMembers], and the difference is the whole
    // point of this seam. `searchMembers` is now *filtered* to the current project's members
    // (see `assignableMembersFor`), so a task assigned last month to someone who has since
    // left the project would resolve to nothing and its pill would silently vanish — the field
    // would read Unassigned while the saved model still held an assignee, which is the app
    // lying about data it has. Resolution by id is not filtered, so the pill survives and the
    // user can see who it is actually assigned to, then reassign or clear it deliberately.
    LaunchedEffect(selectedIDs, resolveSelected) {
        selectedUsers = if (selectedIDs.isEmpty()) {
            emptyList()
        } else {
            resolveSelected(selectedIDs)
        }
    }

    Box(modifier = modifier.fillMaxWidth().testTag(tagPrefix)) {
        // Field and pills stack vertically; the Box exists only so the dropdown Popup has
        // an anchor — children of a Box draw on top of each other, which put the first
        // pill row *over* the field until they were columned.
        Column {
            ProjectsTextField(
                title = title,
                value = query,
                onValueChange = { query = it },
                placeholder = placeholder,
                modifier = Modifier
                    .fillMaxWidth()
                    .onGloballyPositioned { anchorWidthPx = it.size.width }
                    // Observe the tap rather than adding a competing gesture: the Initial pass
                    // sees the down before the text field consumes it, and nothing is consumed
                    // here, so cursor placement and selection still behave. Toggle, not
                    // "open", because the editable-anchor contract is tap-again-to-close —
                    // the same thing Material's own editable menu anchors do.
                    .pointerInput(Unit) {
                        awaitEachGesture {
                            awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                            expanded = !expanded
                        }
                    },
            )
            if (selectedUsers.isNotEmpty()) {
                FlowRow(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = ttSpacing.sm)
                        .testTag(MemberPickerPillsTag(tagPrefix)),
                    horizontalArrangement = Arrangement.spacedBy(ttSpacing.sm),
                    verticalArrangement = Arrangement.spacedBy(ttSpacing.sm),
                ) {
                    for (user in selectedUsers) {
                        MemberPill(
                            id = user.id,
                            name = user.preferredGreeting,
                            onRemove = {
                                onSelectedIDsChanged(selectedIDs - user.id)
                            },
                            modifier = Modifier.testTag(MemberPickerPillTag(tagPrefix, user.id)),
                        )
                    }
                }
            }
        }
        if (expanded) {
            val gapPx = with(density) { ttSpacing.sm.roundToPx() }
            Popup(
                popupPositionProvider = remember(density) {
                    MemberPickerDropdownPositionProvider(gapPx = gapPx)
                },
                onDismissRequest = { expanded = false },
                // See the class KDoc: focusable would steal the IME from the field.
                properties = PopupProperties(focusable = false),
            ) {
                Column(
                    modifier = Modifier
                        // At least the field's width so the menu lines up with it, and the
                        // switcher's 240dp scroll cap so a long directory cannot grow a
                        // menu taller than the sheet it floats over.
                        .widthIn(min = with(density) { anchorWidthPx.toDp() })
                        .heightIn(max = MEMBER_PICKER_MENU_MAX_HEIGHT)
                        .verticalScroll(rememberScrollState())
                        .ttGlassSurface(
                            shape = RoundedCornerShape(ttRadius.control),
                            base = ttSurface().copy(alpha = 1f),
                        )
                        .padding(vertical = ttSpacing.xs)
                        .testTag(MemberPickerMenuTag(tagPrefix)),
                ) {
                    if (clearLabel != null && selectedIDs.isNotEmpty()) {
                        PickerRow(
                            name = clearLabel,
                            email = null,
                            isSelected = false,
                            onClick = {
                                expanded = false
                                onSelectedIDsChanged(emptyList())
                            },
                            modifier = Modifier.testTag(MemberPickerClearTag(tagPrefix)),
                        )
                    }
                    results.forEach { user ->
                        val isSelected = user.id in selectedIDs
                        PickerRow(
                            name = user.preferredGreeting,
                            email = user.email,
                            isSelected = isSelected,
                            onClick = {
                                onSelectedIDsChanged(
                                    when {
                                        multiSelect && isSelected -> selectedIDs - user.id
                                        multiSelect -> selectedIDs + user.id
                                        // Single-select closes on choice: the decision is
                                        // made, and a menu left open over the form is a
                                        // menu the user now has to dismiss by hand.
                                        !multiSelect && isSelected -> {
                                            expanded = false
                                            emptyList()
                                        }
                                        else -> {
                                            expanded = false
                                            listOf(user.id)
                                        }
                                    },
                                )
                            },
                            modifier = Modifier.testTag(MemberPickerRowTag(tagPrefix, user.id)),
                        )
                    }
                }
            }
        }
    }
}

/**
 * One directory row: disc, name, email, and a selected mark.
 *
 * No per-row glass: this row only ever draws inside the dropdown's own surface, and two
 * glass layers — the menu's and the row's — is how a list becomes a stack of unreadable
 * panels. The menu owns the surface; the row owns the content.
 */
@Composable
private fun PickerRow(
    name: String,
    email: String?,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = ttSpacing.lg, vertical = ttSpacing.md),
        horizontalArrangement = Arrangement.spacedBy(ttSpacing.sm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ttInitialsAvatar(
            initials = ttInitialsFor(name),
            size = ttAvatarSize.avatar,
        )
        Column(modifier = Modifier.weight(1f)) {
            ttText(
                text = name,
                style = ttTypography.callout,
                color = ttLabel(),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (email != null) {
                ttText(
                    text = email,
                    style = ttTypography.footnote,
                    color = ttLabelSecondary(),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        if (isSelected) {
            ttText(
                text = "\u2713",
                style = ttTypography.callout,
                color = ttAccent(),
                modifier = Modifier.testTag(MemberPickerSelectedMarkTag),
            )
        }
    }
}

/**
 * A selected member, as a dismissible pill under the field.
 *
 * The dependency pill's shape — control-radius clip, [ttGlassCard] — because a selected
 * value in this app should look the same whether it is a blocker or a person. The one
 * difference is the dismiss affordance: that pill draws a text "X", and this one draws
 * [ttActionIcons.close] at [ttIconSize.close], which is the "not so big" cross made into
 * a token instead of a font size.
 */
@Composable
private fun MemberPill(
    id: UserID,
    name: String,
    onRemove: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(ttRadius.control))
            .ttGlassCard()
            .padding(
                start = ttSpacing.md,
                top = ttSpacing.xs,
                end = ttSpacing.xs,
                bottom = ttSpacing.xs,
            ),
        horizontalArrangement = Arrangement.spacedBy(ttSpacing.xs),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ttText(
            text = name,
            style = ttTypography.callout,
            color = ttLabel(),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(ttRadius.control))
                .clickable(
                    role = Role.Button,
                    onClickLabel = stringResource(Res.string.member_picker_remove, name),
                    onClick = onRemove,
                )
                .padding(ttSpacing.xs)
                .testTag(MemberPickerPillRemoveTag(id)),
        ) {
            ttIcon(
                image = ttActionIcons.close,
                contentDescription = null,
                tint = ttLabelSecondary(),
                modifier = Modifier.size(ttIconSize.close),
            )
        }
    }
}

/**
 * Places the menu under the field, flipped above it when the sheet has no room below.
 *
 * The same geometry [ProjectSwitcherButton]'s provider uses, because the two dropdowns in
 * this app should not disagree about where "under the anchor" is.
 */
private class MemberPickerDropdownPositionProvider(
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
        val y = if (below + popupContentSize.height <= windowSize.height) {
            below
        } else {
            above.coerceAtLeast(0)
        }
        return IntOffset(x, y)
    }
}

@Preview(name = "Member picker, dark", showBackground = true, widthDp = 360)
@Composable
private fun MemberPickerMultiPreview() = ProjectsPreview(ThemeScheme.Dark) {
    MemberPicker(
        title = "Members",
        selectedIDs = listOf(ViewPreviewData.previewUserID),
        onSelectedIDsChanged = {},
        placeholder = "Search members",
        searchMembers = ViewPreviewData.previewSearchMembers,
    )
}

@Preview(name = "Member picker, single, light", showBackground = true, widthDp = 360)
@Composable
private fun MemberPickerSinglePreview() = ProjectsPreview(ThemeScheme.Light) {
    MemberPicker(
        title = "Assignee",
        selectedIDs = listOf(ViewPreviewData.previewUserID),
        onSelectedIDsChanged = {},
        multiSelect = false,
        clearLabel = "Unassigned",
        placeholder = "Search people",
        searchMembers = ViewPreviewData.previewSearchMembers,
    )
}

/** Milliseconds of silence before the port is called; see the LaunchedEffect above. */
private const val SEARCH_DEBOUNCE_MILLIS = 250L

/**
 * The menu's height cap.
 *
 * 240dp, not a new number: it is [ProjectSwitcherButton]'s own menu cap, chosen there as
 * "about two thirds of a phone" — two dropdowns in one app do not get two ceilings.
 */
private val MEMBER_PICKER_MENU_MAX_HEIGHT = 240.dp

fun MemberPickerFieldTag(title: String): String = "projects.picker.$title.field"
fun MemberPickerMenuTag(title: String): String = "projects.picker.$title.menu"
fun MemberPickerRowTag(title: String, id: UserID): String = "projects.picker.$title.row.$id"
fun MemberPickerClearTag(title: String): String = "projects.picker.$title.clear"
fun MemberPickerPillsTag(title: String): String = "projects.picker.$title.pills"
fun MemberPickerPillTag(title: String, id: UserID): String = "projects.picker.$title.pill.$id"
fun MemberPickerPillRemoveTag(id: UserID): String = "projects.picker.pill.remove.$id"
const val MemberPickerSelectedMarkTag = "projects.picker.selected"
