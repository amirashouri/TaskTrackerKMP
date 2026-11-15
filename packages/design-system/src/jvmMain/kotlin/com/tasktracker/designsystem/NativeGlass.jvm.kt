package com.tasktracker.designsystem

import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Dp

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
    Box(modifier = modifier.ttGlassSurface(shape = shape, tint = tint, thickness = thickness, borderWidth = borderWidth, base = base)) {
        content()
    }
}