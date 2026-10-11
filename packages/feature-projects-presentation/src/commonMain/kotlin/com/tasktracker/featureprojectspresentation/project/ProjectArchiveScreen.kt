package com.tasktracker.featureprojectspresentation

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import com.tasktracker.core.WorkItemID
import com.tasktracker.designsystem.LocalTabBarScrollUnderInset
import com.tasktracker.designsystem.ThemeScheme
import com.tasktracker.designsystem.ttBanner
import com.tasktracker.designsystem.ttGlassCard
import com.tasktracker.designsystem.ttLabel
import com.tasktracker.designsystem.ttLabelSecondary
import com.tasktracker.designsystem.ttSpacing
import com.tasktracker.designsystem.ttText
import com.tasktracker.designsystem.ttTypography
import com.tasktracker.domain.WorkItem
import com.tasktracker.featureprojectsdomain.ArchiveProjection
import com.tasktracker.resources.Res
import com.tasktracker.resources.archive_empty_body
import com.tasktracker.resources.archive_empty_title
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import org.jetbrains.compose.resources.stringResource
import kotlin.time.Instant

/**
 * The archive: everything filed away, grouped by month, newest first.
 *
 * ## Why there are no actions here
 *
 * Every item in this list is terminal - `Done` or `Closed` - and the plan is explicit that
 * terminal-only actions are hidden. That is not tidiness. An action on a finished task
 * invites a second decision about work that is already over, and the two moves available
 * (`Done` → `Closed`, or back out to `Todo`) are both worse than doing nothing: one
 * rewrites a record of what was delivered, the other un-files a decision somebody made on
 * purpose. So the card is display-only, and [WorkItemCard] is deliberately *not* used here -
 * it has a tap target that opens the editor, and the editor is where a change would happen.
 *
 * The one thing a user can still do is read the item, so each row opens the editor. What it
 * will not offer is a status control; that is [WorkItemEditorModel]'s
 * [WorkItemEditorModel.selectableStatuses] being empty, enforced in the model rather than
 * hidden here.
 *
 * ## Why the month header is an instant
 *
 * [ArchiveProjection.Month.monthStart] is a `kotlin.time.Instant`, and the wording is
 * produced here. Grouping by a formatted string would make the group's *identity* change
 * when the device's language does, which turns every lazy-list key into something that has
 * to be re-keyed on a locale change. The header is a caption, and a caption is the view's
 * decision.
 */
@Composable
fun ProjectArchiveScreen(
    projection: ArchiveProjection,
    isLoading: Boolean,
    errorMessage: String?,
    onSelect: (WorkItemID) -> Unit,
    onDismissError: () -> Unit,
    modifier: Modifier = Modifier,
    onOpenSwitcher: (() -> Unit)? = null,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            // Top only: bottom padding here would shrink the *list's viewport* and stop it short of the
            // screen's bottom edge. The clearance is the list's `contentPadding`.
            .padding(horizontal = ttSpacing.xxl)
            .padding(top = ttSpacing.screenGutterLarge)
            .testTag(ProjectArchiveScreenTag),
        verticalArrangement = Arrangement.spacedBy(ttSpacing.lg),
    ) {
        onOpenSwitcher?.let { openSwitcher ->
            ProjectSwitcherButton(
                project = projection.project,
                onClick = openSwitcher,
                modifier = Modifier.fillMaxWidth(),
            )
        }
        errorMessage?.let { message ->
            ttBanner(message = message, onDismiss = onDismissError)
        }
        if (projection.isEmpty && !isLoading) {
            ProjectsEmptyState(
                title = stringResource(Res.string.archive_empty_title),
                body = stringResource(Res.string.archive_empty_body),
            )
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize().testTag(ProjectArchiveListTag),
                contentPadding = PaddingValues(bottom = ttSpacing.xl + LocalTabBarScrollUnderInset.current),
                verticalArrangement = Arrangement.spacedBy(ttSpacing.md),
            ) {
                projection.months.forEach { month ->
                    item(key = "month.${month.monthStart}") {
                        MonthHeader(
                            monthStart = month.monthStart,
                            timeZone = projection.timeZone,
                            count = month.count,
                        )
                    }
                    items(month.items, key = { it.id.stringValue }) { item ->
                        ArchivedItemRow(item = item, onSelect = { onSelect(item.id) })
                    }
                }
            }
        }
    }
}

/**
 * The month a section belongs to, and how much is in it.
 *
 * The zone is the projection's, not the device's: the projection grouped by
 * [ArchiveProjection.timeZone], and a header computed in a different zone can disagree with
 * the grouping it is labelling - an item filed at 23:30 on the 31st belongs to March under
 * UTC and to April in Tokyo, and the two answers have to come from the same place.
 */
@Composable
internal fun MonthHeader(
    monthStart: Instant,
    timeZone: TimeZone,
    count: Int,
    modifier: Modifier = Modifier,
) {
    val local = monthStart.toLocalDateTime(timeZone)

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(top = ttSpacing.lg, bottom = ttSpacing.xs)
            .testTag(ArchiveMonthTag(monthStart)),
        verticalArrangement = Arrangement.spacedBy(ttSpacing.xxs),
    ) {
        ttText(
            text = "${local.month.name} ${local.year}",
            style = ttTypography.titleSmall,
            color = ttLabel(),
        )
        ttText(
            text = count.toString(),
            style = ttTypography.captionSmall,
            color = ttLabelSecondary(),
            modifier = Modifier.testTag(ArchiveMonthCountTag(monthStart)),
        )
    }
}

/**
 * A filed-away task, as a row that can be opened but not acted on.
 *
 * A `ttGlassCard` like everything else, but with no action slot and no status. The
 * strike-through comes from the same place the board's does - the item is `Done` - so a
 * task looks the same filed as it does on the board, and a user scrolling from one to the
 * other is not re-learning a visual language.
 */
@Composable
private fun ArchivedItemRow(
    item: WorkItem,
    onSelect: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .ttGlassCard()
            .clickable(onClick = onSelect)
            .padding(ttSpacing.xl)
            .testTag(ArchivedItemTag(item)),
        verticalArrangement = Arrangement.spacedBy(ttSpacing.sm),
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(ttSpacing.md)) {
            ttText(text = item.key.toString(), style = ttTypography.key, color = ttLabelSecondary())
            item.assignee?.takeIf { it.isNotBlank() }?.let {
                ttText(text = it, style = ttTypography.footnote, color = ttLabelSecondary())
            }
        }
        ttText(
            text = item.title,
            style = ttTypography.callout.copy(
                textDecoration = if (item.status.isComplete) TextDecoration.LineThrough else null,
            ),
            color = ttLabel(),
            maxLines = ARCHIVED_TITLE_MAX_LINES,
            overflow = TextOverflow.Ellipsis,
        )
        ttText(text = statusLabel(item.status), style = ttTypography.captionSmall, color = ttLabelSecondary())
    }
}

@Preview
@Composable
private fun ProjectArchiveScreenPreview() = ProjectsPreview(ThemeScheme.Dark) {
    ProjectArchiveScreen(
        projection = ArchiveProjection(
            project = ViewPreviewData.seededProject,
            items = ViewPreviewData.workItems.filter { it.status.isTerminal },
            timeZone = TimeZone.UTC,
        ),
        isLoading = false,
        errorMessage = null,
        onSelect = {},
        onDismissError = {},
    )
}

@Preview
@Composable
private fun ProjectArchiveScreenEmptyPreview() = ProjectsPreview(ThemeScheme.Light) {
    ProjectArchiveScreen(
        projection = ArchiveProjection(
            project = ViewPreviewData.seededProject,
            items = emptyList(),
            timeZone = TimeZone.UTC,
        ),
        isLoading = false,
        errorMessage = null,
        onSelect = {},
        onDismissError = {},
    )
}

const val ProjectArchiveScreenTag = "archive.screen"
const val ProjectArchiveListTag = "archive.rows"

fun ArchiveMonthTag(monthStart: Instant): String = "archive.month.$monthStart"

fun ArchiveMonthCountTag(monthStart: Instant): String = "${ArchiveMonthTag(monthStart)}.count"

fun ArchivedItemTag(item: WorkItem): String = "archive.item.${item.key}"

private const val ARCHIVED_TITLE_MAX_LINES = 2
