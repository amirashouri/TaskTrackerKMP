package com.tasktracker.sharedui

import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier

/**
 * Measures the width it is given once, resolves it to a [ShellLayoutClass], and publishes it.
 *
 * The whole of the adaptive plumbing: read `maxWidth`, ask
 * [ShellLayoutClass.forWidth], provide the answer. Everything below decides what to *do* with it.
 *
 * ## It is a `BoxWithConstraints`, and that is a real cost paid on purpose
 *
 * `BoxWithConstraints` is a `SubcomposeLayout`: its content is composed in a second pass, against
 * constraints the first pass produced. That is one extra measure of everything below it, and it
 * is the price of knowing the width in composition rather than reading it off a `Modifier`. The
 * alternative — an `expect`/`actual` returning a size class — avoids the subcomposition and is
 * rejected in [ShellLayoutClass]'s note for a different reason: it needs three actuals, and the
 * JVM test host has no real window for the `jvm` one to read.
 *
 * ## It is placed **below the splash gate**, and that placement is load-bearing
 *
 * This wraps the signed-in area rather than the whole root, so the splash and the sign-in tree
 * are composed outside it. [RootScreen] documents at length why nothing may add a measure pass
 * ahead of the splash handoff — a board's models must not be built and start observing behind a
 * splash that has not finished drawing — and a `SubcomposeLayout` above the splash is exactly
 * such a pass. The two branches that do not need a layout class do not pay for one.
 *
 * ## It fills its parent rather than wrapping its content
 *
 * `fillMaxSize` and not a wrap. The width this reports is the width the *shell* was offered, and
 * a wrapper that sized itself to its content would report the content's width back — which is
 * circular, and would report `Compact` for any screen whose content happened to be narrow. The
 * parent is already the full window, so this changes no layout; it only makes the number being
 * read the one that was meant.
 */
@Composable
fun ShellLayout(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    BoxWithConstraints(modifier = modifier.fillMaxSize()) {
        CompositionLocalProvider(
            LocalShellLayoutClass provides ShellLayoutClass.forWidth(maxWidth),
        ) {
            content()
        }
    }
}
