package com.tasktracker.featuresettingspresentation

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import com.tasktracker.designsystem.ttAccent
import com.tasktracker.designsystem.ttActionIcons
import com.tasktracker.designsystem.ttIcon
import com.tasktracker.designsystem.ttLabel
import com.tasktracker.designsystem.ttLabelSecondary
import com.tasktracker.designsystem.ttSettingsIcons
import com.tasktracker.designsystem.ttSpacing
import com.tasktracker.designsystem.ttStatusActive
import com.tasktracker.designsystem.ttStatusAway
import com.tasktracker.designsystem.ttStatusBeRightBack
import com.tasktracker.designsystem.ttStatusBusy
import com.tasktracker.designsystem.ttSelectionLabelInk
import com.tasktracker.designsystem.ttText
import com.tasktracker.designsystem.ttTypography
import com.tasktracker.featuresettingsdomain.UserStatus
import com.tasktracker.resources.Res
import com.tasktracker.resources.settings_user_status
import org.jetbrains.compose.resources.stringResource

const val SettingsUserStatusRowTag = "settings.userStatus"

@Composable
internal fun UserStatusPickerRow(
    selected: UserStatus?,
    onSelect: (UserStatus) -> Unit,
    modifier: Modifier = Modifier,
) {
    var expanded by remember { mutableStateOf(false) }

    Box(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 48.dp)
                .clickable(
                    indication = null,
                    interactionSource = remember { MutableInteractionSource() },
                    role = Role.Button,
                ) { expanded = !expanded },
            horizontalArrangement = Arrangement.spacedBy(ttSpacing.md),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            SettingsIconWell(icon = ttSettingsIcons.status, tint = ttAccent())
            ttText(
                text = stringResource(Res.string.settings_user_status),
                style = ttTypography.body,
                color = ttLabel(),
            )
            Spacer(modifier = Modifier.weight(1f))
            val selectedColor = when (selected) {
                UserStatus.Active -> ttStatusActive()
                UserStatus.Away -> ttStatusAway()
                UserStatus.BeRightBack -> ttStatusBeRightBack()
                UserStatus.Busy -> ttStatusBusy()
                else -> ttStatusActive()
            }
            // The colour is the dot's job. Painting the name in it made a 15sp label at
            // 1.94:1 (amber) and 2.68:1 (grey) on the light canvas; `ttLabel` is the ink the
            // rest of the row already uses.
            Row(
                horizontalArrangement = Arrangement.spacedBy(ttSpacing.xs),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    modifier = Modifier
                        .size(12.dp)
                        .clip(CircleShape)
                        .background(selectedColor),
                )
                ttText(
                    text = selected?.displayName ?: "Active",
                    style = ttTypography.subheadline,
                    color = ttLabel(),
                )
            }
            ttIcon(
                image = ttActionIcons.chevronDown,
                contentDescription = null,
                tint = ttLabelSecondary(),
                modifier = Modifier.size(ttSpacing.sm * 1.5f),
            )
        }

        if (expanded) {
            Popup(
                popupPositionProvider = SettingsDropdownPositionProvider,
                onDismissRequest = { expanded = false },
                properties = PopupProperties(focusable = true),
            ) {
                SettingsDropdownColumn(selection = selected) {
                    UserStatus.all.forEach { status ->
                        UserStatusOption(
                            status = status,
                            isSelected = status == selected,
                            onClick = {
                                onSelect(status)
                                expanded = false
                            },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun UserStatusOption(
    status: UserStatus,
    isSelected: Boolean,
    onClick: () -> Unit,
) {
    val statusColor = when (status) {
        UserStatus.Active -> ttStatusActive()
        UserStatus.Away -> ttStatusAway()
        UserStatus.BeRightBack -> ttStatusBeRightBack()
        UserStatus.Busy -> ttStatusBusy()
    }
    // Same ink rule as the theme options: the popup is a backed glass pane in both themes, so
    // the label is [ttSelectionLabelInk] — the token derived for a label under a selection wash.
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .dropdownOption(key = status)
            .selectable(selected = isSelected, role = Role.RadioButton, onClick = onClick)
            .padding(horizontal = ttSpacing.xl, vertical = ttSpacing.md),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(ttSpacing.sm),
    ) {
        Box(
            modifier = Modifier
                .size(12.dp)
                .clip(CircleShape)
                .background(statusColor),
        )
        ttText(
            text = status.displayName,
            style = ttTypography.body,
            color = ttSelectionLabelInk(selected = isSelected),
            modifier = Modifier.weight(1f),
            textAlign = TextAlign.Center,
        )
    }
}
