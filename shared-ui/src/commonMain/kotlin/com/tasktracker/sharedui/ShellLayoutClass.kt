package com.tasktracker.sharedui

import androidx.compose.runtime.ProvidableCompositionLocal
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** Responsive shell layout categories derived from available width. */
enum class ShellLayoutClass {
    Compact,
    Expanded,
    Sidebar,
    ;

    val isWide: Boolean get() = this != Compact

    companion object {
        fun forWidth(width: Dp): ShellLayoutClass = when {
            width >= ShellSidebarMinimumWidth -> Sidebar
            width >= ShellExpandedMinimumWidth -> Expanded
            else -> Compact
        }
    }
}

val ShellExpandedMinimumWidth: Dp = 840.dp
val ShellSidebarWidth: Dp = 280.dp
val ShellSidebarMinimumWidth: Dp = ShellExpandedMinimumWidth + ShellSidebarWidth

val LocalShellLayoutClass: ProvidableCompositionLocal<ShellLayoutClass> =
    compositionLocalOf { ShellLayoutClass.Compact }