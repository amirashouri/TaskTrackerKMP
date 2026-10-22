package com.tasktracker.designsystem

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

/**
 * The colour a project tag is drawn in.
 *
 * ## Why a token set, and not the tag's own hex
 *
 * A tag's colour is chosen once by an administrator and then drawn on every member's board,
 * in whichever appearance they use. If the value on the wire were a hex, the administrator
 * would be choosing a colour that is only guaranteed legible in *their* appearance — a
 * mid-grey that reads on a white canvas disappears on a black one. So the wire carries a
 * name from a closed set and this file owns two tints of each: the light one dark enough to
 * sit on the light canvas, the dark one bright enough to sit on the dark canvas.
 *
 * The same reasoning as [ttAccent], and the same two-appearance resolution: [ttTagTint] reads
 * [ttIsDarkTheme] rather than the system directly, so a pinned appearance is honoured.
 *
 * ## Why the names are generic and not semantic
 *
 * `Red` is not "urgent" and `Blue` is not "information". A tag's meaning is whatever the
 * project's administrator decides it is, and a vocabulary that implied one would be a rule
 * the product never agreed to.
 */
enum class TagTint {
    Red,
    Orange,
    Amber,
    Green,
    Teal,
    Blue,
    Indigo,
    Purple,
    Pink,
    Gray,
}

/** The colour for [tint], resolved for the appearance currently being drawn. */
@Composable
fun ttTagTint(tint: TagTint): Color = if (ttIsDarkTheme()) tint.dark else tint.light

/** The light-appearance tint. Dark enough to clear the light canvas. */
private val TagTint.light: Color
    get() = when (this) {
        TagTint.Red -> Color(0xFFB3261E)
        TagTint.Orange -> Color(0xFFB45309)
        TagTint.Amber -> Color(0xFF8A6100)
        TagTint.Green -> Color(0xFF2E6B34)
        TagTint.Teal -> Color(0xFF00695C)
        TagTint.Blue -> Color(0xFF1558A6)
        TagTint.Indigo -> Color(0xFF3F51B5)
        TagTint.Purple -> Color(0xFF6A1B9A)
        TagTint.Pink -> Color(0xFFA1255C)
        TagTint.Gray -> Color(0xFF546E7A)
    }

/** The dark-appearance tint. Bright enough to clear the dark canvas. */
private val TagTint.dark: Color
    get() = when (this) {
        TagTint.Red -> Color(0xFFFF8A80)
        TagTint.Orange -> Color(0xFFFFB74D)
        TagTint.Amber -> Color(0xFFF2C14E)
        TagTint.Green -> Color(0xFF9CCC8F)
        TagTint.Teal -> Color(0xFF4DD0C4)
        TagTint.Blue -> Color(0xFF8AB4F8)
        TagTint.Indigo -> Color(0xFF9FA8DA)
        TagTint.Purple -> Color(0xFFCE93D8)
        TagTint.Pink -> Color(0xFFF48FB1)
        TagTint.Gray -> Color(0xFFB0BEC5)
    }
