package com.tasktracker.designsystem

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.InternalComposeUiApi
import androidx.compose.ui.LocalSystemTheme
import androidx.compose.ui.SystemTheme

@OptIn(InternalComposeUiApi::class)
@Suppress("DEPRECATION")
@Composable
internal actual fun WithSystemDarkAppearance(content: @Composable () -> Unit) {
    CompositionLocalProvider(LocalSystemTheme provides SystemTheme.Dark, content = content)
}
