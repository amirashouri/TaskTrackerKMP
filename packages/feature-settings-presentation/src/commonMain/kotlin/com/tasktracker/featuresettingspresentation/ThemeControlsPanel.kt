package com.tasktracker.featuresettingspresentation

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.Dp
import com.tasktracker.designsystem.AccentPalette
import com.tasktracker.designsystem.LocalReducedMotion
import com.tasktracker.designsystem.TTGlassSegmentedPicker
import com.tasktracker.designsystem.ThemeScheme
import com.tasktracker.designsystem.ttActionIcons
import com.tasktracker.designsystem.ttIcon
import com.tasktracker.designsystem.ttIconSize
import com.tasktracker.designsystem.ttLabel
import com.tasktracker.designsystem.ttSpacing
import com.tasktracker.designsystem.ttText
import com.tasktracker.designsystem.ttTypography
import com.tasktracker.resources.Res
import com.tasktracker.resources.settings_theme_label
import org.jetbrains.compose.resources.stringResource

const val ThemeControlsPanelTag = "settings.themeControls"
const val ThemeControlsHeaderTag = "settings.themeControls.header"
const val ThemeControlsContentTag = "settings.themeControls.content"
const val ThemeControlsGlassSliderTag = "settings.themeControls.glassSlider"
const val ThemeControlsAccentTagPrefix = "settings.themeControls.accent"

fun ThemeControlsSchemeTag(scheme: ThemeScheme): String =
    "settings.themeControls.scheme.${scheme.wireValue}"

fun ThemeControlsAccentSwatchTag(palette: AccentPalette): String =
    AccentSwatchTag(palette, ThemeControlsAccentTagPrefix)

fun ThemeControlsAccentCellTag(palette: AccentPalette): String =
    AccentCellTag(palette, ThemeControlsAccentTagPrefix)

fun ThemeControlsAccentLensTag(palette: AccentPalette): String =
    AccentGlassTag(palette, ThemeControlsAccentTagPrefix)

/**
 * Appearance controls for the desktop sidebar.
 *
 * The settings screen owns the full-size versions of these rows; this is their compact form. It
 * takes values and callbacks rather than reaching into the shell's preferences, so the sidebar
 * and the Settings tab write through the same shell-owned state and cannot disagree. The open
 * state is one of those values: a `remember` here would die on every tab switch and could not
 * offer a different default on the Settings page.
 *
 * The header stays small when collapsed. When expanded, [contentMaxHeight] bounds the content so
 * a short desktop window scrolls the controls instead of pushing the navigation off the panel.
 */
@Composable
fun ThemeControlsPanel(
    expanded: Boolean,
    themeScheme: ThemeScheme,
    accentPalette: AccentPalette,
    glassIntensity: Float,
    onExpandedChange: (Boolean) -> Unit,
    onThemeChange: (ThemeScheme) -> Unit,
    onAccentPaletteChange: (AccentPalette) -> Unit,
    onGlassIntensityPreview: (Float) -> Unit,
    onGlassIntensityCommit: (Float) -> Unit,
    modifier: Modifier = Modifier,
    contentMaxHeight: Dp? = null,
) {
    val reducedMotion = LocalReducedMotion.current
    val chevronRotation by animateFloatAsState(
        targetValue = if (expanded) 180f else 0f,
        animationSpec = if (reducedMotion) snap() else tween(200),
        label = "themePanelChevron",
    )
    val enter = if (reducedMotion) {
        EnterTransition.None
    } else {
        expandVertically(animationSpec = tween(220)) + fadeIn(animationSpec = tween(180))
    }
    val exit = if (reducedMotion) {
        ExitTransition.None
    } else {
        shrinkVertically(animationSpec = tween(200)) + fadeOut(animationSpec = tween(150))
    }

    Column(
        modifier = modifier.testTag(ThemeControlsPanelTag),
        verticalArrangement = Arrangement.spacedBy(ttSpacing.xs),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(
                    indication = null,
                    interactionSource = remember { MutableInteractionSource() },
                    role = Role.Button,
                ) { onExpandedChange(!expanded) }
                .padding(vertical = ttSpacing.sm)
                .testTag(ThemeControlsHeaderTag),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            ttText(
                text = stringResource(Res.string.settings_theme_label),
                style = ttTypography.callout,
                color = ttLabel(),
                modifier = Modifier.weight(1f),
            )
            ttIcon(
                image = ttActionIcons.chevronDown,
                contentDescription = null,
                tint = ttLabel(),
                modifier = Modifier
                    .size(ttIconSize.close)
                    .rotate(chevronRotation),
            )
        }

        AnimatedVisibility(visible = expanded, enter = enter, exit = exit) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .then(
                        if (contentMaxHeight != null) Modifier.heightIn(max = contentMaxHeight)
                        else Modifier,
                    )
                    .verticalScroll(rememberScrollState())
                    .testTag(ThemeControlsContentTag),
                verticalArrangement = Arrangement.spacedBy(ttSpacing.md),
            ) {
                TTGlassSegmentedPicker(
                    options = ThemeScheme.all,
                    selection = themeScheme,
                    onSelect = onThemeChange,
                    testTag = { ThemeControlsSchemeTag(it) },
                    title = { it.title },
                )
                AccentPalettePickerRow(
                    selected = accentPalette,
                    onSelect = onAccentPaletteChange,
                    showNames = false,
                    tagPrefix = ThemeControlsAccentTagPrefix,
                )
                GlassIntensityRow(
                    intensity = glassIntensity,
                    onPreview = onGlassIntensityPreview,
                    onCommit = onGlassIntensityCommit,
                    sliderTag = ThemeControlsGlassSliderTag,
                )
            }
        }
    }
}
