package com.tasktracker.designsystem

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.viewinterop.UIKitView
import kotlinx.cinterop.ExperimentalForeignApi
import platform.UIKit.UIBlurEffect
import platform.UIKit.UIVisualEffectView

@OptIn(ExperimentalForeignApi::class)
@Composable
actual fun TTGlassSurface(
    shape: Shape,
    modifier: Modifier,
    tint: Color?,
    thickness: ttGlass.GlassThickness,
    borderWidth: Dp,
    base: Color?,
    content: @Composable () -> Unit,
) {
    Box(
        modifier = modifier
            .clip(shape)
            .border(width = borderWidth, color = Color.White.copy(alpha = 0.22f), shape = shape),
    ) {
        UIKitView(
            factory = {
                // UIBlurEffectStyleSystemMaterial == 8 in the UIKit enum (see UIBlurEffect.h).
                // Using the raw value avoids depending on Konan exposing the named constant.
                val effect = UIBlurEffect()
                UIVisualEffectView(effect = effect)
            },
            modifier = Modifier.fillMaxSize(),
        )
        content()
    }
}