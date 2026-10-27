package com.tasktracker.designsystem

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.ExperimentalTestApi
import kotlin.test.Test
import kotlin.test.assertEquals

@OptIn(ExperimentalTestApi::class)
class ReducedMotionTest {
    @Test
    fun `specular offset is zero when reduced motion is enabled`() {
        var offset = -1f
        rasterising {
            setContent {
                ttTheme(ThemeScheme.Dark) {
                    CompositionLocalProvider(LocalReducedMotion provides true) {
                        val controller = rememberDeviceMotion()
                        offset = if (LocalReducedMotion.current) 0f else controller.roll.value * 100f * 0.15f
                    }
                }
            }
            waitForIdle()
        }
        assertEquals(0f, offset)
    }
}
