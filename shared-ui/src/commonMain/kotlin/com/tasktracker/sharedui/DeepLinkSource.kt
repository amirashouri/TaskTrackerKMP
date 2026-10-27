package com.tasktracker.sharedui

import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.receiveAsFlow

class DeepLinkSource {
    private val inbox = Channel<String>(Channel.BUFFERED)

    internal val urls: Flow<String> = inbox.receiveAsFlow()

    fun receive(url: String) {
        inbox.trySend(url)
    }
}

val appDeepLinks: DeepLinkSource = DeepLinkSource()

fun receiveDeepLink(url: String) {
    appDeepLinks.receive(url)
}
