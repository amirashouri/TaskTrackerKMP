package com.tasktracker.designsystem

import androidx.compose.foundation.border
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Shape

/**
 * A visible keyboard-focus state for a glass control.
 *
 * ## Why the design system needs this
 *
 * The controls here pass `indication = null` deliberately — a ripple on glass reads as a
 * rectangle the material does not have, and the tab bar's lens is the press feedback. The
 * cost was that a keyboard user had nothing to look at: pointer press, selection and focus
 * all look the same, which on desktop makes Tab navigation blind.
 *
 * The ring is drawn as a border **inside the control's own bounds**, not as padding or an
 * outline. That is the part that makes it safe to add to every pill and cell: it changes no
 * measurement, so a focused row does not shift the layout under it. The accent supplies the
 * colour because `AccentContrastTest` already holds it at 3:1 against each canvas.
 *
 * Apply it **before** the node's `clickable`/`selectable`, so `onFocusChanged` observes the
 * focus target that modifier installs:
 *
 * ```kotlin
 * Modifier
 *     .ttFocusRing(CircleShape)
 *     .clickable { … }
 * ```
 *
 * ## Why not `indication`
 *
 * `Indication` is driven by the interaction source and is the right seam for a press wash.
 * A focus ring is a stable state rather than a transition, and this design system has no
 * Material indication to inherit one from; a border drawn from `onFocusChanged` is two lines
 * and is identical on every target.
 */
@Composable
fun Modifier.ttFocusRing(
    shape: Shape = RoundedCornerShape(ttRadius.control),
): Modifier {
    var focused by remember { mutableStateOf(false) }
    val color = ttAccent()
    return this
        .onFocusChanged { focused = it.isFocused }
        .then(
            if (focused) Modifier.border(ttFocusRingWidth, color, shape) else Modifier,
        )
}
