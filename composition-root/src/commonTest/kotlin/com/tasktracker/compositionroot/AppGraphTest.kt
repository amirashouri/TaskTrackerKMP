package com.tasktracker.compositionroot

import com.tasktracker.di.DiException
import com.tasktracker.di.Resolver
import kotlin.test.Test
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * The seam the whole port depends on: one describable object graph, reachable from one
 * place, that no other module can bypass.
 */
class AppGraphTest {

    @Test
    fun `start yields a started container`() {
        val resolver = AppGraph.start()

        try {
            assertTrue(resolver.isStarted)
        } finally {
            resolver.stop()
        }
    }

    @Test
    fun `an unregistered dependency fails loudly rather than resolving to something`() {
        val resolver = AppGraph.start()

        try {
            assertFailsWith<DiException.Unregistered> {
                resolver.resolve(Resolver::class)
            }
        } finally {
            resolver.stop()
        }
    }

    @Test
    fun `the graph is empty until features are ported`() {
        val resolver = AppGraph.start()

        try {
            assertFalse(resolver.registeredNames.isNotEmpty(), "expected an empty graph")
        } finally {
            resolver.stop()
        }
    }
}
