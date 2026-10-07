package com.tasktracker.featuresettingspresentation

import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.tasktracker.designsystem.ttAccent
import com.tasktracker.designsystem.ttActionIcons
import com.tasktracker.designsystem.ttIcon
import com.tasktracker.designsystem.ttInitialsAvatar
import com.tasktracker.designsystem.ttInitialsFor
import com.tasktracker.designsystem.ttLabel
import com.tasktracker.designsystem.ttSpacing
import com.tasktracker.designsystem.ttText
import com.tasktracker.designsystem.ttTypography
import com.tasktracker.featuresettingsdomain.UserDetails
import com.tasktracker.resources.Res
import com.tasktracker.resources.avatar_0
import com.tasktracker.resources.avatar_1
import com.tasktracker.resources.avatar_2
import com.tasktracker.resources.avatar_3
import com.tasktracker.resources.avatar_4
import com.tasktracker.resources.avatar_5
import com.tasktracker.resources.avatar_6
import com.tasktracker.resources.avatar_7
import com.tasktracker.resources.avatar_8
import com.tasktracker.resources.avatar_9
import com.tasktracker.resources.settings_avatar_label
import org.jetbrains.compose.resources.DrawableResource
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource

const val SettingsUserDetailsTag = "settings.userDetails"
const val SettingsAvatarTag = "settings.avatar"
const val AvatarPickerTag = "settings.avatarPicker"

/** Per-portrait tag, so a scenario can pick "7" without a positional matcher. */
fun AvatarOptionTag(id: Int): String = "settings.avatarOption.$id"

/**
 * The identity header: a large centred disc with the name beneath it, the way a profile reads
 * on a social app rather than a settings row — and the disc is also the button that opens
 * the portrait picker.
 */
@Composable
internal fun UserDetailsRow(
    details: UserDetails,
    onDetailsChange: (UserDetails) -> Unit,
    modifier: Modifier = Modifier,
) {
    var avatarPickerOpen by remember { mutableStateOf(false) }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = ttSpacing.sm),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(ttSpacing.sm),
    ) {
        Box(
            modifier = Modifier
                .clip(CircleShape)
                .clickable(role = Role.Button) { avatarPickerOpen = !avatarPickerOpen }
                .testTag(SettingsAvatarTag),
            contentAlignment = Alignment.Center,
        ) {
            val avatarId = details.avatarId
            if (avatarId != null && avatarId in AvatarIdRange) {
                Image(
                    painter = painterResource(avatarResource(avatarId)),
                    contentDescription = avatarPortraitLabel(avatarId),
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.size(UserDetailsAvatarSize).clip(CircleShape),
                )
            } else {
                Box(
                    modifier = Modifier.size(UserDetailsAvatarSize),
                    contentAlignment = Alignment.Center,
                ) {
                    ttIcon(
                        image = ttActionIcons.person,
                        contentDescription = null,
                        tint = ttAccent(),
                        modifier = Modifier.size(UserDetailsAvatarSize * 0.55f),
                    )
                    ttInitialsAvatar(
                        initials = if (details.fullName.isNotBlank()) ttInitialsFor(details.fullName) else "U",
                        size = UserDetailsAvatarSize,
                    )
                }
            }
        }
        ttText(
            text = if (details.fullName.isNotBlank()) details.fullName else details.email,
            style = ttTypography.headline,
            color = ttLabel(),
            textAlign = TextAlign.Center,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        if (avatarPickerOpen) {
            Column(
                modifier = Modifier.fillMaxWidth().testTag(AvatarPickerTag),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(ttSpacing.xs),
            ) {
                AvatarIdRange.chunked(AvatarsPerRow).forEach { row ->
                    Row(horizontalArrangement = Arrangement.spacedBy(ttSpacing.xs)) {
                        row.forEach { id ->
                            AvatarPortraitOption(
                                id = id,
                                isSelected = id == details.avatarId,
                                onClick = {
                                    onDetailsChange(details.copy(avatarId = id))
                                    avatarPickerOpen = false
                                },
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * One of the ten bundled portraits, in the grid: 44dp, tappable, and selected-state marked by
 * opacity rather than a ring.
 */
@Composable
private fun AvatarPortraitOption(
    id: Int,
    isSelected: Boolean,
    onClick: () -> Unit,
) {
    Image(
        painter = painterResource(avatarResource(id)),
        contentDescription = avatarPortraitLabel(id),
        contentScale = ContentScale.Crop,
        modifier = Modifier
            .size(UserDetailsAvatarOptionSize)
            .clip(CircleShape)
            .alpha(if (isSelected) SelectedAvatarAlpha else UnselectedAvatarAlpha)
            .selectable(selected = isSelected, role = Role.Button, onClick = onClick)
            .testTag(AvatarOptionTag(id)),
    )
}

/** "Avatar 7" — the label a screen reader announces for a portrait. */
@Composable
private fun avatarPortraitLabel(id: Int): String =
    "${stringResource(Res.string.settings_avatar_label)} $id"

/** One of the ten bundled portraits; ids outside the range fall back to the first. */
private fun avatarResource(id: Int): DrawableResource = when (id) {
    0 -> Res.drawable.avatar_0
    1 -> Res.drawable.avatar_1
    2 -> Res.drawable.avatar_2
    3 -> Res.drawable.avatar_3
    4 -> Res.drawable.avatar_4
    5 -> Res.drawable.avatar_5
    6 -> Res.drawable.avatar_6
    7 -> Res.drawable.avatar_7
    8 -> Res.drawable.avatar_8
    9 -> Res.drawable.avatar_9
    else -> Res.drawable.avatar_0
}

/** The profile picture's diameter. A named constant so the KDoc's 72 and the render agree. */
private val UserDetailsAvatarSize = 72.dp

/** The grid thumb: 44dp x 5 with `ttSpacing.xs` gaps is 236dp, inside the section's 264dp. */
private val UserDetailsAvatarOptionSize = 44.dp

private val AvatarIdRange = 0..9
private const val AvatarsPerRow = 5
private const val SelectedAvatarAlpha = 1f
private const val UnselectedAvatarAlpha = 0.55f
