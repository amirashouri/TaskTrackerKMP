package com.tasktracker.featureprojectspresentation

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
import com.tasktracker.core.DateProvider
import com.tasktracker.designsystem.ThemeScheme
import com.tasktracker.designsystem.ttCanvas
import com.tasktracker.designsystem.ttLabel
import com.tasktracker.designsystem.ttLabelSecondary
import com.tasktracker.designsystem.ttSpacing
import com.tasktracker.designsystem.ttText
import com.tasktracker.designsystem.ttTheme
import com.tasktracker.designsystem.ttTypography
import com.tasktracker.featureprojectsdomain.ProjectRepositoryPort

/**
 * The collaborators every screen in this module needs, supplied from outside.
 *
 * A container rather than two separate composition locals because a model needs both or
 * neither: [ProjectRepositoryPort] without a [DateProvider] cannot run a single use case,
 * since every one of them takes the instant from the clock and not from the store. Two
 * locals would also let a caller provide a repository from one composition and a clock
 * from another, and the resulting model would stamp records with a time that had nothing
 * to do with the data it was writing.
 *
 * Typed as the port and the interface, never the Room repository and never
 * `SystemDateProvider`. As with auth, that is enforced rather than merely intended: this
 * module does not depend on `:packages:feature-projects-data` or on `:packages:core`'s
 * implementations, so naming either would not compile.
 */
class ProjectEnvironment(
    val repository: ProjectRepositoryPort,
    val dateProvider: DateProvider,
)

/**
 * How a view reaches the projects feature.
 *
 * A `staticCompositionLocalOf`, and nullable, for the same two reasons as
 * `LocalAuthSession`: the environment is fixed for the lifetime of a composition so
 * there is nothing to gain by tracking reads, and making it nullable turns "the
 * composition root has not been wired yet" into a visible message on first composition
 * rather than a crash on a screen nobody was supposed to reach.
 */
val LocalProjectEnvironment = staticCompositionLocalOf<ProjectEnvironment?> { null }

/** The projects environment, or `null` when the composition root supplied none. */
val currentProjectEnvironment: ProjectEnvironment?
    @Composable get() = LocalProjectEnvironment.current

/** Supplies a projects environment to a subtree. */
@Composable
fun ProvideProjectEnvironment(
    environment: ProjectEnvironment,
    content: @Composable () -> Unit,
) {
    CompositionLocalProvider(LocalProjectEnvironment provides environment, content = content)
}

/**
 * Shown instead of any projects screen when the composition root did not supply an
 * environment.
 *
 * One gate for the whole feature rather than one per screen, because the failure is
 * singular - nothing was wired - and eight identical gates would each need a test to
 * prove they say the same thing. Deliberately not an error *banner* for the same reason
 * as the auth gate: this is not a failed attempt, it is a build that cannot read any
 * projects, and styling it as a failure suggests the user did something wrong.
 */
@Composable
fun MissingProjectEnvironmentView(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .testTag(MissingProjectEnvironmentTag)
            .background(ttCanvas()),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            modifier = Modifier.padding(ttSpacing.screenGutterLarge),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(ttSpacing.sm),
        ) {
            ttText(
                text = "Projects unavailable",
                style = ttTypography.titleSmall,
                color = ttLabel(),
            )
            ttText(
                text = "The app was built without a projects data source, so no project " +
                    "can be shown.",
                style = ttTypography.footnote,
                color = ttLabelSecondary(),
            )
        }
    }
}

/** The gate's test tag, so a test asserts on the gate rather than on its copy. */
const val MissingProjectEnvironmentTag = "projects.missingEnvironment"

@Preview(name = "Missing projects environment", showBackground = true)
@Composable
private fun MissingProjectEnvironmentPreview() {
    ttTheme(ThemeScheme.System) {
        MissingProjectEnvironmentView()
    }
}
