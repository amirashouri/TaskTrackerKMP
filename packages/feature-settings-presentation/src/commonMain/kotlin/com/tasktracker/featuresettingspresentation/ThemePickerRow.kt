package com.tasktracker.featuresettingspresentation

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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import com.tasktracker.designsystem.ThemeScheme
import com.tasktracker.designsystem.ttAccent
import com.tasktracker.designsystem.ttActionIcons
import com.tasktracker.designsystem.ttIcon
import com.tasktracker.designsystem.ttIsDarkTheme
import com.tasktracker.designsystem.ttLabel
import com.tasktracker.designsystem.ttLabelSecondary
import com.tasktracker.designsystem.ttSpacing
import com.tasktracker.designsystem.ttText
import com.tasktracker.designsystem.ttTypography
import com.tasktracker.resources.Res
import com.tasktracker.resources.settings_theme_label
import org.jetbrains.compose.resources.stringResource
import com.tasktracker.designsystem.ttSettingsIcons

const val ThemeDropdownTag = "settings.themeDropdown"

/** Per-scheme test tag, so a scenario can tap "Dark" without a positional matcher. */
fun ThemeOptionTag(scheme: ThemeScheme): String = "settings.theme.${scheme.wireValue}"

/**
 * The three schemes, as one row of segments.
 *
 * `ThemeScheme.title` rather than three strings in `Res`: the design system already owns these
 * three words as part of the type it defines, and a second copy in `strings.xml` would be two
 * sources for one sentence.
 */
@Composable
internal fun ThemePickerRow(
    selected: ThemeScheme,
    onSelect: (ThemeScheme) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }

    Box(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 48.dp)
                .clickable(role = Role.Button) { expanded = !expanded }
                .testTag(ThemeDropdownTag),
            horizontalArrangement = Arrangement.spacedBy(ttSpacing.md),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            SettingsIconWell(icon = ttSettingsIcons.appearance, tint = ttAccent())
            ttText(
                text = stringResource(Res.string.settings_theme_label),
                style = ttTypography.body,
                color = ttLabel(),
            )
            Spacer(modifier = Modifier.weight(1f))
            ttText(
                text = selected.title,
                style = ttTypography.subheadline,
                color = ttLabelSecondary(),
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
                    ThemeScheme.all.forEach { scheme ->
                        ThemePickerOption(
                            scheme = scheme,
                            isSelected = scheme == selected,
                            onClick = {
                                onSelect(scheme)
                                expanded = false
                            },
                        )
                    }
                }
            }
        }
    }
}

/**
 * One theme scheme, as a selectable option row in the theme dropdown.
 *
 * `internal`, not `private`, so the pixel test can compose one option in the main root
 * instead of opening the `Popup` — a popup is a second composition root, and mapping its
 * coordinates onto a captured frame is guesswork (`MonthArrowRenderTest` documents the same
 * rule for `TTDayGrid`).
 */
@Composable
internal fun ThemePickerOption(
    scheme: ThemeScheme,
    isSelected: Boolean,
    onClick: () -> Unit,
) {
    val isDarkTheme = ttIsDarkTheme()
    val optionLabel = if (isDarkTheme) ttLabel() else Color.White
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .dropdownOptionSurface(isSelected = isSelected)
            .selectable(
                selected = isSelected,
                role = Role.RadioButton,
                onClick = onClick,
            )
            .testTag(ThemeOptionTag(scheme))
            .padding(horizontal = ttSpacing.xl, vertical = ttSpacing.md),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(ttSpacing.sm),
    ) {
        ttText(
            text = scheme.title,
            style = ttTypography.body,
            color = if (isSelected && isDarkTheme) ttAccent() else optionLabel,
            modifier = Modifier.weight(1f),
            textAlign = TextAlign.Center,
        )
    }
}
