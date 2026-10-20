package com.tasktracker.sharedui

import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.receiveAsFlow

/**
 * The platform → shared seam for URLs the OS hands the app.
 *
 * Android and iOS each own a different callback for "this app was opened by a link": an
 * `Intent` in `onCreate`/`onNewIntent`, a SwiftUI `onOpenURL`. Neither can call into the
 * Compose tree directly, and neither should have to find it. They write here; [RootScreen]
 * reads and calls `Router.open`.
 *
 * A [Channel] rather than a `SharedFlow` because a link that *launches* the process arrives
 * before the root screen has a collector. An event with no subscriber yet must wait, not
 * vanish — that is the whole point of the custom scheme.
 *
 * Deliberately one consumer: `receiveAsFlow` hands each element to exactly one collector.
 * The root screen is that collector; a second one would take links away from it.
 */
class DeepLinkSource {
    private val inbox = Channel<String>(Channel.BUFFERED)

    /** The URLs in the order the platform delivered them. */
    internal val urls: Flow<String> = inbox.receiveAsFlow()

    /**
     * Called from a platform shell. Never suspends: an OS callback cannot wait for Compose,
     * and a link is small enough that the bounded buffer is not a practical limit.
     */
    fun receive(url: String) {
        inbox.trySend(url)
    }
}

/**
 * The process-wide inbox, so the platform callbacks do not need a reference to the root
 * screen. Surviving the view controller or activity is correct: the app is the process, and
 * a link delivered while a shell is being recreated is still meant for the next one.
 */
val appDeepLinks: DeepLinkSource = DeepLinkSource()

/**
 * Hands a URL from a platform shell (Android's `Intent.dataString`, iOS's
 * `URL.absoluteString`) to [appDeepLinks].
 *
 * A top-level function rather than a Swift-visible `appDeepLinks` property: the iOS entry
 * point deliberately exports no factory and no platform-typed argument, so the framework's
 * public surface stays "one controller and one sink". Swift names it
 * `DeepLinkSourceKt.receiveDeepLink(url:)`; Android calls the same function from
 * `MainActivity`, and keeping both shells on one symbol means the two cannot drift.
 */
fun receiveDeepLink(url: String) {
    appDeepLinks.receive(url)
}
