package com.tasktracker.featuresettingspresentation

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.platform.testTag
import com.tasktracker.designsystem.ttSlider
import kotlin.math.roundToInt
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupPositionProvider
import androidx.compose.ui.window.PopupProperties
import com.tasktracker.designsystem.LocalTabBarScrollUnderInset
import com.tasktracker.designsystem.ThemeScheme
import com.tasktracker.designsystem.AccentPalette
import com.tasktracker.designsystem.ttAccent
import com.tasktracker.designsystem.ttError
import com.tasktracker.designsystem.ttGlass
import com.tasktracker.designsystem.ttGlassSurface
import com.tasktracker.designsystem.ttLabel
import com.tasktracker.designsystem.ttLabelSecondary
import com.tasktracker.designsystem.ttOnAccent
import com.tasktracker.designsystem.ttRadius
import com.tasktracker.designsystem.ttSeparator
import com.tasktracker.designsystem.ttSeparatorWidth
import com.tasktracker.designsystem.ttSettingsIconSize
import com.tasktracker.designsystem.ttSettingsIcons
import com.tasktracker.designsystem.ttSpacing
import com.tasktracker.designsystem.ttIsDarkTheme
import com.tasktracker.designsystem.ttText
import com.tasktracker.designsystem.ttMonospacedDigits
import com.tasktracker.designsystem.ttTypography
import com.tasktracker.designsystem.ttIcon
import com.tasktracker.featuresettingsdomain.AppVersion
import com.tasktracker.featuresettingsdomain.ConnectivitySummary
import com.tasktracker.featuresettingsdomain.SyncActivity
import com.tasktracker.featuresettingsdomain.SyncOutcome
import com.tasktracker.resources.Res
import com.tasktracker.resources.settings_about_section
import com.tasktracker.resources.settings_account_section
import com.tasktracker.resources.settings_activity_failed
import com.tasktracker.resources.settings_activity_idle
import com.tasktracker.resources.settings_activity_retrying
import com.tasktracker.resources.settings_activity_signed_out
import com.tasktracker.resources.settings_activity_syncing
import com.tasktracker.resources.settings_activity_up_to_date
import com.tasktracker.resources.settings_activity_waiting
import com.tasktracker.resources.settings_connectivity_needs_connection
import com.tasktracker.resources.settings_connectivity_offline
import com.tasktracker.resources.settings_connectivity_online
import com.tasktracker.resources.settings_connectivity_unknown
import com.tasktracker.resources.settings_connection_section
import com.tasktracker.resources.settings_force_offline
import com.tasktracker.resources.settings_glass_label
import com.tasktracker.resources.settings_glass_reduced
import com.tasktracker.resources.settings_appearance_section
import com.tasktracker.resources.settings_outcome_already_running
import com.tasktracker.resources.settings_outcome_offline
import com.tasktracker.resources.settings_sign_out
import com.tasktracker.resources.settings_status_label
import com.tasktracker.resources.settings_sync_now
import com.tasktracker.resources.settings_sync_section
import com.tasktracker.resources.settings_sync_stuck
import com.tasktracker.resources.settings_sync_waiting
import com.tasktracker.resources.settings_theme_label
import com.tasktracker.resources.settings_title
import com.tasktracker.resources.settings_version
import org.jetbrains.compose.resources.stringResource

/**
 * Settings: appearance, the connection, the state of sync, and the way out.
 *
 * ## Every reading arrives as a parameter, and none of them are read from storage here
 *
 * This is a function of its arguments and owns no state, like every other screen in the app:
 * [SettingsModel] holds the readings and this composable turns them into rows. The one thing
 * that *is* passed in rather than reached for is the theme — [themeScheme] and [onThemeChange]
 * — and the reason is the original's own: an appearance preference is read synchronously and
 * has to be right on the first frame, and it is not something this feature owns. Routing it
 * through the settings port would make the theme a server-reachable concern, and reading it
 * from a preferences store here would give this module an edge to
 * `:packages:user-defaults` that the architecture table does not allow. So the shell owns it
 * and hands it down.
 *
 * ## Rows appear only when their numbers are non-zero
 *
 * The queue depth and the dead-letter count are each rendered only when
 * [SyncActivity.outstandingCount] and [SyncActivity.stuckCount] are non-null. The quiet state
 * cannot then be mistaken for the alarming one: a screen that always showed "Waiting to sync: 0"
 * reads as a stuck app, which is the opposite of what an empty queue means.
 *
 * ## The Sync Now button is disabled only while a cycle is running
 *
 * Not for an offline path, and not for a previous outcome. A control that goes dead for a
 * reason the user cannot see is how a dead button gets reported as a bug — so an offline tap
 * is *answered*, with [SyncOutcome.Offline] rendered as the sentence explaining that the edits
 * are queued and safe.
 */
@Composable
fun SettingsScreen(
    connectivity: ConnectivitySummary,
    activity: SyncActivity,
    isForceOffline: Boolean,
    themeScheme: ThemeScheme,
    version: AppVersion,
    commandOutcome: SyncOutcome?,
    onThemeChange: (ThemeScheme) -> Unit,
    onForceOfflineChange: (Boolean) -> Unit,
    onSyncNow: () -> Unit,
    onSignOut: () -> Unit,
    modifier: Modifier = Modifier,
    accentPalette: AccentPalette = AccentPalette.Classic,
    onAccentPaletteChange: (AccentPalette) -> Unit = {},
    glassIntensity: Float = 1f,
    onGlassIntensityPreview: (Float) -> Unit = {},
    onGlassIntensityCommit: (Float) -> Unit = {},
) {
    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            // Top only: bottom padding here would shrink the *list's viewport*, and the sign-out row
            // would then never reach the screen's bottom edge — which is the bound the scroll is
            // supposed to have. Its clearance from the glass is `contentPadding`, below.
            .padding(horizontal = ttSpacing.xxl)
            .padding(top = ttSpacing.xl)
            .testTag(SettingsScreenTag),
        // Scroll range under the floating tab bar, not viewport inset — see
        // `LocalTabBarScrollUnderInset`. This is the list whose last row is the sign-out button,
        // so an inset that is a few dp short is a sign-out button parked under the glass. The
        // `ttSpacing.xl` is clearance rather than decoration: it is the same air the board gives
        // its lanes, and without it the last row comes to rest flush against the capsule's top
        // edge instead of above it.
        contentPadding = PaddingValues(bottom = ttSpacing.xl + LocalTabBarScrollUnderInset.current),
        verticalArrangement = Arrangement.spacedBy(ttSpacing.md),
    ) {
        item {
            ttText(
                text = stringResource(Res.string.settings_title),
                style = ttTypography.largeTitle,
                color = ttLabel(),
                modifier = Modifier.padding(bottom = ttSpacing.xs),
            )
        }
        item {
            SettingsSection(title = stringResource(Res.string.settings_appearance_section)) {
                ThemePickerRow(
                    selected = themeScheme,
                    onSelect = onThemeChange,
                )
                SettingsDivider()
                AccentPalettePickerRow(
                    selected = accentPalette,
                    onSelect = onAccentPaletteChange,
                )
                SettingsDivider()
                GlassIntensityRow(
                    intensity = glassIntensity,
                    onPreview = onGlassIntensityPreview,
                    onCommit = onGlassIntensityCommit,
                )
            }
        }
        item {
            SettingsSection(title = stringResource(Res.string.settings_connection_section)) {
                SettingsIconRow(
                    label = stringResource(Res.string.settings_status_label),
                    value = connectivityLabel(connectivity),
                    icon = ttSettingsIcons.connectivity,
                )
                SettingsDivider()
                SettingsToggleRow(
                    label = stringResource(Res.string.settings_force_offline),
                    isOn = isForceOffline,
                    onToggle = onForceOfflineChange,
                )
            }
        }
        item {
            SettingsSection(title = stringResource(Res.string.settings_sync_section)) {
                SettingsIconRow(
                    label = stringResource(Res.string.settings_status_label),
                    value = activityLabel(activity.phase),
                    icon = ttSettingsIcons.sync,
                )
                // The queue depth is the number a user actually cares about: it is how many of
                // their edits are still only on this device.
                activity.outstandingCount?.let { outstanding ->
                    SettingsDivider()
                    SettingsIconRow(
                        label = stringResource(Res.string.settings_sync_waiting),
                        value = outstanding.toString(),
                        icon = ttSettingsIcons.sync,
                        modifier = Modifier.testTag(SettingsWaitingRowTag),
                    )
                }
                // Dead rows are kept rather than dropped, so this can be non-zero, and it is
                // then the only sign that something is stuck.
                activity.stuckCount?.let { stuck ->
                    SettingsDivider()
                    SettingsIconRow(
                        label = stringResource(Res.string.settings_sync_stuck),
                        value = stuck.toString(),
                        icon = ttSettingsIcons.sync,
                        valueColor = ttError(),
                        modifier = Modifier.testTag(SettingsStuckRowTag),
                    )
                }
                if (activity.failures.isNotEmpty()) {
                    SettingsDivider()
                    ttText(
                        text = activity.failures.joinToString(separator = "\n"),
                        style = ttTypography.footnote,
                        color = ttLabelSecondary(),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = ttSpacing.sm)
                            .testTag(SettingsFailuresTag),
                    )
                }
                // Only a request that could not start lands here. The button below is
                // deliberately *not* disabled for it: greying a control out for a reason the
                // user cannot see is how a dead button gets reported as a bug.
                commandOutcome?.let { outcome ->
                    outcomeMessage(outcome)?.let { message ->
                        SettingsDivider()
                        ttText(
                            text = message,
                            style = ttTypography.footnote,
                            color = ttError(),
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = ttSpacing.sm)
                                .testTag(SettingsOutcomeTag),
                        )
                    }
                }
                SettingsDivider()
                SettingsActionRow(
                    label = stringResource(Res.string.settings_sync_now),
                    icon = ttSettingsIcons.sync,
                    tint = ttAccent(),
                    onClick = onSyncNow,
                    enabled = !activity.isSyncing,
                    modifier = Modifier.testTag(SettingsSyncNowTag),
                    // The one state that disables it: a cycle already running, which the status
                    // row above is already saying out loud. Offline is *not* a reason — that
                    // tap has to be answerable, and the answer is what lands above.
                )
            }
        }
        item {
            SettingsSection(title = stringResource(Res.string.settings_account_section)) {
                // Through the port, which the app backs with the session coordinator: that is
                // what forgets the credentials, ends the session-scoped state and routes back
                // to sign-in. Tearing down from here would leave the tokens behind.
                SettingsActionRow(
                    label = stringResource(Res.string.settings_sign_out),
                    icon = ttSettingsIcons.account,
                    tint = ttError(),
                    onClick = onSignOut,
                    modifier = Modifier.testTag(SettingsSignOutTag),
                )
            }
        }
        // The About section hides itself entirely rather than rendering an empty one: a
        // version row with nothing beside it is a row that says the app does not know its own
        // version, which is only true for a build with no version keys at all.
        version.display?.let { displayed ->
            item {
                SettingsSection(title = stringResource(Res.string.settings_about_section)) {
                    SettingsIconRow(
                        label = stringResource(Res.string.settings_version),
                        value = displayed,
                        icon = ttSettingsIcons.about,
                        modifier = Modifier.testTag(SettingsVersionRowTag),
                    )
                }
            }
        }
    }
}

@Composable
private fun AccentPalettePickerRow(
    selected: AccentPalette,
    onSelect: (AccentPalette) -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(vertical = ttSpacing.sm),
        verticalArrangement = Arrangement.spacedBy(ttSpacing.sm),
    ) {
        ttText(
            text = AccentPalette.pickerTitle,
            style = ttTypography.subheadline,
            color = ttLabel(),
        )
        Row(horizontalArrangement = Arrangement.spacedBy(ttSpacing.xs)) {
            AccentPalette.all.forEach { palette ->
                val isSelected = palette == selected
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(ttRadius.control))
                        .selectable(selected = isSelected, role = Role.RadioButton) {
                            onSelect(palette)
                        }
                        .padding(vertical = ttSpacing.sm),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(ttSpacing.xs),
                ) {
                    Box(
                        modifier = Modifier
                            .size(32.dp)
                            .clip(CircleShape)
                            .background(
                                Brush.horizontalGradient(listOf(palette.light, palette.dark)),
                            )
                            .border(
                                width = if (isSelected) 2.dp else 1.dp,
                                color = if (isSelected) ttAccent() else ttSeparator(),
                                shape = CircleShape,
                            ),
                    )
                    ttText(
                        text = palette.title,
                        style = ttTypography.footnote,
                        color = if (isSelected) ttAccent() else ttLabelSecondary(),
                        // Two lines, not one: the second palette ("Classic Light") was 13
                        // characters on a six-column row — long enough to be clipped on a
                        // phone. A caption that wraps to a second line stays whole instead of
                        // ending in an ellipsis.
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        textAlign = TextAlign.Center,
                    )
                }
            }
        }
    }
}

@Composable
private fun GlassIntensityRow(
    intensity: Float,
    onPreview: (Float) -> Unit,
    onCommit: (Float) -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(vertical = ttSpacing.sm),
        verticalArrangement = Arrangement.spacedBy(ttSpacing.sm),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            ttText(
                text = stringResource(Res.string.settings_glass_label),
                style = ttTypography.subheadline,
                color = ttLabel(),
            )
            ttText(
                // Named at the low end rather than left as "0%": the bottom of this slider is
                // Reduce Transparency, which is a state with a name in the OS, and showing the
                // user a bare zero for it would describe the control rather than the result.
                text = glassAmountLabel(intensity),
                style = ttTypography.footnote,
                color = ttLabelSecondary(),
                modifier = Modifier.testTag(GlassAmountTag),
            )
        }
        ttSlider(
            value = intensity,
            onValueChange = onPreview,
            onValueChangeFinished = onCommit,
            modifier = Modifier.testTag(GlassSliderTag),
        )
    }
}

/**
 * The slider's readout.
 *
 * A percentage everywhere except the very bottom, which reads as the platform's Reduce
 * Transparency. The threshold is one step of the slider's own quantisation rather than a
 * round number, so there is no band of values near the bottom that are Reduce Transparency but
 * do not say so.
 */
@Composable
private fun glassAmountLabel(intensity: Float): String =
    if (intensity <= 0.005f) {
        stringResource(Res.string.settings_glass_reduced)
    } else {
        "${(intensity * 100f).roundToInt()}%"
    }

/** A titled, inset group in the shared Settings list. */
@Composable
private fun SettingsSection(
    title: String,
    content: @Composable () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(ttSpacing.xs)) {
        ttText(
            text = title,
            style = ttTypography.caption,
            color = ttLabelSecondary(),
            modifier = Modifier.padding(start = ttSpacing.xs, bottom = ttSpacing.xs),
        )
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .ttGlassSurface(shape = RoundedCornerShape(ttRadius.surface))
                .padding(horizontal = ttSpacing.xl, vertical = ttSpacing.xs),
            content = { content() },
        )
    }
}

/** A compact label/value row with the leading symbol and trailing setting value. */
@Composable
private fun SettingsIconRow(
    label: String,
    value: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    modifier: Modifier = Modifier,
    valueColor: Color = ttLabelSecondary(),
    iconTint: Color = ttAccent(),
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .padding(vertical = ttSpacing.xs),
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
private fun SettingsIconWell(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
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
private fun SettingsDivider() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = ttSettingsIconSize.tile + ttSpacing.md)
            .height(ttSeparatorWidth)
            .background(ttSeparator()),
    )
}

/**
 * A capsule switch with a full-row accessible tap target.
 */
@Composable
private fun SettingsToggleRow(
    label: String,
    isOn: Boolean,
    onToggle: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .toggleable(value = isOn, role = Role.Switch, onValueChange = onToggle)
            .testTag(SettingsForceOfflineTag),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(ttSpacing.md),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            SettingsIconWell(icon = ttSettingsIcons.offline, tint = ttAccent())
            ttText(text = label, style = ttTypography.body, color = ttLabel())
        }
        Box(
            modifier = Modifier
                .width(51.dp)
                .height(31.dp)
                .ttGlassSurface(
                    shape = CircleShape,
                    tint = if (isOn) ttAccent() else ttLabelSecondary(),
                ),
        ) {
            Box(
                modifier = Modifier
                    .align(if (isOn) Alignment.CenterEnd else Alignment.CenterStart)
                    .padding(3.dp)
                    .size(25.dp)
                    .background(ttOnAccent, CircleShape),
            )
        }
    }
}

@Composable
private fun SettingsActionRow(
    label: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    tint: Color,
    onClick: () -> Unit,
    enabled: Boolean = true,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .clip(RoundedCornerShape(ttRadius.row))
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(ttSpacing.md),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            SettingsIconWell(icon = icon, tint = tint)
            ttText(text = label, style = ttTypography.body, color = tint)
        }
    }
}

/**
 * The three schemes, as one row of segments.
 *
 * `ThemeScheme.title` rather than three strings in `Res`: the design system already owns these
 * three words as part of the type it defines, and a second copy in `strings.xml` would be two
 * sources for one sentence.
 */
@Composable
private fun ThemePickerRow(
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
            ttText(text = "⌄", style = ttTypography.body, color = ttLabelSecondary())
        }

        if (expanded) {
            Popup(
                popupPositionProvider = SettingsDropdownPositionProvider,
                onDismissRequest = { expanded = false },
                properties = PopupProperties(focusable = true),
            ) {
                Column(
                    modifier = Modifier
                        .width(216.dp)
                        .ttGlassSurface(
                            shape = RoundedCornerShape(ttRadius.control),
                            fillAlpha = 0.81f,
                        )
                        .padding(vertical = ttSpacing.xs),
                ) {
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
 * The selected option is signalled twice: the old way (accent title + a check mark) and a
 * ring. The ring is not decoration — in the light theme the title and check are rendered in
 * `Color.White` on the glass, so the check mark's position was the only thing telling the
 * selected row apart from its neighbours. The ring reuses the exact accent-circle vocabulary
 * from [AccentPalettePickerRow] so the page has one kind of emphasis, not two.
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
            .clip(RoundedCornerShape(ttRadius.control))
            .border(
                width = if (isSelected) 2.dp else 1.dp,
                color = if (isSelected) ttAccent() else if (ttIsDarkTheme()) {
                    Color.White.copy(alpha = ttGlass.HAIRLINE_ALPHA)
                } else {
                    Color.Black.copy(alpha = ttGlass.LIGHT_HAIRLINE_ALPHA)
                },
                shape = RoundedCornerShape(ttRadius.control),
            )
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

private object SettingsDropdownPositionProvider : PopupPositionProvider {
    override fun calculatePosition(
        anchorBounds: IntRect,
        windowSize: IntSize,
        layoutDirection: LayoutDirection,
        popupContentSize: IntSize,
    ): IntOffset {
        val maxX = (windowSize.width - popupContentSize.width).coerceAtLeast(0)
        val preferredX = when (layoutDirection) {
            LayoutDirection.Ltr -> anchorBounds.right - popupContentSize.width
            LayoutDirection.Rtl -> anchorBounds.left
        }
        val below = anchorBounds.bottom
        val above = anchorBounds.top - popupContentSize.height
        val y = if (below + popupContentSize.height <= windowSize.height) below else above
        return IntOffset(
            x = preferredX.coerceIn(0, maxX),
            y = y.coerceIn(0, (windowSize.height - popupContentSize.height).coerceAtLeast(0)),
        )
    }
}

/** One connectivity state, as the word the Connection row shows. */
@Composable
private fun connectivityLabel(connectivity: ConnectivitySummary): String =
    stringResource(
        when (connectivity) {
            ConnectivitySummary.Online -> Res.string.settings_connectivity_online
            ConnectivitySummary.Offline -> Res.string.settings_connectivity_offline
            ConnectivitySummary.NeedsConnection -> Res.string.settings_connectivity_needs_connection
            ConnectivitySummary.Unknown -> Res.string.settings_connectivity_unknown
        },
    )

/** One sync phase, as the word the Sync row shows. Exhaustive with no `else`, on purpose. */
@Composable
private fun activityLabel(phase: SyncActivity.Phase): String =
    stringResource(
        when (phase) {
            SyncActivity.Phase.Idle -> Res.string.settings_activity_idle
            SyncActivity.Phase.WaitingForConnection -> Res.string.settings_activity_waiting
            SyncActivity.Phase.Syncing -> Res.string.settings_activity_syncing
            SyncActivity.Phase.UpToDate -> Res.string.settings_activity_up_to_date
            SyncActivity.Phase.Retrying -> Res.string.settings_activity_retrying
            SyncActivity.Phase.Failed -> Res.string.settings_activity_failed
            SyncActivity.Phase.SignedOut -> Res.string.settings_activity_signed_out
        },
    )

/**
 * The sentence for an outcome, or `null` for [SyncOutcome.Started].
 *
 * `null` for the happy path rather than an empty string, so the row is not rendered at all:
 * saying anything about a sync that started would be noise, and an empty text node in the
 * tree is a thing a test can accidentally match.
 */
@Composable
private fun outcomeMessage(outcome: SyncOutcome): String? = when (outcome) {
    SyncOutcome.Started -> null
    SyncOutcome.AlreadyRunning -> stringResource(Res.string.settings_outcome_already_running)
    SyncOutcome.Offline -> stringResource(Res.string.settings_outcome_offline)
}

const val SettingsScreenTag = "settings.screen"
const val SettingsSyncNowTag = "settings.syncNow"
const val SettingsSignOutTag = "settings.signOut"
const val SettingsForceOfflineTag = "settings.forceOffline"
const val SettingsWaitingRowTag = "settings.waitingRow"
const val SettingsStuckRowTag = "settings.stuckRow"
const val SettingsFailuresTag = "settings.failures"
const val SettingsOutcomeTag = "settings.outcome"
const val SettingsVersionRowTag = "settings.versionRow"
const val ThemeDropdownTag = "settings.themeDropdown"
const val GlassSliderTag = "settings.glassSlider"
const val GlassAmountTag = "settings.glassAmount"

/** Per-scheme test tag, so a scenario can tap "Dark" without a positional matcher. */
fun ThemeOptionTag(scheme: ThemeScheme): String = "settings.theme.${scheme.wireValue}"
