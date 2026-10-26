package com.tasktracker.featureprojectspresentation

import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
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
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import com.tasktracker.core.UserID
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
 * Screen displaying read-only archived/terminal tasks grouped by completion month.
 * Tapping a row opens task details in read-only mode (status actions are disabled).
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
    /** Display names for assignee IDs, resolved from directory. */
    assigneeNames: Map<UserID, String> = emptyMap(),
) {
    Column(
        modifier = modifier
            .fillMaxSize()
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
                        ArchivedItemRow(
                            item = item,
                            onSelect = { onSelect(item.id) },
                            assigneeNames = assigneeNames,
                        )
                    }
                }
            }
        }
    }
}

/** Formatted month header; uses projection timeZone to match partitioned item groups. */
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

@Composable
private fun ArchivedItemRow(
    item: WorkItem,
    onSelect: () -> Unit,
    modifier: Modifier = Modifier,
    assigneeNames: Map<UserID, String> = emptyMap(),
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .ttGlassCard()
            .clickable(
                indication = null,
                interactionSource = remember { MutableInteractionSource() },
                onClick = onSelect,
            )
            .padding(ttSpacing.xl)
            .testTag(ArchivedItemTag(item)),
        verticalArrangement = Arrangement.spacedBy(ttSpacing.sm),
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(ttSpacing.md)) {
            ttText(text = item.key.toString(), style = ttTypography.key, color = ttLabelSecondary())
            item.assigneeID?.let { assigneeNames[it] }?.takeIf { it.isNotBlank() }?.let {
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

const val ProjectArchiveScreenTag = "archive.screen"
const val ProjectArchiveListTag = "archive.rows"

fun ArchiveMonthTag(monthStart: Instant): String = "archive.month.$monthStart"
fun ArchiveMonthCountTag(monthStart: Instant): String = "${ArchiveMonthTag(monthStart)}.count"
fun ArchivedItemTag(item: WorkItem): String = "archive.item.${item.key}"

private const val ARCHIVED_TITLE_MAX_LINES = 2