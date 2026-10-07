package com.tasktracker.featuresettingspresentation

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import com.tasktracker.designsystem.ttAccent
import com.tasktracker.designsystem.ttActionIcons
import com.tasktracker.designsystem.ttIcon
import com.tasktracker.designsystem.ttIsDarkTheme
import com.tasktracker.designsystem.ttLabel
import com.tasktracker.designsystem.ttLabelSecondary
import com.tasktracker.designsystem.ttSpacing
import com.tasktracker.designsystem.ttStatusActive
import com.tasktracker.designsystem.ttStatusAway
import com.tasktracker.designsystem.ttStatusBeRightBack
import com.tasktracker.designsystem.ttStatusBusy
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
                .clickable(role = Role.Button) { expanded = !expanded },
            horizontalArrangement = Arrangement.spacedBy(ttSpacing.md),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            SettingsIconWell(icon = ttActionIcons.person, tint = ttAccent())
            ttText(
                text = stringResource(Res.string.settings_user_status),
                style = ttTypography.body,
                color = ttLabel(),
            )
            Spacer(modifier = Modifier.weight(1f))
            val selectedColor = when (selected) {
                UserStatus.Active -> ttStatusActive
                UserStatus.Away -> ttStatusAway
                UserStatus.BeRightBack -> ttStatusBeRightBack
                UserStatus.Busy -> ttStatusBusy
                else -> ttStatusActive
            }
            ttText(
                text = selected?.displayName ?: "Active",
                style = ttTypography.subheadline,
                color = selectedColor,
            )
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
                SettingsDropdownColumn {
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
        UserStatus.Active -> ttStatusActive
        UserStatus.Away -> ttStatusAway
        UserStatus.BeRightBack -> ttStatusBeRightBack
        UserStatus.Busy -> ttStatusBusy
    }
    val isDarkTheme = ttIsDarkTheme()
    val optionLabel = if (isDarkTheme) ttLabel() else Color.White
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .dropdownOptionSurface(isSelected = isSelected)
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
            color = if (isSelected && isDarkTheme) ttAccent() else optionLabel,
            modifier = Modifier.weight(1f),
            textAlign = TextAlign.Center,
        )
    }
}
