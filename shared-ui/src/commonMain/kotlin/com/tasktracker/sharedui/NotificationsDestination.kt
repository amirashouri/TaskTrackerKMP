package com.tasktracker.sharedui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.tasktracker.core.DateProvider
import com.tasktracker.designsystem.ttLabelSecondary
import com.tasktracker.designsystem.ttSpacing
import com.tasktracker.designsystem.ttText
import com.tasktracker.designsystem.ttTypography
import com.tasktracker.featurenotificationsdomain.NotificationPort
import com.tasktracker.featurenotificationspresentation.NotificationsModel
import com.tasktracker.featurenotificationspresentation.NotificationsScreen
import com.tasktracker.featurenotificationspresentation.notificationTarget
import com.tasktracker.routing.AppRoute
import com.tasktracker.routing.AppTab
import com.tasktracker.routing.Router
import com.tasktracker.resources.Res
import com.tasktracker.resources.notifications_empty_body
import kotlin.time.Clock
import kotlinx.coroutines.CoroutineScope
import org.jetbrains.compose.resources.stringResource

/**
 * The inbox tab's root.
 *
 * The model is keyed on `(port, userID)` and disposed with the destination, the same
 * observe/stopObserving shape every other screen uses. Keying on the account is what makes a
 * sign-in switch the feed rather than leave the previous account's rows on screen.
 *
 * Opening a card is the shell's decision, not the screen's: the notification names a project
 * and a task, and `Router.push` is the only thing that may move the stack. A comment
 * notification opens the task with the comment focused.
 */
@Composable
internal fun NotificationsDestination(
    port: NotificationPort,
    router: Router,
    preferences: ShellPreferences,
    dateProvider: DateProvider,
    scope: CoroutineScope,
) {
    val userID by preferences.currentUserID.collectAsState()
    val id = userID ?: return

    val model = remember(port, id) { NotificationsModel(port, id, dateProvider) }
    DisposableEffect(model) {
        model.observe(scope)
        onDispose { model.stopObserving() }
    }

    val navigation by router.state.collectAsState()

    NotificationsScreen(
        model = model,
        highlightedID = navigation.highlightedNotificationID,
        // A display clock, not the store's: the age labels only need to be roughly right and
        // must not suspend a composition. Every write still takes the injected `dateProvider`.
        now = Clock.System.now(),
        onOpen = { notification ->
            notificationTarget(notification)?.let { target ->
                // Opening is reading: the row is marked read before the editor covers the inbox.
                model.markRead(notification.id)
                router.push(
                    route = AppRoute.WorkItemEditor(
                        projectID = target.projectID,
                        workItemID = target.workItemID,
                        focusCommentID = target.commentID,
                    ),
                    onTab = AppTab.Notifications,
                )
            }
        },
        onHighlightShown = { router.consumeNotificationHighlight() },
    )
}

/** What the tab says when no notification port is wired, rather than crashing. */
@Composable
internal fun NotificationsUnavailableView() {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        ttText(
            text = stringResource(Res.string.notifications_empty_body),
            style = ttTypography.body,
            color = ttLabelSecondary(),
            modifier = Modifier.padding(ttSpacing.xl),
        )
    }
}
