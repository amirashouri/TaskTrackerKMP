package com.tasktracker.featureauthpresentation

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
import com.tasktracker.designsystem.ttCanvas
import com.tasktracker.designsystem.ttLabel
import com.tasktracker.designsystem.ttLabelSecondary
import com.tasktracker.designsystem.ttSpacing
import com.tasktracker.designsystem.ttText
import com.tasktracker.designsystem.ThemeScheme
import com.tasktracker.designsystem.ttTheme
import com.tasktracker.designsystem.ttTypography
import com.tasktracker.featureauthdomain.AuthSessionPort

/**
 * How a view reaches the auth feature.
 *
 * Typed as [AuthSessionPort], never as the concrete repository. That is not a preference:
 * this module does not depend on `:packages:feature-auth-data`, so naming the
 * implementation would not compile — which is the point. A presentation module that can
 * name its repository will eventually call something on it that only the repository
 * sensibly knows how to do, and the seam is gone the first time it does.
 *
 * ## Why the value is nullable
 *
 * The original makes the environment value optional so a view with no port renders an
 * explanation instead of trapping, and that is what keeps the sign-in screen previewable
 * against a fake. The same reason holds here for a harder case: this module is built long
 * before anything binds a real port, and a non-null local would make "the composition root
 * has not been wired yet" a crash on first composition rather than a visible message.
 *
 * A `staticCompositionLocalOf` rather than a `compositionLocalOf`: the port is fixed for
 * the lifetime of a composition and never changes per-subtree the way a theme does, so
 * there is nothing to be gained by tracking reads, and skipping the tracking avoids
 * recomposing the sign-in form on every write of an unrelated value.
 */
val LocalAuthSession = staticCompositionLocalOf<AuthSessionPort?> { null }

/** The session port, or `null` when the composition root supplied none. */
val currentAuthSession: AuthSessionPort?
    @Composable get() = LocalAuthSession.current

/**
 * Supplies a session port to a subtree.
 *
 * Separate from [LocalAuthSession] so the preview and the composition root read as two
 * different intents at the call site, even though both are a `CompositionLocalProvider`. The
 * alternative — telling them apart by argument — is a distinction nothing in the type system
 * carries.
 */
@Composable
fun ProvideAuthSession(session: AuthSessionPort, content: @Composable () -> Unit) {
    CompositionLocalProvider(LocalAuthSession provides session, content = content)
}

/**
 * Shown instead of sign-in when the composition root did not supply a session port.
 *
 * The original uses `ContentUnavailableView`, which is a SwiftUI type with no Compose
 * equivalent, so this is the same content laid out by hand: a title, a line of explanation,
 * and nothing to press. Deliberately not an error *banner* — this is not a failed attempt,
 * it is a build that cannot sign anyone in, and styling it as one would suggest the user
 * did something wrong.
 */
@Composable
fun MissingAuthSessionView(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .testTag(MissingAuthSessionTag)
            .background(ttCanvas()),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            modifier = Modifier.padding(ttSpacing.screenGutterLarge),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(ttSpacing.sm),
        ) {
            ttText(
                text = "Sign-in unavailable",
                style = ttTypography.titleSmall,
                color = ttLabel(),
            )
            ttText(
                text = "The app was built without an auth session, so nobody can sign in.",
                style = ttTypography.footnote,
                color = ttLabelSecondary(),
            )
        }
    }
}

/** The gate's test tag, so a test asserts on the gate rather than on its copy. */
const val MissingAuthSessionTag = "auth.missingSession"

@Preview(name = "Missing auth session", showBackground = true)
@Composable
private fun MissingAuthSessionPreview() {
    ttTheme(ThemeScheme.System) {
        MissingAuthSessionView()
    }
}
