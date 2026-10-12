package com.tasktracker.designsystem

import kotlinx.coroutines.test.runTest
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * [ResourceStringsScenarios] on the Android host.
 *
 * The runner annotation is the whole reason this file exists separately, and the reason the
 * body is in `commonTest`: without Robolectric the host's stub `android.jar` throws
 * `RuntimeException: Method getSystem in android.content.res.Resources not mocked` before the
 * first assertion, and an annotation naming a JVM runner cannot sit in `commonTest` beside the
 * iOS target. Measured, not assumed — that is the exact failure this file was split out to fix.
 *
 * `@Config(sdk = [34])` matches the Android host's other Robolectric entry points.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ResourceStringsAndroidHostTest {

    @Test
    fun theGeneratedAccessorsResolveToTheDeclaredText() =
        runTest { ResourceStringsScenarios.theGeneratedAccessorsResolveToTheDeclaredText() }
}
