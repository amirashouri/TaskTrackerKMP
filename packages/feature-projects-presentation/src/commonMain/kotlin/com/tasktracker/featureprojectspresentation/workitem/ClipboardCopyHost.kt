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

/** Wrapper hosting clipboard write operations and temporary copy confirmation toasts. */
@Composable
internal fun ClipboardCopyHost(
    modifier: Modifier = Modifier,
    content: @Composable (onCopy: (String) -> Unit) -> Unit,
) {
    @Suppress("DEPRECATION")
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
                    .padding(bottom = LocalTabBarScrollUnderInset.current + ttSpacing.xxl),
            )
        }
    }
}

/**
 * Uses deprecated [ClipboardManager] to avoid platform-specific `expect/actual` seams
 * until `ClipEntry` gains a common constructor in Compose Multiplatform.
 */
@Suppress("DEPRECATION")
private fun copyToClipboard(clipboard: ClipboardManager, text: String) {
    clipboard.setText(AnnotatedString(text))
}

/** Duration of the temporary copy confirmation toast banner. */
internal const val COPIED_TOAST_MILLIS = 2_000L