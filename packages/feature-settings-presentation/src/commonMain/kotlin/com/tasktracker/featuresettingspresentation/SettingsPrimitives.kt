package com.tasktracker.featuresettingspresentation

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.tasktracker.designsystem.ttAccent
import com.tasktracker.designsystem.ttActionIcons
import com.tasktracker.designsystem.ttGlassSurface
import com.tasktracker.designsystem.ttIcon
import com.tasktracker.designsystem.ttLabel
import com.tasktracker.designsystem.ttLabelSecondary
import com.tasktracker.designsystem.ttMonospacedDigits
import com.tasktracker.designsystem.ttOnAccent
import com.tasktracker.designsystem.ttRadius
import com.tasktracker.designsystem.ttSeparator
import com.tasktracker.designsystem.ttSeparatorWidth
import com.tasktracker.designsystem.ttSettingsIconSize
import com.tasktracker.designsystem.ttSpacing
import com.tasktracker.designsystem.ttText
import com.tasktracker.designsystem.ttTypography

/** A glass group in the shared Settings list. */
@Composable
internal fun SettingsSection(
    content: @Composable () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .ttGlassSurface(shape = RoundedCornerShape(ttRadius.surface))
            .padding(horizontal = ttSpacing.xl, vertical = ttSpacing.md),
        content = { content() },
    )
}

/** A compact label/value row with the leading symbol and trailing setting value. */
@Composable
internal fun SettingsIconRow(
    label: String,
    value: String,
    icon: ImageVector,
    modifier: Modifier = Modifier,
    valueColor: Color = ttLabelSecondary(),
    iconTint: Color = ttAccent(),
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .padding(vertical = ttSpacing.md),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(ttSpacing.md),
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.weight(1f),
        ) {
            SettingsIconWell(icon = icon, tint = iconTint)
            ttText(text = label, style = ttTypography.body, color = ttLabel())
        }
        ttText(
            text = value,
            style = ttTypography.subheadline.ttMonospacedDigits(),
            color = valueColor,
            textAlign = TextAlign.End,
            maxLines = 1,
        )
    }
}

@Composable
internal fun SettingsIconWell(
    icon: ImageVector,
    tint: Color,
) {
    Box(
        modifier = Modifier
            .size(ttSettingsIconSize.tile)
            .ttGlassSurface(shape = RoundedCornerShape(ttRadius.row), tint = tint),
        contentAlignment = Alignment.Center,
    ) {
        ttIcon(
            image = icon,
            contentDescription = null,
            tint = ttOnAccent,
            modifier = Modifier.size(ttSettingsIconSize.glyph),
        )
    }
}

@Composable
internal fun SettingsDivider() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = ttSettingsIconSize.tile + ttSpacing.md)
            .height(ttSeparatorWidth)
            .background(ttSeparator()),
    )
}

@Composable
internal fun SettingsActionRow(
    label: String,
    icon: ImageVector,
    tint: Color,
    onClick: () -> Unit,
    enabled: Boolean = true,
    // `null` keeps the standard legible tokens (ttLabel / ttLabelSecondary). A caller that
    // wants to flag the row as destructive (Sign Out) passes ttError() for both, which the
    // design system holds to the same 4.5:1 contrast bar as the accent.
    labelColor: Color? = null,
    chevronTint: Color? = null,
    modifier: Modifier = Modifier,
) {
    val resolvedLabelColor = labelColor ?: ttLabel()
    val resolvedChevronTint = chevronTint ?: ttLabelSecondary()
    Row(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .clip(RoundedCornerShape(ttRadius.row))
            .clickable(
                enabled = enabled,
                indication = null,
                interactionSource = remember { MutableInteractionSource() },
                role = Role.Button,
                onClick = onClick,
            ),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(
            modifier = Modifier.weight(1f),
            horizontalArrangement = Arrangement.spacedBy(ttSpacing.md),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            SettingsIconWell(icon = icon, tint = tint)
            // The accent is a *fill* colour (it sits under white text in the icon well),
            // not a foreground; painting the label in it fails the contrast bar on the
            // mid-tone dark palettes and reads as a disabled row. Use the legible
            // primary-text token instead, as SettingsIconRow already does.
            ttText(text = label, style = ttTypography.body, color = resolvedLabelColor)
        }
        // Disclosure indicator: this row navigates. A chevron reads as "tappable" in
        // every theme and contrast level, unlike an accent-coloured label, which fails
        // the bar on the mid-tone dark palettes (Navy Blue / Classic dark).
        ttIcon(
            image = ttActionIcons.chevronRight,
            contentDescription = null,
            tint = resolvedChevronTint,
            modifier = Modifier.size(ttSettingsIconSize.glyph),
        )
    }
}
