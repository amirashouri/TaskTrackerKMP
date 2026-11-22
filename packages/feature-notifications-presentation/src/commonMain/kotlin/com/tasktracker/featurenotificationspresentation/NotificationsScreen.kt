package com.tasktracker.featurenotificationspresentation

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.tasktracker.core.NotificationID
import com.tasktracker.designsystem.LocalReducedMotion
import com.tasktracker.designsystem.LocalTabBarScrollUnderInset
import com.tasktracker.designsystem.TTGlassButtonStyle
import com.tasktracker.designsystem.ttAccent
import com.tasktracker.designsystem.ttActionIcons
import com.tasktracker.designsystem.ttBanner
import com.tasktracker.designsystem.ttGlassButton
import com.tasktracker.designsystem.ttGlassIconButton
import com.tasktracker.designsystem.ttLabel
import com.tasktracker.designsystem.ttLabelSecondary
import com.tasktracker.designsystem.ttSearchField
import com.tasktracker.designsystem.ttSpacing
import com.tasktracker.designsystem.ttText
import com.tasktracker.designsystem.ttTypography
import com.tasktracker.featurenotificationsdomain.Notification
import com.tasktracker.resources.Res
import com.tasktracker.resources.notifications_cancel_selection
import com.tasktracker.resources.notifications_delete
import com.tasktracker.resources.notifications_empty_body
import com.tasktracker.resources.notifications_empty_title
import com.tasktracker.resources.notifications_mark_all_read
import com.tasktracker.resources.notifications_mark_read
import com.tasktracker.resources.notifications_no_matches
import com.tasktracker.resources.notifications_search_close
import com.tasktracker.resources.notifications_search_hint
import com.tasktracker.resources.notifications_search_open
import com.tasktracker.resources.notifications_select
import com.tasktracker.resources.notifications_select_all
import com.tasktracker.resources.notifications_selected
import com.tasktracker.resources.notifications_title
import com.tasktracker.resources.notifications_unread_count
import kotlin.time.Instant
import kotlinx.coroutines.delay
import org.jetbrains.compose.resources.stringResource

/**
 * The inbox page.
 *
 * Search and multi-select are the two top-right controls, as the requirement names them. Search
 * opens a field under the header rather than replacing it, so the query and the selection
 * controls stay reachable while typing. Newest first comes from the port; the screen never
 * re-sorts, because the list is the port's order and a second ordering would be a second truth.
 */
@Composable
fun NotificationsScreen(
    model: NotificationsModel,
    highlightedID: NotificationID?,
    now: Instant,
    onOpen: (Notification) -> Unit,
    onHighlightShown: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var isSearching by remember { mutableStateOf(model.query.isNotEmpty()) }
    var isFlashing by remember(highlightedID) { mutableStateOf(highlightedID != null) }

    val searchField: @Composable (Modifier) -> Unit = { fieldModifier ->
        ttSearchField(
            value = model.query,
            onValueChange = model::updateQuery,
            hint = stringResource(Res.string.notifications_search_hint),
            modifier = fieldModifier.testTag(NotificationSearchFieldTag),
        )
    }

    LaunchedEffect(highlightedID) {
        if (highlightedID != null) {
            isFlashing = true
            delay(NOTIFICATION_FLASH_MILLIS)
            isFlashing = false
            onHighlightShown()
        }
    }

    BoxWithConstraints(modifier = modifier.fillMaxSize()) {
        // Desktop/tablet: the field unfolds beside the controls in the header, the way the
        // Backlog and Board headers do. Compact keeps the field under the header, because
        // there is no room for a 300dp field and the two controls on one phone row.
        val wideHeader = maxWidth >= NotificationHeaderWideMinimumWidth

        Column(
            modifier = Modifier
                .fillMaxSize()
                .testTag(NotificationsScreenTag)
                .padding(horizontal = ttSpacing.xxl),
            verticalArrangement = Arrangement.spacedBy(ttSpacing.sm),
        ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = ttSpacing.md),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(ttSpacing.sm),
        ) {
            Column(modifier = Modifier.weight(1f)) {
                ttText(
                    text = stringResource(Res.string.notifications_title),
                    style = ttTypography.title,
                    color = ttLabel(),
                )
                if (model.unreadCount > 0) {
                    ttText(
                        text = stringResource(Res.string.notifications_unread_count, model.unreadCount),
                        style = ttTypography.footnote,
                        color = ttLabelSecondary(),
                    )
                }
            }
            if (!model.isSelecting) {
                if (wideHeader) {
                    NotificationSearchRevealBeside(visible = isSearching) {
                        searchField(Modifier.width(NotificationSearchFieldWidth))
                    }
                }
                ttGlassIconButton(
                    image = ttActionIcons.search,
                    contentDescription = stringResource(
                        if (isSearching) {
                            Res.string.notifications_search_close
                        } else {
                            Res.string.notifications_search_open
                        },
                    ),
                    onClick = {
                        isSearching = !isSearching
                        if (!isSearching) model.updateQuery("")
                    },
                    modifier = Modifier.testTag(NotificationSearchToggleTag),
                )
                ttGlassIconButton(
                    image = ttActionIcons.select,
                    contentDescription = stringResource(Res.string.notifications_select),
                    onClick = model::toggleSelectionMode,
                    modifier = Modifier.testTag(NotificationSelectToggleTag),
                )
            } else {
                ttGlassButton(
                    label = stringResource(Res.string.notifications_cancel_selection),
                    onClick = model::toggleSelectionMode,
                    modifier = Modifier.testTag(NotificationCancelSelectionTag),
                )
            }
        }

        if (!wideHeader) {
            NotificationSearchReveal(visible = isSearching && !model.isSelecting) {
                searchField(Modifier.fillMaxWidth())
            }
        }

        model.errorMessage?.let { message ->
            ttBanner(message = message, onDismiss = model::clearError)
        }

        if (model.isSelecting) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(ttSpacing.sm),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                ttText(
                    text = stringResource(Res.string.notifications_selected, model.selectedCount),
                    style = ttTypography.footnote,
                    color = ttLabelSecondary(),
                    modifier = Modifier.weight(1f),
                )
                ttGlassButton(
                    label = stringResource(Res.string.notifications_select_all),
                    onClick = model::toggleSelectAll,
                    modifier = Modifier.testTag(NotificationSelectAllTag),
                )
                ttGlassButton(
                    label = stringResource(Res.string.notifications_mark_read),
                    onClick = model::markSelectedRead,
                    enabled = model.selectedCount > 0,
                    modifier = Modifier.testTag(NotificationBulkReadTag),
                )
                ttGlassButton(
                    label = stringResource(Res.string.notifications_delete),
                    onClick = model::deleteSelected,
                    enabled = model.selectedCount > 0,
                    modifier = Modifier.testTag(NotificationBulkDeleteTag),
                    style = TTGlassButtonStyle(tint = ttAccent(), prominent = true),
                )
            }
        } else if (model.unreadCount > 0) {
            ttGlassButton(
                label = stringResource(Res.string.notifications_mark_all_read),
                onClick = model::markAllRead,
                modifier = Modifier.testTag(NotificationMarkAllReadTag),
            )
        }

        when {
            model.isLoading -> Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                ttText(
                    text = stringResource(Res.string.notifications_empty_body),
                    style = ttTypography.body,
                    color = ttLabelSecondary(),
                )
            }

            model.visibleRows.isEmpty() -> Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(top = ttSpacing.xxl)
                    .testTag(NotificationEmptyTag),
                verticalArrangement = Arrangement.spacedBy(ttSpacing.sm),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                ttText(
                    text = if (model.query.isBlank()) {
                        stringResource(Res.string.notifications_empty_title)
                    } else {
                        stringResource(Res.string.notifications_no_matches)
                    },
                    style = ttTypography.callout,
                    color = ttLabel(),
                    textAlign = TextAlign.Center,
                )
                if (model.query.isBlank()) {
                    ttText(
                        text = stringResource(Res.string.notifications_empty_body),
                        style = ttTypography.body,
                        color = ttLabelSecondary(),
                        textAlign = TextAlign.Center,
                    )
                }
            }

            else -> LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .testTag(NotificationListTag),
                contentPadding = PaddingValues(
                    top = ttSpacing.xs,
                    bottom = LocalTabBarScrollUnderInset.current + ttSpacing.xxl,
                ),
                verticalArrangement = Arrangement.spacedBy(ttSpacing.sm),
            ) {
                items(model.visibleRows, key = { it.id.stringValue }) { notification ->
                    NotificationCard(
                        notification = notification,
                        now = now,
                        selectionMode = model.isSelecting,
                        isSelected = notification.id in model.selected,
                        isHighlighted = isFlashing && notification.id == highlightedID,
                        onOpen = onOpen,
                        onToggleSelected = { model.toggleSelected(it.id) },
                        onMarkRead = { model.markRead(it.id) },
                        onDelete = { model.delete(it.id) },
                    )
                }
            }
        }
    }
    }
}

const val NotificationsScreenTag = "inbox.screen"
const val NotificationListTag = "inbox.list"
const val NotificationEmptyTag = "inbox.empty"
const val NotificationSearchToggleTag = "inbox.search.toggle"
const val NotificationSearchFieldTag = "inbox.search.field"
const val NotificationSelectToggleTag = "inbox.select.toggle"
const val NotificationCancelSelectionTag = "inbox.select.cancel"
const val NotificationSelectAllTag = "inbox.select.all"
const val NotificationBulkReadTag = "inbox.bulk.read"
const val NotificationBulkDeleteTag = "inbox.bulk.delete"
const val NotificationMarkAllReadTag = "inbox.markAllRead"

/** How long a tapped notification stays outlined after the popup opens the inbox. */
const val NOTIFICATION_FLASH_MILLIS: Long = 1_400L

/**
 * The inbox's search field unfolds into the column, matching the reveal the Board and
 * Backlog use.
 *
 * Duplicated rather than shared because `SearchReveal` lives in the projects presentation
 * module, and presentation modules do not depend on each other. The durations and the
 * reduced-motion rule are copied deliberately: the same control must not unfold at a
 * different speed on a different page, and reduced motion removes the travel rather than
 * the field.
 */
@Composable
private fun NotificationSearchReveal(
    visible: Boolean,
    content: @Composable () -> Unit,
) {
    val reducedMotion = LocalReducedMotion.current

    AnimatedVisibility(
        visible = visible,
        enter = if (reducedMotion) {
            EnterTransition.None
        } else {
            fadeIn(animationSpec = tween(SEARCH_FADE_IN_MILLIS)) +
                expandVertically(
                    animationSpec = tween(SEARCH_REVEAL_MILLIS),
                    expandFrom = Alignment.Top,
                )
        },
        exit = if (reducedMotion) {
            ExitTransition.None
        } else {
            fadeOut(animationSpec = tween(SEARCH_FADE_OUT_MILLIS)) +
                shrinkVertically(
                    animationSpec = tween(SEARCH_REVEAL_MILLIS),
                    shrinkTowards = Alignment.Top,
                )
        },
    ) {
        content()
    }
}

/**
 * The desktop variant: the field grows sideways out of the trailing controls rather than
 * pushing a new row into the list. Matches `SearchRevealBeside` on the Backlog and Board.
 */
@Composable
private fun NotificationSearchRevealBeside(
    visible: Boolean,
    content: @Composable () -> Unit,
) {
    val reducedMotion = LocalReducedMotion.current

    AnimatedVisibility(
        visible = visible,
        enter = if (reducedMotion) {
            EnterTransition.None
        } else {
            fadeIn(animationSpec = tween(SEARCH_FADE_IN_MILLIS)) +
                expandHorizontally(
                    animationSpec = tween(SEARCH_REVEAL_MILLIS),
                    expandFrom = Alignment.End,
                )
        },
        exit = if (reducedMotion) {
            ExitTransition.None
        } else {
            fadeOut(animationSpec = tween(SEARCH_FADE_OUT_MILLIS)) +
                shrinkHorizontally(
                    animationSpec = tween(SEARCH_REVEAL_MILLIS),
                    shrinkTowards = Alignment.End,
                )
        },
    ) {
        content()
    }
}

/** A search box, not a second title — the same width the Board gives its wide-header field. */
private val NotificationSearchFieldWidth = 300.dp

/** The same header breakpoint the Board and Backlog use for their one-row desktop header. */
private val NotificationHeaderWideMinimumWidth = 700.dp

/** Matches `SEARCH_REVEAL_MILLIS` in the projects presentation module. */
private const val SEARCH_REVEAL_MILLIS = 200

/** Settling opacity early hides the growing container's clipped edge. */
private const val SEARCH_FADE_IN_MILLIS = 120
private const val SEARCH_FADE_OUT_MILLIS = 90
