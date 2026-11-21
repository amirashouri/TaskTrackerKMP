package com.tasktracker.featurenotificationspresentation

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.tasktracker.core.DateProvider
import com.tasktracker.core.NotificationID
import com.tasktracker.core.UserID
import com.tasktracker.featurenotificationsdomain.Notification
import com.tasktracker.featurenotificationsdomain.NotificationPort
import com.tasktracker.featurenotificationsdomain.wireValue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/**
 * The inbox's state: the feed, the search, and multi-select.
 *
 * A model rather than state held inside the screen, so the shell can route a popup tap into the
 * same instance the screen is drawing. It owns no navigation: a card tap is reported upwards and
 * the shell decides where it goes, which keeps the "open the task" rule in one place.
 */
class NotificationsModel(
    private val port: NotificationPort,
    private val userID: UserID,
    private val dateProvider: DateProvider,
) {
    var rows by mutableStateOf<List<Notification>>(emptyList())
        private set
    var unreadCount by mutableStateOf(0)
        private set
    var query by mutableStateOf("")
        private set
    var isSelecting by mutableStateOf(false)
        private set
    var selected by mutableStateOf<Set<NotificationID>>(emptySet())
        private set
    var isLoading by mutableStateOf(true)
        private set
    var errorMessage by mutableStateOf<String?>(null)
        private set

    private var observing: Job? = null
    private var scope: CoroutineScope? = null

    /** Rows after the search filter, newest first (the port already orders them). */
    val visibleRows: List<Notification>
        get() {
            val needle = query.trim()
            if (needle.isEmpty()) return rows
            return rows.filter { row ->
                val description = notificationDescription(row)
                description.contains(needle, ignoreCase = true) ||
                    row.kind.wireValue.contains(needle, ignoreCase = true) ||
                    row.taskTitle()?.contains(needle, ignoreCase = true) == true
            }
        }

    val selectedCount: Int get() = selected.size
    val allVisibleSelected: Boolean
        get() = visibleRows.isNotEmpty() && visibleRows.all { it.id in selected }

    fun observe(scope: CoroutineScope) {
        stopObserving()
        this.scope = scope
        observing = scope.launch {
            launch {
                port.notificationsStream(userID).collect {
                    rows = it
                    isLoading = false
                    // A selected row that was dismissed elsewhere cannot stay selected.
                    selected = selected.intersect(it.map { row -> row.id }.toSet())
                }
            }
            launch { port.unreadCountStream(userID).collect { unreadCount = it } }
        }
    }

    fun stopObserving() {
        observing?.cancel()
        observing = null
        scope = null
    }

    fun updateQuery(value: String) {
        query = value
    }

    fun toggleSelectionMode() {
        isSelecting = !isSelecting
        selected = emptySet()
    }

    fun toggleSelected(id: NotificationID) {
        selected = if (id in selected) selected - id else selected + id
    }

    fun toggleSelectAll() {
        selected = if (allVisibleSelected) {
            selected - visibleRows.map { it.id }.toSet()
        } else {
            selected + visibleRows.map { it.id }.toSet()
        }
    }

    fun markRead(id: NotificationID) {
        mutate { port.setRead(id, true, dateProvider.now()) }
    }

    fun delete(id: NotificationID) {
        mutate { port.deleteNotification(id, dateProvider.now()) }
    }

    fun markSelectedRead() {
        val ids = selected.toList()
        if (ids.isEmpty()) return
        mutate { ids.forEach { port.setRead(it, true, dateProvider.now()) } }
        isSelecting = false
        selected = emptySet()
    }

    fun deleteSelected() {
        val ids = selected.toList()
        if (ids.isEmpty()) return
        mutate { ids.forEach { port.deleteNotification(it, dateProvider.now()) } }
        isSelecting = false
        selected = emptySet()
    }

    fun markAllRead() {
        mutate { port.markAllRead(userID, dateProvider.now()) }
    }

    fun clearError() {
        errorMessage = null
    }

    private fun mutate(action: suspend () -> Unit) {
        val target = scope ?: return
        target.launch {
            runCatching { action() }.onFailure { error ->
                errorMessage = error.message ?: "The notification could not be updated."
            }
        }
    }
}

private fun Notification.taskTitle(): String? =
    com.tasktracker.featurenotificationsdomain.decodeNotificationPayload(payload)?.taskTitle
