package com.tasktracker.featuresettingspresentation

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.tooling.preview.Preview
import com.tasktracker.designsystem.ThemeScheme
import com.tasktracker.designsystem.ttCanvas
import com.tasktracker.designsystem.ttLabel
import com.tasktracker.designsystem.ttLabelSecondary
import com.tasktracker.designsystem.ttSpacing
import com.tasktracker.designsystem.ttText
import com.tasktracker.designsystem.ttTheme
import com.tasktracker.designsystem.ttTypography
import com.tasktracker.featuresettingsdomain.SettingsPort
import com.tasktracker.resources.Res
import com.tasktracker.resources.settings_missing_body
import com.tasktracker.resources.settings_missing_title
import org.jetbrains.compose.resources.stringResource

/**
 * How the Settings screen reaches its port.
 *
 * The port itself, not a wrapper class: there is exactly one collaborator, so an environment
 * type would be a container around a single field.
 *
 * Typed as [SettingsPort], never as `LiveSettingsService`. That is not a preference: this
 * module does not depend on `:packages:feature-settings-data`, so naming the concrete service
 * here would not compile — the same edge the projects feature's `ProjectEnvironment` relies
 * on to keep its screen unable to reach a repository.
 *
 * Nullable so a screen the app never installed a service for renders an explanation instead
 * of trapping, which is also what keeps it previewable.
 */
val LocalSettingsPort = staticCompositionLocalOf<SettingsPort?> { null }

/** The settings port, or `null` when the composition root supplied none. */
val currentSettingsPort: SettingsPort?
    @Composable get() = LocalSettingsPort.current

/** Supplies a settings port to a subtree. */
@Composable
fun ProvideSettingsPort(
    port: SettingsPort,
    content: @Composable () -> Unit,
) {
    CompositionLocalProvider(LocalSettingsPort provides port, content = content)
}

/**
 * Shown instead of the screen when the composition root supplied no service.
 *
 * One gate for the screen rather than an error banner, for the reason
 * `MissingProjectEnvironmentView` gives: this is not a failed request, it is a build that
 * cannot read any settings, and styling it as a failure suggests the user did something wrong.
 */
@Composable
fun MissingSettingsServiceView(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .testTag(MissingSettingsServiceTag)
            .background(ttCanvas()),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            modifier = Modifier.padding(ttSpacing.screenGutterLarge),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(ttSpacing.sm),
        ) {
            ttText(
                text = stringResource(Res.string.settings_missing_title),
                style = ttTypography.titleSmall,
                color = ttLabel(),
            )
            ttText(
                text = stringResource(Res.string.settings_missing_body),
                style = ttTypography.footnote,
                color = ttLabelSecondary(),
            )
        }
    }
}

/** The gate's test tag, so a test asserts on the gate rather than on its copy. */
const val MissingSettingsServiceTag = "settings.missingService"

@Preview(name = "Missing settings service", showBackground = true)
@Composable
private fun MissingSettingsServicePreview() {
    ttTheme(ThemeScheme.System) {
        MissingSettingsServiceView()
    }
}