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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.tasktracker.designsystem.AmbientBackdrop
import com.tasktracker.designsystem.ThemeScheme
import com.tasktracker.designsystem.ttAccent
import com.tasktracker.designsystem.ttBodyLabel
import com.tasktracker.designsystem.ttCanvas
import com.tasktracker.designsystem.ttIcon
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
import com.tasktracker.domain.StatusDefinition
import com.tasktracker.domain.TagColor
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
    /** Test tag for the editable node itself; the default derives one from [title]. */
    fieldTag: String? = null,
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
                modifier = Modifier.fillMaxWidth().testTag(fieldTag ?: title.tagSuffix()),
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

/**
 * The placeholder a list shows instead of a blank page.
 *
 * Centered, with the list's own glyph in a tinted badge where the caller has one. The copy is a
 * statement about the whole list, so left-aligned body text under a heading reads like a row
 * that failed to load; the badge carries the destination's own symbol, so an empty backlog
 * looks like the backlog rather than like a generic "nothing here".
 *
 * [image] is nullable because some callers (the project picker, the archive screen) have no
 * glyph of their own; they keep the same layout without a badge rather than growing a second
 * empty-state component.
 *
 * [action] is the one thing the screen exists to offer when it has nothing to show — "New
 * Project" on an empty list, "Add task" on an empty backlog. A slot rather than a label and an
 * `onClick`, so the caller keeps the wording of its own screen and the component stays about
 * layout; nullable, because a filtered list ("no matching tasks") has no single action to
 * offer.
 */
@Composable
internal fun ProjectsEmptyState(
    title: String,
    body: String,
    modifier: Modifier = Modifier,
    image: ImageVector? = null,
    action: (@Composable () -> Unit)? = null,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = ttSpacing.xxl, vertical = ttSpacing.xxxl),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(ttSpacing.sm, Alignment.CenterVertically),
    ) {
        if (image != null) {
            Box(
                modifier = Modifier
                    .padding(bottom = ttSpacing.md)
                    .size(EMPTY_BADGE_SIZE)
                    .clip(CircleShape)
                    .background(ttAccent().copy(alpha = EMPTY_BADGE_ALPHA)),
                contentAlignment = Alignment.Center,
            ) {
                ttIcon(
                    image = image,
                    // The title beside it says what the screen is; a description here would
                    // announce the same thing twice.
                    contentDescription = null,
                    tint = ttAccent(),
                    modifier = Modifier.size(EMPTY_GLYPH_SIZE),
                )
            }
        }
        ttText(
            text = title,
            style = ttTypography.titleSmall,
            color = ttLabel(),
            textAlign = TextAlign.Center,
        )
        // ttBodyLabel(), not ttLabelSecondary(): BodyLabelContrastTest measures this copy
        // against the canvas, and it is the only thing an empty screen has to say.
        ttText(
            text = body,
            style = ttTypography.subheadline,
            color = ttBodyLabel(),
            textAlign = TextAlign.Center,
        )
        action?.let { button ->
            Box(modifier = Modifier.padding(top = ttSpacing.md)) { button() }
        }
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
internal fun statusLabel(definition: StatusDefinition): String =
    definition.name.takeIf { it.isNotEmpty() } ?: statusLabel(definition.category)

@Composable
internal fun statusLabel(status: WorkItemStatus): String = when (status) {
    WorkItemStatus.Backlog -> stringResource(Res.string.status_backlog)
    WorkItemStatus.Todo -> stringResource(Res.string.status_todo)
    WorkItemStatus.InProgress -> stringResource(Res.string.status_in_progress)
    WorkItemStatus.InReview -> stringResource(Res.string.status_in_review)
    WorkItemStatus.Done -> stringResource(Res.string.status_done)
    WorkItemStatus.Closed -> stringResource(Res.string.status_closed)
}

/**
 * The six built-in stages as catalog entries: what an unconfigured project already draws,
 * turned into rows an admin can edit.
 *
 * Seeded into the *draft*, not written by the screen, because the catalog is what gets saved:
 * opening a project with no catalog and adding one stage must send seven entries, not one. The
 * names are the same labels the catalog-free fallback draws, so the board looks identical before
 * and after the first save, and the ids are stable strings rather than fresh UUIDs so two admins
 * seeding the same project offline do not mint six duplicate lanes each.
 */
@Composable
fun defaultStatusCatalog(): List<StatusDefinition> =
    WorkItemStatus.entries.mapIndexed { index, status ->
        StatusDefinition(
            id = "builtin-${status.wireValue}",
            name = statusLabel(status),
            category = status,
            order = index,
            color = status.defaultStatusColor(),
        )
    }

/** A colour per built-in lane, so the seeded rows are told apart before anyone edits them. */
private fun WorkItemStatus.defaultStatusColor(): TagColor = when (this) {
    WorkItemStatus.Backlog -> TagColor.Gray
    WorkItemStatus.Todo -> TagColor.Blue
    WorkItemStatus.InProgress -> TagColor.Amber
    WorkItemStatus.InReview -> TagColor.Teal
    WorkItemStatus.Done -> TagColor.Green
    WorkItemStatus.Closed -> TagColor.Gray
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

private val EMPTY_BADGE_SIZE = 64.dp
private val EMPTY_GLYPH_SIZE = 28.dp
private const val EMPTY_BADGE_ALPHA = 0.12f
private const val FOCUS_ANIMATION_MILLIS = 150
private val FOCUSED_RING_WIDTH = 2.dp
private val RESTING_RING_WIDTH = 1.dp