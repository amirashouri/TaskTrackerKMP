package com.tasktracker.designsystem

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.alloc
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.ptr
import kotlinx.cinterop.value
import platform.CoreGraphics.CGFloatVar
import platform.UIKit.UIColor
import platform.UIKit.UITraitCollection
import platform.UIKit.currentTraitCollection
import platform.UIKit.resolvedColorWithTraitCollection
import platform.UIKit.secondarySystemBackgroundColor
import platform.UIKit.separatorColor
import platform.UIKit.systemBackgroundColor

/**
 * iOS is the original's home platform, so these are the same lookups `Palette.swift`
 * performs. The Kotlin names carry a `Color` suffix (`UIColor.systemBackgroundColor`)
 * because Kotlin/Native appends it where the bare ObjC name would collide with the type.
 *
 * These stay `@Composable` rather than cached in a `val` for one reason: a `UIColor` is
 * *dynamic*. Its components change with the trait collection, and a value read once
 * during composition would keep painting the light canvas after an appearance switch.
 */
@Composable
actual fun ttCanvas(): Color = UIColor.systemBackgroundColor.toComposeColor()

@Composable
actual fun ttSurface(): Color = UIColor.secondarySystemBackgroundColor.toComposeColor()

@Composable
actual fun ttSeparator(): Color = UIColor.separatorColor.toComposeColor()

/**
 * Converts a dynamic `UIColor` to a Compose [Color], resolved against the trait
 * collection that is current at composition time.
 *
 * Compose Multiplatform 1.12.1 ships no `UIColor` interop — there is no `toComposeColor`
 * in any of its artifacts — so the port does the conversion itself. The resolution step
 * is explicit rather than relying on `getRed` happening to resolve dynamically: a
 * semantic token that quietly stopped adapting in one OS version is exactly the bug this
 * file exists to prevent.
 *
 * @see Palette.swift, which is the behaviour being preserved.
 */
@OptIn(ExperimentalForeignApi::class)
private fun UIColor.toComposeColor(): Color {
    val resolved = resolvedColorWithTraitCollection(UITraitCollection.currentTraitCollection)
    return memScoped {
        val red = alloc<CGFloatVar>()
        val green = alloc<CGFloatVar>()
        val blue = alloc<CGFloatVar>()
        val alpha = alloc<CGFloatVar>()
        if (!resolved.getRed(red.ptr, green.ptr, blue.ptr, alpha.ptr)) {
            // A colour outside the RGB space (a pattern colour) has no RGB components.
            // Opaque black is a visible, debuggable failure; a transparent one would
            // silently delete the canvas.
            return@memScoped Color.Black
        }
        Color(
            red = red.value.toFloat(),
            green = green.value.toFloat(),
            blue = blue.value.toFloat(),
            alpha = alpha.value.toFloat(),
        )
    }
}
