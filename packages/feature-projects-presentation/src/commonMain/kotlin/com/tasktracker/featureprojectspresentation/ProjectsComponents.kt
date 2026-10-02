package com.tasktracker.featureprojectspresentation

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
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
import com.tasktracker.designsystem.ttCanvas
import com.tasktracker.designsystem.ttErrorFill
import com.tasktracker.designsystem.ttErrorRim
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

/**
 * The pieces the project screens share, gathered here rather than in a `components`
 * package.
 *
 * The obvious alternative is to reach for `LabelledField` and `ErrorBanner` from
 * `:packages:feature-auth-presentation`. They are `internal`-shaped, private to that
 * module, and a shared UI module cannot be a dependency of a feature: it would be a
 * cycle. So this file re-declares the two shapes, which is duplication of *code* and not
 * of *design* - both copies read `ttSpacing`, `ttRadius` and `ttTypography`, so a change to
 * the spacing ramp moves both. The alternative - promoting them to `:packages:design-system`
 * - is the right answer and belongs in its own phase, because it changes a finished module's
 * public surface.
 */

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

/**
 * A single-line text field with a label, or a read-only rendering of one.
 *
 * A read-only field is still a field, not a disabled `BasicTextField`: a disabled field
 * greys its text and takes it out of the semantics tree's editable set, and the project
 * key prefix is a fact the user needs to *read* - it is the prefix every task key will
 * carry - rather than a value they are being prevented from changing.
 */
@Composable
internal fun ProjectsTextField(
    title: String,
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    placeholder: String? = null,
    isFocused: Boolean = false,
    isReadOnly: Boolean = false,
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
                textStyle = ttTypography.body.copy(color = ttLabel()),
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
 * A failure, in a banner the user can dismiss.
 *
 * The message arrives as a [String] rather than an `AppError` because that is the whole
 * contract: `ProjectsListModel.errorMessage` and its siblings are the one place the module
 * turns a domain failure into text, and a view that re-worded one would be a view that
 * disagrees with the data layer.
 */
@Composable
internal fun ProjectsErrorBanner(
    message: String,
    modifier: Modifier = Modifier,
    onDismiss: (() -> Unit)? = null,
) {
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(ttRadius.control))
            .background(ttErrorFill())
            .border(1.dp, ttErrorRim(), RoundedCornerShape(ttRadius.control))
            .padding(ttSpacing.xl)
            .then(
                if (onDismiss == null) {
                    Modifier
                } else {
                    Modifier.clickable(onClick = onDismiss)
                },
            ),
        horizontalArrangement = Arrangement.spacedBy(ttSpacing.md),
        verticalAlignment = Alignment.Top,
    ) {
        ttText(text = message, style = ttTypography.callout, color = ttErrorRim(), modifier = Modifier.weight(1f))
        if (onDismiss != null) {
            ttText(text = "×", style = ttTypography.callout, color = ttErrorRim())
        }
    }
}

/**
 * A title and a body, for a list that has nothing in it.
 *
 * Both halves are parameters because every empty state in this module is a different
 * sentence - "no project yet" and "this backlog is empty" are not variants of one - and
 * collapsing them into a single empty-state component with an enum parameter would put
 * the choice back in a `when` somewhere with less context than the screen has.
 */
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
        ttText(text = body, style = ttTypography.subheadline, color = ttLabelSecondary())
    }
}

/**
 * The frame every preview in this module draws into.
 *
 * `AmbientBackdrop` takes a [Modifier] and paints a background - it does not take content
 * - so the content has to be a sibling, not a child. Writing that out seven times is how
 * one preview ends up with a `Column` floating on the raw canvas with no ambient wash
 * behind it, which looks plausible and hides contrast problems.
 *
 * Both schemes are drawn by the preview that uses it, and the convention is to pin
 * [scheme] rather than use [ThemeScheme.System] for anything about colour: a system-driven
 * preview changes with the machine's settings, and a screenshot taken on a light-mode
 * machine cannot then be compared with one taken on a dark-mode machine.
 */
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

/**
 * The user-facing name of a [WorkItemStatus].
 *
 * An exhaustive `when` over all six cases, and that is the point: `Priority` gained a
 * `Critical` case in this port, and a table lookup would have quietly rendered it as
 * "High". The compiler then fails here when a status is added, which is the only moment
 * the missing label can still be caught.
 */
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

/**
 * Turns a field's label into a stable test tag.
 *
 * `title.tagSuffix()` rather than passing a tag per call site: there are nine fields
 * across this module's two editors, and a hand-written tag at each is nine chances to
 * write `"project.name"` in one and `"projectName"` in another. Deriving it from the
 * label the field already has means a test asks for the field the same way the screen
 * names it.
 */
private fun String.tagSuffix(): String =
    lowercase().replace(' ', '.').filter { it.isLetterOrDigit() || it == '.' }

private const val FOCUS_ANIMATION_MILLIS = 150
// `val`, not `const val`: Dp is a value class, so a `const val` is not allowed.
private val FOCUSED_RING_WIDTH = 2.dp
private val RESTING_RING_WIDTH = 1.dp
