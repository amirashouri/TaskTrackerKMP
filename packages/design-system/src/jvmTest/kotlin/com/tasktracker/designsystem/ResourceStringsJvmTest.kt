package com.tasktracker.designsystem

import kotlinx.coroutines.test.runTest
import kotlin.test.Test

/**
 * [ResourceStringsScenarios] on the JVM host — the Desktop-relevant one.
 *
 * The Desktop app is a JVM process, so this is the host whose answer decides whether a
 * Desktop launch reads `composeResources` or hits a missing bundle.
 */
class ResourceStringsJvmTest {

    @Test
    fun theGeneratedAccessorsResolveToTheDeclaredText() =
        runTest { ResourceStringsScenarios.theGeneratedAccessorsResolveToTheDeclaredText() }
}
