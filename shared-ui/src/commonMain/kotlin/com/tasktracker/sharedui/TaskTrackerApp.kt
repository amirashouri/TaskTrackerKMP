package com.tasktracker.sharedui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.tasktracker.designsystem.ttCanvas
import com.tasktracker.designsystem.ttSurface
import com.tasktracker.di.Resolver

/**
 * The Compose root shared by Android and the iOS `UIViewControllerRepresentable` shells.
 *
 * Both platforms call this same function, which is the point of `:shared-ui`: the screen
 * tree, its state and its navigation live in Kotlin, and the platform layer contributes
 * only system chrome (window insets, the tab and navigation containers, the glass
 * materials) plus the entry point.
 *
 * The root paints [ttCanvas] rather than a literal so the first frame matches the system's
 * appearance, including a dark-mode switch that happens while the app is running. See
 * `packages/design-system`'s kdoc for why the token is `expect`/`actual` and not a hex
 * value.
 *
 * @param resolver the started object graph. It is a parameter rather than a global lookup
 *   so the dependency is visible in the signature of the one function that needs it, and
 *   so two graphs can be rendered side by side in a screenshot test.
 */
@Composable
fun TaskTrackerApp(resolver: Resolver) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(ttCanvas()),
    ) {
        ShellPlaceholder()
    }
}

/**
 * Stands in for the routed screen tree.
 *
 * Deliberately a placeholder rather than a stub that imitates the real thing: the router,
 * the session coordinator and the feature screens are ported later and a fake navigation
 * stack would be replaced wholesale. It does establish now the shape the real content will
 * take — a surface-filled card on the canvas — so the design system's token pairing is
 * exercised on every platform from the first commit rather than discovered later.
 */
@Composable
private fun ShellPlaceholder() {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier
                .size(96.dp)
                .background(ttSurface(), RoundedCornerShape(20.dp)),
        )
    }
}
