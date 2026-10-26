package com.tasktracker.featureprojectspresentation

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import com.tasktracker.designsystem.TTGlassFlowPicker
import com.tasktracker.designsystem.ttLabelSecondary
import com.tasktracker.designsystem.ttSpacing
import com.tasktracker.designsystem.ttText
import com.tasktracker.designsystem.ttTypography
import com.tasktracker.domain.Recurrence
import com.tasktracker.resources.Res
import com.tasktracker.resources.work_item_repeat_daily
import com.tasktracker.resources.work_item_repeat_label
import com.tasktracker.resources.work_item_repeat_monthly
import com.tasktracker.resources.work_item_repeat_none
import com.tasktracker.resources.work_item_repeat_weekly
import com.tasktracker.resources.work_item_repeat_yearly
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.isoDayNumber
import kotlinx.datetime.toLocalDateTime
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource
import kotlin.time.Clock
import kotlin.time.Instant

/**
 * Recurrence rule flow picker anchoring weekly/monthly frequency patterns to due dates or current date.
 */
@Composable
fun WorkItemRecurrenceRow(
    recurrence: Recurrence?,
    onRecurrenceChanged: (Recurrence?) -> Unit,
    modifier: Modifier = Modifier,
    /** Instant used to anchor day/week/month calculations; defaults to system current time. */
    anchorDate: Instant? = null,
) {
    val anchor = anchorDay(anchorDate)
    val choices = RecurrenceChoice.entries
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(ttSpacing.sm),
    ) {
        ttText(
            text = stringResource(Res.string.work_item_repeat_label),
            style = ttTypography.captionEmphasised,
            color = ttLabelSecondary(),
        )
        TTGlassFlowPicker(
            options = choices,
            selection = recurrence.choice(),
            onSelect = { choice ->
                onRecurrenceChanged(choice.toRecurrence(anchor, until = recurrence?.until))
            },
            title = { stringResource(it.labelRes) },
            modifier = Modifier.testTag(WorkItemRecurrencePickerTag),
        )
    }
}

private enum class RecurrenceChoice(val labelRes: StringResource) {
    None(Res.string.work_item_repeat_none),
    Daily(Res.string.work_item_repeat_daily),
    Weekly(Res.string.work_item_repeat_weekly),
    Monthly(Res.string.work_item_repeat_monthly),
    Yearly(Res.string.work_item_repeat_yearly),
}

private fun Recurrence?.choice(): RecurrenceChoice = when (this) {
    null -> RecurrenceChoice.None
    is Recurrence.Daily -> RecurrenceChoice.Daily
    is Recurrence.Weekly -> RecurrenceChoice.Weekly
    is Recurrence.Monthly -> RecurrenceChoice.Monthly
    is Recurrence.Yearly -> RecurrenceChoice.Yearly
}

private fun RecurrenceChoice.toRecurrence(anchor: LocalDate, until: Instant?): Recurrence? =
    when (this) {
        RecurrenceChoice.None -> null
        RecurrenceChoice.Daily -> Recurrence.Daily(until)
        RecurrenceChoice.Weekly -> Recurrence.Weekly(anchor.dayOfWeek.isoDayNumber, until)
        RecurrenceChoice.Monthly -> Recurrence.Monthly(anchor.dayOfMonth, until)
        RecurrenceChoice.Yearly -> Recurrence.Yearly(anchor.monthNumber, anchor.dayOfMonth, until)
    }

private fun anchorDay(anchorDate: Instant?): LocalDate {
    val instant = anchorDate ?: Clock.System.now()
    return instant.toLocalDateTime(TimeZone.currentSystemDefault()).date
}

const val WorkItemRecurrencePickerTag = "workItem.editor.recurrence"