package com.tasktracker.featureprojectspresentation

import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.interaction.MutableInteractionSource
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
import com.tasktracker.core.UserID
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
 * Searchable member picker dropdown with dismissible selection pills.
 * Rendered in a non-focusable popup to prevent stealing IME keyboard focus while typing.
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
    /** Resolves selected IDs unfiltered so historic/unscoped assignees remain visible. */
    resolveSelected: suspend (List<UserID>) -> List<User> = { ids ->
        val directory = searchMembers("")
        ids.mapNotNull { id -> directory.firstOrNull { it.id == id } }
    },
    tagPrefix: String = title,
) {
    var query by remember { mutableStateOf("") }
    var results by remember { mutableStateOf<List<User>>(emptyList()) }
    var expanded by remember { mutableStateOf(false) }
    var anchorWidthPx by remember { mutableStateOf(0) }
    var selectedUsers by remember { mutableStateOf<List<User>>(emptyList()) }
    val density = LocalDensity.current

    LaunchedEffect(query) {
        if (query.isNotEmpty()) delay(SEARCH_DEBOUNCE_MILLIS)
        results = searchMembers(query)
    }

    LaunchedEffect(selectedIDs, resolveSelected) {
        selectedUsers = if (selectedIDs.isEmpty()) {
            emptyList()
        } else {
            resolveSelected(selectedIDs)
        }
    }

    Box(modifier = modifier.fillMaxWidth().testTag(tagPrefix)) {
        Column {
            ProjectsTextField(
                title = title,
                value = query,
                onValueChange = { query = it },
                placeholder = placeholder,
                modifier = Modifier
                    .fillMaxWidth()
                    .onGloballyPositioned { anchorWidthPx = it.size.width }
                    // Observe initial down event to toggle dropdown without stealing text field focus
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
                properties = PopupProperties(focusable = false),
            ) {
                Column(
                    modifier = Modifier
                        .widthIn(min = with(density) { anchorWidthPx.toDp() }, max = 280.dp)
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
            .clickable(
                indication = null,
                interactionSource = remember { MutableInteractionSource() },
                role = Role.Button,
                onClick = onClick,
            )
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

private const val SEARCH_DEBOUNCE_MILLIS = 250L
private val MEMBER_PICKER_MENU_MAX_HEIGHT = 240.dp

fun MemberPickerFieldTag(title: String): String = "projects.picker.$title.field"
fun MemberPickerMenuTag(title: String): String = "projects.picker.$title.menu"
fun MemberPickerRowTag(title: String, id: UserID): String = "projects.picker.$title.row.$id"
fun MemberPickerClearTag(title: String): String = "projects.picker.$title.clear"
fun MemberPickerPillsTag(title: String): String = "projects.picker.$title.pills"
fun MemberPickerPillTag(title: String, id: UserID): String = "projects.picker.$title.pill.$id"
fun MemberPickerPillRemoveTag(id: UserID): String = "projects.picker.pill.remove.$id"
const val MemberPickerSelectedMarkTag = "projects.picker.selected"