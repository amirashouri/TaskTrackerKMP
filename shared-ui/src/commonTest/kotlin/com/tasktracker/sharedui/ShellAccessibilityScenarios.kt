package com.tasktracker.sharedui

import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * What Reduce Transparency does to the value the glass material receives.
 *
 * ## Why this is asserted on the rule rather than on the app root
 *
 * The same reason `ShellMotionScenarios` asserts on `ShellMotionProvider`: the shell's entry
 * point has no content slot, and the intensity is consumed by `ttTheme`'s composition local
 * rather than by a node in the semantics tree. A test that composed the whole root would have
 * to build a resolver and then read a local out of the middle of the tree, which is not
 * possible from outside.
 *
 * The rendered half is not missing: `GlassIntensityRenderTest` in `:packages:design-system`
 * renders the material at `0f` and holds it to the opaque fill. This file holds the other
 * half, that Reduce Transparency is what produces `0f`, and that the stored value survives.
 */
class ShellAccessibilityScenarios {

    @Test
    fun `reduce transparency forces the opaque fill regardless of the stored slider`() {
        assertEquals(0f, effectiveGlassIntensity(stored = 1f, reduceTransparency = true))
        assertEquals(0f, effectiveGlassIntensity(stored = 0.42f, reduceTransparency = true))
        assertEquals(0f, effectiveGlassIntensity(stored = 0f, reduceTransparency = true))
    }

    @Test
    fun `turning reduce transparency off restores the stored value untouched`() {
        assertEquals(0.42f, effectiveGlassIntensity(stored = 0.42f, reduceTransparency = false))
        assertEquals(1f, effectiveGlassIntensity(stored = 1f, reduceTransparency = false))
    }
}
