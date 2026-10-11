package com.tasktracker.featureprojectspresentation

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.tooling.preview.Preview
import com.tasktracker.designsystem.TTGlassFlowPicker
import com.tasktracker.designsystem.ThemeScheme
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
 * The repeat rule, as a wrapping row of glass choices.
 *
 * ## The day a rule repeats on comes from the due date, not from a second picker
 *
 * "Weekly" needs a weekday and "Monthly" a day of the month, and offering those as
 * separate pickers turns a two-tap setting into a small calendar problem — half the
 * monthly options do not exist in February. So a choice takes its day from the item's
 * due date as it stands at that moment (today when there is none): a task due Friday
 * repeats on Fridays, which is what the user meant when they set the due date and the
 * repeat together. The day is fixed when the choice is made, not re-derived on every
 * recomposition, so an existing rule keeps its stored weekday even if the due date
 * moves afterwards — re-picking the cadence is how a moved due date re-anchors it.
 *
 * `until` is not offered here. It is carried through unchanged when the cadence is
 * switched, so a series that was set to end keeps its end; the row only decides the
 * cadence and whether there is a rule at all.
 */
@Composable
fun WorkItemRecurrenceRow(
    recurrence: Recurrence?,
    onRecurrenceChanged: (Recurrence?) -> Unit,
    modifier: Modifier = Modifier,
    /** The instant the rule takes its day from; today's date in the system zone when `null`. */
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

/**
 * The five things the picker can say, spelled as an enum so the lens has a stable
 * position per option and the model never has to compare constructed rules for equality.
 */
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

@Preview
@Composable
private fun WorkItemRecurrenceRowPreview() = ProjectsPreview(ThemeScheme.Dark) {
    WorkItemRecurrenceRow(
        recurrence = Recurrence.Weekly(5),
        onRecurrenceChanged = {},
        anchorDate = Instant.parse("2026-01-16T12:00:00Z"),
    )
}

@Preview
@Composable
private fun WorkItemRecurrenceRowNonePreview() = ProjectsPreview(ThemeScheme.Light) {
    WorkItemRecurrenceRow(
        recurrence = null,
        onRecurrenceChanged = {},
        anchorDate = Instant.parse("2026-01-16T12:00:00Z"),
    )
}

/** The repeat row's picker, for scenario tests. */
const val WorkItemRecurrencePickerTag = "workItem.editor.recurrence"
