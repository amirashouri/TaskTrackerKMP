package com.tasktracker.featureprojectspresentation

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.tasktracker.designsystem.AmbientBackdrop
import com.tasktracker.designsystem.ThemeScheme
import com.tasktracker.designsystem.ttAccent
import com.tasktracker.designsystem.ttBodyLabel
import com.tasktracker.designsystem.ttCanvas
import com.tasktracker.designsystem.ttLabel
import com.tasktracker.designsystem.ttLabelSecondary
import com.tasktracker.designsystem.ttRadius
import com.tasktracker.designsystem.ttSeparator
import com.tasktracker.designsystem.ttSpacing
import com.tasktracker.designsystem.ttSurface
import com.tasktracker.designsystem.ttText
import com.tasktracker.designsystem.ttTheme
import com.tasktracker.designsystem.ttTypography
import com.tasktracker.domain.Priority
import com.tasktracker.domain.WorkItemStatus
import com.tasktracker.resources.Res
import com.tasktracker.resources.priority_critical
import com.tasktracker.resources.priority_high
import com.tasktracker.resources.priority_low
import com.tasktracker.resources.priority_lowest
import com.tasktracker.resources.priority_medium
import com.tasktracker.resources.status_backlog
import com.tasktracker.resources.status_closed
import com.tasktracker.resources.status_done
import com.tasktracker.resources.status_in_progress
import com.tasktracker.resources.status_in_review
import com.tasktracker.resources.status_todo
import org.jetbrains.compose.resources.stringResource

@Composable
internal fun ProjectsLabelledField(
    title: String,
    isFocused: Boolean,
    modifier: Modifier = Modifier,
    trailing: (@Composable () -> Unit)? = null,
    content: @Composable () -> Unit,
) {
    val ringColor by animateColorAsState(
        targetValue = if (isFocused) ttAccent() else ttSeparator(),
        animationSpec = tween(FOCUS_ANIMATION_MILLIS),
        label = "ProjectsLabelledField ring",
    )
    val shape = RoundedCornerShape(ttRadius.control)

    Column(
        modifier = modifier
            .clip(shape)
            .background(ttSurface())
            .border(
                width = if (isFocused) FOCUSED_RING_WIDTH else RESTING_RING_WIDTH,
                color = ringColor,
                shape = shape,
            )
            .padding(horizontal = ttSpacing.xxl, vertical = ttSpacing.xl),
        verticalArrangement = Arrangement.spacedBy(ttSpacing.sm),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            ttText(
                text = title,
                style = ttTypography.captionEmphasised,
                color = ttLabelSecondary(),
            )
            trailing?.invoke()
        }
        content()
    }
}

/** Labelled text field supporting both editable input and read-only text presentation. */
@Composable
internal fun ProjectsTextField(
    title: String,
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    placeholder: String? = null,
    isFocused: Boolean = false,
    isReadOnly: Boolean = false,
    enabled: Boolean = true,
    keyboardType: KeyboardType = KeyboardType.Text,
    capitalization: KeyboardCapitalization = KeyboardCapitalization.Sentences,
    imeAction: ImeAction = ImeAction.Next,
    onImeAction: () -> Unit = {},
    singleLine: Boolean = true,
    minLines: Int = 1,
) {
    ProjectsLabelledField(title = title, isFocused = isFocused && !isReadOnly, modifier = modifier) {
        if (isReadOnly) {
            ttText(
                text = value,
                style = ttTypography.body,
                color = ttLabelSecondary(),
            )
        } else {
            BasicTextField(
                value = value,
                onValueChange = onValueChange,
                modifier = Modifier.fillMaxWidth().testTag(title.tagSuffix()),
                enabled = enabled,
                textStyle = ttTypography.body.copy(color = if (enabled) ttLabel() else ttLabelSecondary()),
                cursorBrush = SolidColor(ttAccent()),
                singleLine = singleLine,
                minLines = minLines,
                keyboardOptions = KeyboardOptions(
                    keyboardType = keyboardType,
                    imeAction = imeAction,
                    capitalization = capitalization,
                ),
                keyboardActions = KeyboardActions(
                    onNext = { onImeAction() },
                    onDone = { onImeAction() },
                    onGo = { onImeAction() },
                ),
                decorationBox = { inner ->
                    Column {
                        if (value.isEmpty() && placeholder != null) {
                            ttText(text = placeholder, style = ttTypography.body, color = ttLabelSecondary())
                        }
                        inner()
                    }
                },
            )
        }
    }
}

/** Empty state container rendering a title and instructional body. */
@Composable
internal fun ProjectsEmptyState(
    title: String,
    body: String,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.padding(ttSpacing.xxl),
        verticalArrangement = Arrangement.spacedBy(ttSpacing.sm),
    ) {
        ttText(text = title, style = ttTypography.titleSmall, color = ttLabel())
        // Uses ttBodyLabel() to satisfy WCAG contrast on light canvas
        ttText(
            text = body,
            style = ttTypography.subheadline,
            color = ttBodyLabel(),
        )
    }
}

/** Preview scaffold rendering canvas background and ambient backdrop. */
@Composable
internal fun ProjectsPreview(
    scheme: ThemeScheme,
    content: @Composable () -> Unit,
) {
    ttTheme(scheme) {
        Box(modifier = Modifier.background(ttCanvas())) {
            AmbientBackdrop()
            Column(modifier = Modifier.padding(ttSpacing.screenGutterLarge)) { content() }
        }
    }
}

@Composable
internal fun statusLabel(status: WorkItemStatus): String = when (status) {
    WorkItemStatus.Backlog -> stringResource(Res.string.status_backlog)
    WorkItemStatus.Todo -> stringResource(Res.string.status_todo)
    WorkItemStatus.InProgress -> stringResource(Res.string.status_in_progress)
    WorkItemStatus.InReview -> stringResource(Res.string.status_in_review)
    WorkItemStatus.Done -> stringResource(Res.string.status_done)
    WorkItemStatus.Closed -> stringResource(Res.string.status_closed)
}

@Composable
internal fun priorityLabel(priority: Priority): String = when (priority) {
    Priority.Lowest -> stringResource(Res.string.priority_lowest)
    Priority.Low -> stringResource(Res.string.priority_low)
    Priority.Medium -> stringResource(Res.string.priority_medium)
    Priority.High -> stringResource(Res.string.priority_high)
    Priority.Critical -> stringResource(Res.string.priority_critical)
}

private fun String.tagSuffix(): String =
    lowercase().replace(' ', '.').filter { it.isLetterOrDigit() || it == '.' }

private const val FOCUS_ANIMATION_MILLIS = 150
private val FOCUSED_RING_WIDTH = 2.dp
private val RESTING_RING_WIDTH = 1.dp