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
import com.tasktracker.core.DateProvider
import com.tasktracker.designsystem.ThemeScheme
import com.tasktracker.designsystem.ttCanvas
import com.tasktracker.designsystem.ttLabel
import com.tasktracker.designsystem.ttLabelSecondary
import com.tasktracker.designsystem.ttSpacing
import com.tasktracker.designsystem.ttText
import com.tasktracker.designsystem.ttTheme
import com.tasktracker.designsystem.ttTypography
import com.tasktracker.featureprojectsdomain.AdminProjectsPort
import com.tasktracker.featureprojectsdomain.AttachmentBlobTransport
import com.tasktracker.featureprojectsdomain.AttachmentOpener
import com.tasktracker.featureprojectsdomain.FilePicker
import com.tasktracker.featureprojectsdomain.ProjectRepositoryPort
import com.tasktracker.featureteamsdomain.MemberAdminPort
import com.tasktracker.featureteamsdomain.TeamPort

/**
 * Dependency container providing domain ports and clock services to project presentation screens.
 * Optional ports default to null for offline or permission-restricted environments.
 */
class ProjectEnvironment(
    val repository: ProjectRepositoryPort,
    val dateProvider: DateProvider,
    /** Directory port for resolving member profiles and assignees. */
    val teamPort: TeamPort? = null,
    /** Admin member management port for creating and deactivating users. */
    val memberAdminPort: MemberAdminPort? = null,
    /** Platform file picker for attachments; null disables file selection. */
    val filePicker: FilePicker? = null,
    /** Admin project management port (online-only). */
    val adminProjectsPort: AdminProjectsPort? = null,
    /** Remote blob transport for attachment uploads/downloads; null disables remote sync. */
    val blobTransport: AttachmentBlobTransport? = null,
    /** Platform file opener for viewing local attachment files. */
    val opener: AttachmentOpener? = null,
)

val LocalProjectEnvironment = staticCompositionLocalOf<ProjectEnvironment?> { null }

val currentProjectEnvironment: ProjectEnvironment?
    @Composable get() = LocalProjectEnvironment.current

@Composable
fun ProvideProjectEnvironment(
    environment: ProjectEnvironment,
    content: @Composable () -> Unit,
) {
    CompositionLocalProvider(LocalProjectEnvironment provides environment, content = content)
}

/** Fallback screen rendered when the composition root provides no [ProjectEnvironment]. */
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
                text = "The app was built without a projects data source, so no project can be shown.",
                style = ttTypography.footnote,
                color = ttLabelSecondary(),
            )
        }
    }
}

const val MissingProjectEnvironmentTag = "projects.missingEnvironment"