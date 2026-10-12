package com.tasktracker.designsystem

import kotlinx.coroutines.test.runTest
import kotlin.test.Test

/** [ResourceStringsScenarios] on the iOS host, where resources come from the bundled framework. */
class ResourceStringsIosTest {

    @Test
    fun theGeneratedAccessorsResolveToTheDeclaredText() =
        runTest { ResourceStringsScenarios.theGeneratedAccessorsResolveToTheDeclaredText() }
}
