package com.tasktracker.designsystem

import androidx.compose.runtime.Composable
import androidx.compose.runtime.ProvidableCompositionLocal
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Dp

/**
 * Native-backed glass surface.
 *
 * On iOS this wraps its content in a [platform.UIKit.UIVisualEffectView] using the
 * system glass/blur material so the surface reads as a real native glass panel rather
 * than a painted approximation. On every other target it falls back to the painted
 * [Modifier.ttGlassSurface] so behaviour and pixel tests stay unchanged there.
 *
 * This is the migration seam for moving the rest of the app off the painted material:
 * call sites that currently chain `.ttGlassSurface(...)` can be moved onto this
 * composable one at a time without changing the surrounding layout.
 */
@Composable
expect fun TTGlassSurface(
    shape: Shape,
    modifier: Modifier = Modifier,
    tint: Color? = null,
    thickness: ttGlass.GlassThickness = ttGlass.GlassThickness.STANDARD,
    borderWidth: Dp = ttGlass.hairlineWidth,
    base: Color? = null,
    content: @Composable () -> Unit,
)

/**
 * Whether [TTGlassSurface] may back its rim with a native view on this host.
 *
 * Off everywhere by default, which is what the painted fallback always did. Only the iOS
 * application entry point turns it on. A native backing view is UIKit interop, and the
 * Compose test scene (`runComposeUiTest`) has no interop container to host it, so a tree
 * composed by a scenario must not reach for one — otherwise every screen that draws a
 * surface fails to compose on that host with `LocalInteropContainer not provided`.
 * Gating it here rather than sniffing for a test host keeps the test harness out of
 * production code: the one host that can host a UIKit view is the one that says so.
 */
val LocalNativeGlassEnabled: ProvidableCompositionLocal<Boolean> = compositionLocalOf { false }