package com.tasktracker.featureprojectspresentation

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import com.tasktracker.designsystem.LocalTabBarScrollUnderInset
import com.tasktracker.designsystem.ThemeScheme
import com.tasktracker.designsystem.TTGlassButtonStyle
import com.tasktracker.designsystem.TTSwipeToRevealRow
import com.tasktracker.designsystem.ttBanner
import com.tasktracker.designsystem.ttBannerTone
import com.tasktracker.designsystem.ttError
import com.tasktracker.designsystem.ttGlassButton
import com.tasktracker.designsystem.ttGlassIconButton
import com.tasktracker.designsystem.ttActionIcons
import com.tasktracker.designsystem.ttGlassSurface
import com.tasktracker.designsystem.ttLabel
import com.tasktracker.designsystem.ttLabelSecondary
import com.tasktracker.designsystem.ttRadius
import com.tasktracker.designsystem.ttSpacing
import com.tasktracker.designsystem.ttText
import com.tasktracker.designsystem.ttTypography
import com.tasktracker.core.ProjectID
import com.tasktracker.resources.Res
import com.tasktracker.resources.common_cancel
import com.tasktracker.resources.common_delete
import com.tasktracker.resources.project_archived_notice_counted
import com.tasktracker.resources.project_deleted_notice
import com.tasktracker.resources.project_already_archived
import com.tasktracker.resources.project_new
import com.tasktracker.resources.project_remove_archive
import com.tasktracker.resources.project_remove_body_clear
import com.tasktracker.resources.project_remove_body_outstanding
import com.tasktracker.resources.project_remove_title
import com.tasktracker.resources.tab_projects
import com.tasktracker.resources.projects_empty_body
import com.tasktracker.resources.projects_empty_title
import org.jetbrains.compose.resources.stringResource

/**
 * The list of projects, with a confirmation in front of removing one.
 *
 * ## Why the first `LazyColumn` in this app is here, and not on a board
 *
 * A board's columns scroll *sideways* and each column scrolls vertically, which in Compose
 * means nested scroll containers - the thing that makes gesture arbitration ambiguous and
 * which is why the board below is a `Row` of independently-sized columns rather than a
 * `LazyRow` of `LazyColumn`s. A list of projects has no such constraint: one axis, one
 * container, and it is the shape a phone is good at. So the nesting argument does not
 * apply here, and a `LazyColumn` is correct rather than a compromise.
 *
 * ## Why the confirmation names the archive rule
 *
 * Deleting a project with unfinished work does not delete it - it archives it, and the
 * tasks move to the archive. A confirmation reading "Delete?" above a button that archives
 * is the app quietly disagreeing with itself, and the user finds out after the fact.
 *
 * So the dialog's body is [ProjectRow.outstandingCount]'s two cases verbatim: either the
 * tasks survive in the archive, or - and only when there is nothing unfinished - they are
 * destroyed. The count is read from the row the user pressed, and the model re-checks it
 * against the repository before acting, because the count can change between the two. That
 * is why [ProjectRemovalOutcome.AlreadyArchived] exists at all.
 */
@Composable
fun ProjectsListScreen(
    rows: List<ProjectRow>,
    notice: RemovalNotice?,
    isLoading: Boolean,
    errorMessage: String?,
    onOpen: (ProjectID) -> Unit,
    onConfirmRemove: (ProjectID) -> Unit,
    onDismissRemove: () -> Unit,
    onDismissNotice: () -> Unit,
    onDismissError: () -> Unit,
    modifier: Modifier = Modifier,
    onNewProject: (() -> Unit)? = null,
    onEditProject: (ProjectID) -> Unit = {},
) {
    // The *id*, not the row. The row is a snapshot of a moment ago, and the count in it is
    // what the confirmation is worded from - so it has to be re-read when the dialog opens
    // rather than captured when the button was drawn.
    var pendingRemovalID by remember { mutableStateOf<ProjectID?>(null) }

    // How much of the bottom of this screen the floating tab bar covers, published by the shell
    // in `shared-ui`. It goes into the list's `contentPadding` below and into the add button's
    // offset, and in neither place is it interchangeable with a root padding: that would stop the
    // screen short of the glass, which is the layout this screen is leaving.
    val tabBarInset = LocalTabBarScrollUnderInset.current

    Box(
        modifier = modifier.fillMaxSize().testTag(ProjectsListScreenTag),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                // Top only: bottom padding here would shrink the *list's viewport* and stop it short of the
                // screen's bottom edge, which is the bound the scroll is supposed to reach. The
                // clearance that keeps the last row off the glass is the list's `contentPadding`.
                .padding(horizontal = ttSpacing.xxl)
                .padding(top = ttSpacing.screenGutterLarge),
            verticalArrangement = Arrangement.spacedBy(ttSpacing.lg),
        ) {
            ProjectsHeader(onNewProject = onNewProject)
            errorMessage?.let { message ->
                ttBanner(message = message, onDismiss = onDismissError)
            }
            notice?.let { outcome ->
                ttBanner(
                    tone = ttBannerTone.Accent,
                    message = when (outcome) {
                        is RemovalNotice.ArchivedInstead -> stringResource(
                            Res.string.project_archived_notice_counted,
                            outcome.projectName,
                            outcome.outstandingCount,
                        )
                        is RemovalNotice.Deleted -> stringResource(
                            Res.string.project_deleted_notice,
                            outcome.projectName,
                        )
                        is RemovalNotice.AlreadyArchived -> stringResource(
                            Res.string.project_already_archived,
                            outcome.projectName,
                        )
                    },
                    onDismiss = onDismissNotice,
                )
            }
            if (rows.isEmpty() && !isLoading) {
                ProjectsEmptyState(
                    title = stringResource(Res.string.projects_empty_title),
                    body = stringResource(Res.string.projects_empty_body),
                    // `weight`, not `fillMaxSize`: a child that fills the column is measured
                    // against the column's full height, which leaves the title above it at zero.
                    modifier = Modifier.weight(1f).fillMaxWidth(),
                )
            } else {
                LazyColumn(
                    // `weight(1f)` so the list takes the column's remaining height and its own
                    // `contentPadding` is measured against that — padding is laid out inside the
                    // viewport, so a viewport that stopped at the bar would reserve nothing useful.
                    modifier = Modifier.weight(1f).testTag(ProjectsListTag),
                    contentPadding = PaddingValues(
                        bottom = ttSpacing.xl + tabBarInset,
                    ),
                    verticalArrangement = Arrangement.spacedBy(ttSpacing.md),
                ) {
                    items(rows, key = { it.summary.id.stringValue }) { row ->
                        // The swipe and the button are the same gesture, offered twice. The
                        // swipe is the fast path a thumb finds without looking, and the button
                        // is the one that is reachable by keyboard and screen reader and can be
                        // aimed deliberately - so they share `onRemove`, and share the
                        // confirmation behind it, rather than being two actions that could
                        // disagree about what removing a project means.
                        TTSwipeToRevealRow(
                            revealLabel = stringResource(Res.string.common_delete),
                            isRevealed = pendingRemovalID == row.summary.id,
                            onRevealed = { pendingRemovalID = row.summary.id },
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            ProjectRowView(
                                project = row.summary,
                                onOpen = { onOpen(row.summary.id) },
                                onRemove = { pendingRemovalID = row.summary.id },
                                onEdit = { onEditProject(row.summary.id) },
                            )
                        }
                    }
                }
            }
        }
    }

    // Looked up here rather than stored, so the confirmation always words itself from the
    // list as it stands now. A project that vanished between the press and the dialog
    // simply has no row to confirm, and the dialog closes instead of acting on a ghost.
    val pendingRow = pendingRemovalID?.let { wanted -> rows.firstOrNull { it.summary.id == wanted } }
    val pendingProjectVanished = pendingRemovalID != null && pendingRow == null
    if (pendingProjectVanished) {
        // Cleared in an effect rather than here, because this is a caller's state change
        // made *during composition*: a dialog that re-renders and calls back into its host
        // mid-composition can loop, and the host's recomposition can re-enter this. An
        // effect runs after the composition settles, once, and the guard makes it once even
        // if the row is still missing on the following frame.
        LaunchedEffect(pendingRemovalID, pendingProjectVanished) {
            if (pendingProjectVanished) {
                pendingRemovalID = null
                onDismissRemove()
            }
        }
    }
    pendingRow?.let { row ->
        RemoveProjectConfirmation(
            row = row,
            onConfirm = {
                onConfirmRemove(row.summary.id)
                pendingRemovalID = null
            },
            onDismiss = {
                pendingRemovalID = null
                onDismissRemove()
            },
        )
    }
}

/**
 * The page title, with the create action in its trailing section.
 *
 * ## Why this is not a floating button
 *
 * It used to be one, in the bottom trailing corner, and that was a bug rather than a
 * preference. The list's `contentPadding` reserves the tab bar and nothing else, so the last row
 * was laid out *underneath* a 56dp circle that also won the touch. A row's own actions are
 * bottom trailing, so the one thing the circle covered was the delete button on the last card -
 * on exactly the cards long enough to reach the bottom of the viewport. The backlog's button had
 * the same shape and the same consequence.
 *
 * A floating control has to be paid for twice: once in the layout that reserves space for it and
 * once in the pointer target that has to be aimed past it. Every screen that carries one pays
 * both costs eventually, and the reservation is the one that gets forgotten. An action in the
 * header has neither cost, because there is nothing underneath it to cover.
 *
 * ## Why the action scrolls away with the title
 *
 * That is the trade this makes, and it is deliberate: on a long list you scroll up to add. It
 * is the iOS large-title behaviour rather than the pinned navigation bar, and it is the right
 * one here because a projects list is short and the alternative is a permanent overlay over the
 * list. A pinned bar would need a `LocalTopBarScrollUnderInset` to mirror
 * `LocalTabBarScrollUnderInset` and every scrolling screen's `contentPadding` updated with it.
 */
@Composable
private fun ProjectsHeader(
    onNewProject: (() -> Unit)?,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(ttSpacing.md),
    ) {
        ttText(
            text = stringResource(Res.string.tab_projects),
            style = ttTypography.largeTitle,
            color = ttLabel(),
            modifier = Modifier.weight(1f),
        )
        // Absent rather than disabled when the host has no create action: a greyed-out circle
        // with no explanation is worse than no circle, and the host already knows whether it can
        // create a project.
        if (onNewProject != null) {
            ttGlassIconButton(
                image = ttActionIcons.add,
                contentDescription = stringResource(Res.string.project_new),
                onClick = onNewProject,
                modifier = Modifier.testTag(ProjectsListNewTag),
            )
        }
    }
}

/**
 * The dialog, drawn by the screen rather than by a `Dialog`.
 *
 * Compose Multiplatform's `Dialog` is platform-backed and does not render under
 * `runComposeUiTest` on every target, so a confirmation built from it would be untestable
 * on at least one of the three. Drawing it inline means it is part of the same tree, it
 * appears in the same semantics pass, and a UI test finds it with a tag like anything else.
 */
@Composable
private fun RemoveProjectConfirmation(
    row: ProjectRow,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val shape = RoundedCornerShape(ttRadius.surface)
    val hasOutstanding = row.outstandingCount > 0

    Column(
        modifier = modifier
            .fillMaxWidth()
            .ttGlassSurface(shape = shape)
            .padding(ttSpacing.formStack)
            .testTag(RemoveProjectConfirmationTag),
        verticalArrangement = Arrangement.spacedBy(ttSpacing.lg),
    ) {
        ttText(
            text = stringResource(Res.string.project_remove_title, row.summary.name),
            style = ttTypography.titleSmall,
            color = ttLabel(),
        )
        ttText(
            text = if (hasOutstanding) {
                stringResource(Res.string.project_remove_body_outstanding, row.outstandingCount)
            } else {
                stringResource(Res.string.project_remove_body_clear)
            },
            style = ttTypography.callout,
            color = ttLabelSecondary(),
            textAlign = TextAlign.Start,
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(ttSpacing.sm, Alignment.End),
        ) {
            ttGlassButton(
                label = stringResource(Res.string.common_cancel),
                onClick = onDismiss,
                modifier = Modifier.testTag(RemoveProjectCancelTag),
            )
            ttGlassButton(
                // The button says *archive* when that is what will happen. A destructive
                // label on a button that archives is the disagreement this dialog exists
                // to prevent, and it is cheapest to prevent at the button.
                label = stringResource(
                    if (hasOutstanding) Res.string.project_remove_archive else Res.string.common_delete,
                ),
                onClick = onConfirm,
                modifier = Modifier.testTag(RemoveProjectConfirmTag),
                style = TTGlassButtonStyle(tint = ttError(), prominent = true),
            )
        }
    }
}

@Preview
@Composable
private fun ProjectsListScreenPreview() = ProjectsPreview(ThemeScheme.Dark) {
    ProjectsListScreen(
        rows = ViewPreviewData.summaries().map { ProjectRow(summary = it, outstandingCount = it.itemCount) },
        notice = null,
        isLoading = false,
        errorMessage = null,
        onOpen = {},
        onConfirmRemove = {},
        onDismissRemove = {},
        onDismissNotice = {},
        onDismissError = {},
    )
}

@Preview
@Composable
private fun ProjectsListScreenEmptyPreview() = ProjectsPreview(ThemeScheme.Light) {
    ProjectsListScreen(
        rows = emptyList(),
        notice = null,
        isLoading = false,
        errorMessage = null,
        onOpen = {},
        onConfirmRemove = {},
        onDismissRemove = {},
        onDismissNotice = {},
        onDismissError = {},
    )
}

@Preview
@Composable
private fun ProjectsListScreenArchivedNoticePreview() = ProjectsPreview(ThemeScheme.Dark) {
    ProjectsListScreen(
        rows = ViewPreviewData.summaries().map { ProjectRow(summary = it, outstandingCount = it.itemCount) },
        notice = RemovalNotice.ArchivedInstead(projectName = "Gemini", outstandingCount = 3),
        isLoading = false,
        errorMessage = null,
        onOpen = {},
        onConfirmRemove = {},
        onDismissRemove = {},
        onDismissNotice = {},
        onDismissError = {},
    )
}

const val ProjectsListScreenTag = "projects.list"
const val ProjectsListTag = "projects.list.rows"
const val ProjectsListNewTag = "projects.list.new"
const val RemoveProjectConfirmationTag = "projects.remove.confirmation"
const val RemoveProjectConfirmTag = "projects.remove.confirm"
const val RemoveProjectCancelTag = "projects.remove.cancel"
