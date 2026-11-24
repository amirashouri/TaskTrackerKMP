package com.tasktracker.featureprojectspresentation

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import com.tasktracker.designsystem.TTBottomSheetScaffold
import com.tasktracker.designsystem.TTGlassButtonStyle
import com.tasktracker.designsystem.ttError
import com.tasktracker.designsystem.ttGlassButton
import com.tasktracker.designsystem.ttLabel
import com.tasktracker.designsystem.ttLabelSecondary
import com.tasktracker.designsystem.ttSpacing
import com.tasktracker.designsystem.ttText
import com.tasktracker.designsystem.ttTypography
import com.tasktracker.resources.Res
import com.tasktracker.resources.common_cancel
import com.tasktracker.resources.epic_archive_all
import com.tasktracker.resources.epic_archive_body
import com.tasktracker.resources.epic_archive_only
import com.tasktracker.resources.epic_archive_title
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource

/**
 * The two-choice archive dialog.
 *
 * Archive-only is the safe default and the only choice when nothing is unfinished; the
 * "and its tasks" option appears **only** when there is something to file, and names the
 * count so the user knows what a tap will move. A zero-count second button would be a choice
 * that does nothing, which reads as a bug.
 */
@Composable
fun EpicArchiveSheet(
    epicTitle: String,
    unfinishedCount: Int,
    visible: Boolean,
    onArchiveOnly: () -> Unit,
    onArchiveAll: () -> Unit,
    onDismissRequest: () -> Unit,
    onDismissed: () -> Unit,
    modifier: Modifier = Modifier,
) {
    TTBottomSheetScaffold(
        visible = visible,
        onDismissRequest = onDismissRequest,
        modifier = modifier,
        onDismissed = onDismissed,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .testTag(EpicArchiveSheetTag)
                .padding(ttSpacing.formStack),
            verticalArrangement = Arrangement.spacedBy(ttSpacing.lg),
        ) {
            ttText(
                text = stringResource(Res.string.epic_archive_title, epicTitle),
                style = ttTypography.titleSmall,
                color = ttLabel(),
            )
            ttText(
                text = stringResource(Res.string.epic_archive_body),
                style = ttTypography.callout,
                color = ttLabelSecondary(),
            )
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(ttSpacing.sm),
            ) {
                if (unfinishedCount > 0) {
                    ttGlassButton(
                        label = pluralStringResource(Res.plurals.epic_archive_all, unfinishedCount, unfinishedCount),
                        onClick = onArchiveAll,
                        style = TTGlassButtonStyle(prominent = true),
                        modifier = Modifier.fillMaxWidth().testTag(EpicArchiveAllTag),
                    )
                }
                ttGlassButton(
                    label = stringResource(Res.string.epic_archive_only),
                    onClick = onArchiveOnly,
                    style = TTGlassButtonStyle(
                        prominent = unfinishedCount == 0,
                        tint = if (unfinishedCount > 0) null else ttError(),
                    ),
                    modifier = Modifier.fillMaxWidth().testTag(EpicArchiveOnlyTag),
                )
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(ttSpacing.sm, Alignment.End),
            ) {
                ttGlassButton(
                    label = stringResource(Res.string.common_cancel),
                    onClick = onDismissRequest,
                    modifier = Modifier.testTag(EpicArchiveCancelTag),
                )
            }
        }
    }
}

const val EpicArchiveSheetTag = "projects.epic.archive"
const val EpicArchiveOnlyTag = "projects.epic.archive.only"
const val EpicArchiveAllTag = "projects.epic.archive.all"
const val EpicArchiveCancelTag = "projects.epic.archive.cancel"
