package com.tasktracker.featureprojectspresentation

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ClipboardManager
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import com.tasktracker.designsystem.LocalTabBarScrollUnderInset
import com.tasktracker.designsystem.ttSpacing
import com.tasktracker.designsystem.ttToast
import com.tasktracker.resources.Res
import com.tasktracker.resources.work_item_copied
import kotlinx.coroutines.delay
import org.jetbrains.compose.resources.stringResource

/**
 * Hosts a surface's copy control: owns the clipboard write and the confirmation.
 *
 * ## Why a host and not a helper per screen
 *
 * A copy is two things that must agree — the platform write, and a confirmation shown by the
 * surface that was copied from. The board and the backlog both copy a task's text, and a second
 * copy of the timer here is exactly how the two surfaces would come to disagree about how long a
 * confirmation stays up. The host keeps the rule in one place; [content] only decides where its
 * cards or rows sit and which text they hand back through `onCopy`.
 *
 * The counter is not shown. It exists to be the `LaunchedEffect` key, so a second copy restarts
 * the clock rather than leaving the first copy's clock running and the toast expiring early.
 *
 * The toast is drawn in this `Box` rather than in [content]'s layout: a toast inserted into a
 * column would shift the lanes or rows up while it is visible, which is the layout jump a
 * confirmation is not allowed to cause.
 */
@Composable
internal fun ClipboardCopyHost(
    modifier: Modifier = Modifier,
    content: @Composable (onCopy: (String) -> Unit) -> Unit,
) {
    @Suppress("DEPRECATION") // See copyToClipboard for why the deprecated half is used.
    val clipboard = LocalClipboardManager.current
    var copies by remember { mutableIntStateOf(0) }
    LaunchedEffect(copies) {
        if (copies == 0) return@LaunchedEffect
        delay(COPIED_TOAST_MILLIS)
        copies = 0
    }

    Box(modifier = modifier.fillMaxSize()) {
        content { text ->
            copyToClipboard(clipboard, text)
            copies++
        }

        if (copies > 0) {
            ttToast(
                message = stringResource(Res.string.work_item_copied),
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(horizontal = ttSpacing.xxl)
                    // Clear of the tab bar, so the glass bar cannot cover the confirmation.
                    .padding(bottom = LocalTabBarScrollUnderInset.current + ttSpacing.xxl),
            )
        }
    }
}

/**
 * Writes plain text to the platform clipboard.
 *
 * `ClipboardManager` is the **deprecated** half of Compose's clipboard API, and it is used
 * deliberately. Its replacement, `Clipboard`, takes a `ClipEntry`, and in Compose
 * Multiplatform 1.12.1 `ClipEntry` has no common constructor: the iOS builder is
 * `@ExperimentalComposeUiApi` and every platform's constructor is `internal`. Writing through
 * the deprecated interface is therefore the only way to copy a string from `commonMain`
 * without an `expect`/`actual` seam and three platform implementations for one call.
 *
 * Isolated here rather than annotated at the call site so the suppression is one line and the
 * reason sits next to it. When `ClipEntry` gains a common constructor, this function is the
 * only place that has to change.
 */
@Suppress("DEPRECATION")
private fun copyToClipboard(clipboard: ClipboardManager, text: String) {
    clipboard.setText(AnnotatedString(text))
}

/**
 * How long the copy confirmation stays up.
 *
 * Two seconds is long enough to read four words and short enough that it is gone before the
 * next card is picked up. The timer lives with the host rather than in `ttToast` because the
 * host is what knows a copy happened; see the toast's own docs for why timing is the caller's.
 *
 * `internal` so the scenario that waits it out can hold the same number the host uses, rather
 * than a second copy of it.
 */
internal const val COPIED_TOAST_MILLIS = 2_000L
