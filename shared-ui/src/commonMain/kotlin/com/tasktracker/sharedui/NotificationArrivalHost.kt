package com.tasktracker.sharedui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.tasktracker.designsystem.ttSpacing
import com.tasktracker.featurenotificationsdomain.Notification
import com.tasktracker.featurenotificationsdomain.NotificationPort
import com.tasktracker.featurenotificationspresentation.NotificationArrivalBanner
import com.tasktracker.routing.AppTab
import com.tasktracker.routing.Router
import kotlin.time.Clock
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.delay

/**
 * Hosts the in-app arrival popup over the whole signed-in shell.
 *
 * ## Why here and not in the inbox
 *
 * An arrival is only worth interrupting for while the user is somewhere else. If the popup were
 * drawn by the inbox it could only appear on the inbox, which is the one screen where it adds
 * nothing. The shell is the first place that knows both the arrival and the current tab.
 *
 * ## The three rules, and where each lives
 *
 * - **Three seconds, then gone.** The timer is here rather than in the banner because this
 *   composable owns the queue; a banner-local timer would start again whenever the next
 *   notification re-keyed it.
 * - **One at a time.** Arrivals queue here, so three comments in a minute do not stack three
 *   glass cards over the page. Each dismissal — timer, swipe or close — shows the next.
 * - **The inbox is silent.** A user already reading the inbox does not need a popup about the
 *   row they are looking at, so arrivals that land while that tab is selected are dropped.
 *
 * Only rows the server *generated* arrive here. The repository emits on insert from the pull
 * path and never on a local read/dismiss, which is what keeps the popup from announcing the
 * user's own tap.
 */
@Composable
internal fun NotificationArrivalHost(
    router: Router,
    port: NotificationPort?,
    preferences: ShellPreferences,
    modifier: Modifier = Modifier,
) {
    val userID by preferences.currentUserID.collectAsState()
    val navigation by router.state.collectAsState()
    val currentTab by rememberUpdatedState(navigation.selectedTab)

    var queue by remember { mutableStateOf<List<Notification>>(emptyList()) }
    var current by remember { mutableStateOf<Notification?>(null) }

    fun advance() {
        current = queue.firstOrNull()
        queue = queue.drop(1)
    }

    LaunchedEffect(port, userID) {
        val id = userID ?: return@LaunchedEffect
        val notifications = port ?: return@LaunchedEffect
        notifications.arrivals(id).collect { notification ->
            val isFresh = notification.createdAt >= Clock.System.now() - ARRIVAL_MAX_AGE
            if (!isFresh || currentTab == AppTab.Notifications) return@collect
            if (current == null) current = notification else queue = queue + notification
        }
    }

    LaunchedEffect(current?.id) {
        val shown = current ?: return@LaunchedEffect
        delay(ARRIVAL_VISIBLE_MILLIS)
        if (current?.id == shown.id) advance()
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .padding(
                horizontal = ttSpacing.tabBarEdgeInset,
                vertical = ttSpacing.sm,
            ),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Top,
    ) {
        AnimatedVisibility(
            visible = current != null,
            enter = fadeIn() + slideInVertically { height -> -height },
            exit = fadeOut() + slideOutVertically { height -> -height },
        ) {
            current?.let { notification ->
                NotificationArrivalBanner(
                    notification = notification,
                    onOpen = {
                        current = null
                        router.openNotifications(notification.id)
                    },
                    onDismiss = { advance() },
                )
            }
        }
    }
}

/** The popup's dwell time. Long enough to read two lines, short enough to stay a glance. */
private const val ARRIVAL_VISIBLE_MILLIS: Long = 3_000L

/** A row delivered late by a background pull is history, not an arrival. */
private val ARRIVAL_MAX_AGE = 60.seconds
