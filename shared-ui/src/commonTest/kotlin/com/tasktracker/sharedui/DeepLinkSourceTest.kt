package com.tasktracker.sharedui

import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

class DeepLinkSourceTest {
    @Test
    fun aLinkDeliveredBeforeTheShellCollectsIsKept() = runTest {
        val source = DeepLinkSource()

        source.receive("tasktracker://settings")

        // The cold-start case: the OS callback that carries the link runs before Compose has
        // a collector. A broadcast flow would have dropped it; the channel's buffer is what
        // makes opening the app from a link work at all.
        assertEquals("tasktracker://settings", source.urls.first())
    }

    @Test
    fun linksAreDeliveredInTheOrderThePlatformSentThem() = runTest {
        val source = DeepLinkSource()
        source.receive("tasktracker://projects")
        source.receive("tasktracker://connect?url=https%3A%2F%2Ftasks.example.com")

        assertEquals(
            listOf(
                "tasktracker://projects",
                "tasktracker://connect?url=https%3A%2F%2Ftasks.example.com",
            ),
            source.urls.take(2).toList(),
        )
    }
}
