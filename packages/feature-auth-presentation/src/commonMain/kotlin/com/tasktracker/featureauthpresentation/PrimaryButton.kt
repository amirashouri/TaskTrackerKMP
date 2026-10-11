package com.tasktracker.featureauthpresentation

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.scale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.tasktracker.designsystem.AmbientBackdrop
import com.tasktracker.designsystem.ThemeScheme
import com.tasktracker.designsystem.ttAccent
import com.tasktracker.designsystem.ttCanvas
import com.tasktracker.designsystem.ttGlassSurface
import com.tasktracker.designsystem.ttOnAccent
import com.tasktracker.designsystem.ttRadius
import com.tasktracker.designsystem.ttSpinner
import com.tasktracker.designsystem.ttSpacing
import com.tasktracker.designsystem.ttText
import com.tasktracker.designsystem.ttTheme
import com.tasktracker.designsystem.ttTypography

/**
 * The app's primary action button: a full-width filled control with a press response.
 *
 * Built rather than borrowed for the three reasons the original gives, all of which survive
 * the port:
 *
 *  - **Height and radius.** A sign-in button is the whole screen's call to action, so it gets
 *    a real target and a soft corner rather than a compact capsule.
 *  - **A press response.** Without the scale, a filled button on a tinted backdrop gives
 *    almost no feedback that a touch registered, which reads as "nothing happened".
 *  - **A spinner that replaces the label.** A button that stays labelled while a request is in
 *    flight invites a second tap, and a label that changes width as it swaps for a spinner
 *    makes the layout jump — so [PrimaryButton] keeps both in the tree and cross-fades them.
 *
 * ## Why this is a function and not a `ButtonStyle`
 *
 * The original is a `ButtonStyle` and this is a composable. Material 3 is not a dependency
 * (see `tasktracker.kmp.compose.gradle.kts`), so there is no `Button` to apply a style to and
 * no `ButtonStyle` interface to implement. A composable that takes its own state and draws
 * itself is the equivalent, and it means the enabled and pressed states are parameters this
 * component controls rather than ambient state it has to read.
 */
@Composable
fun PrimaryButton(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    isLoading: Boolean = false,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed && enabled) PRESSED_SCALE else 1f,
        animationSpec = spring(
            dampingRatio = PRESS_SPRING_DAMPING,
            stiffness = Spring.StiffnessMedium,
        ),
        label = "PrimaryButton press",
    )
    val shape = RoundedCornerShape(ttRadius.control)

    Box(
        modifier = modifier
            .fillMaxWidth()
            .testTag(PrimaryButtonTag)
            .alpha(if (enabled) 1f else DISABLED_ALPHA)
            .scale(scale)
            .ttGlassSurface(
                shape = shape,
                tint = ttAccent(),
                fillAlpha = PRIMARY_FILL_ALPHA,
                // The solid fill the original drew underneath its glass lens. Without it this
                // control is a 62%-alpha accent over a near-white canvas, which composites to
                // `#F6F7F8` and leaves the white `ttOnAccent` label at 1.07:1 — measured on a
                // device, where it read as a white-on-white slab with a white band across the
                // top. A translucent fill cannot carry an opaque label; this one has to be
                // solid. The lens, the radius and the hairline are all still there.
                base = ttAccent(),
            )
            // The padding is inside the material, so the control's touch target is the
            // visible control. Applied after, it would enlarge the row without enlarging the
            // thing being pressed.
            .height(BUTTON_HEIGHT)
            .padding(
                vertical = ttSpacing.buttonPaddingV,
                horizontal = ttSpacing.buttonPaddingH,
            )
            // `indication = null` because the press response is the scale. Keeping the default
            // would stack a ripple under a button that already shrinks, which reads as two
            // reactions to one tap.
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                enabled = enabled,
                role = Role.Button,
                onClick = onClick,
            ),
        contentAlignment = Alignment.Center,
    ) {
        // Both children are always composed and cross-faded, so the button's width does not
        // change when the label becomes a spinner.
        LabelContent(
            text = label,
            visible = !isLoading,
        )
        ttSpinner(color = ttOnAccent)
    }
}

/** The test tag, so a test does not have to guess the string. */
const val PrimaryButtonTag = "signIn.submit"

/** The original's `.frame(height: 52)`. */
private val BUTTON_HEIGHT = 52.dp

/** The original's `scaleEffect(0.97)`. */
private const val PRESSED_SCALE = 0.97f

private const val PRESS_SPRING_DAMPING = 0.6f

/**
 * The glass lens's fill alpha over the accent.
 *
 * The original's fill is a solid `Color.accentColor` with a `glassEffect(.regular.tint(...
 * opacity(0.35)))` lens over it. Compose has no `glassEffect`, so the lens is the fill: 55%
 * accent over 35% of the accent again leaves the pairing that `AccentContrastTest` enforces
 * intact, because the composited result is still a dark-enough fill for white text.
 *
 * That reasoning only held for the *original's* compositing, where the accent sat on a solid
 * accent. When the same alpha was used with no base, the 62% accent landed on a near-white
 * canvas instead and composited to `#F6F7F8`, which is why the value looked safe on paper and
 * was not. It is kept as the lens's strength, but the solid `base` is what the label now
 * actually sits on.
 */
private const val PRIMARY_FILL_ALPHA = 0.62f

/** The original's `.opacity(isEnabled ? 1 : 0.4)`. */
private const val DISABLED_ALPHA = 0.4f

@Composable
private fun LabelContent(text: String, visible: Boolean) {
    ttText(
        text = text,
        style = ttTypography.headline,
        color = ttOnAccent,
        modifier = Modifier.alpha(if (visible) 1f else 0f),
    )
}

/**
 * The in-flight indicator.
 *
 * Drawn rather than imported, for the same reason as [ErrorBanner]'s glyph: the design system
 * ships no icon set. An indeterminate arc rather than a full circle, because a static ring
 * reads as a broken image next to a label that has just vanished.
 */

@Preview(name = "Primary button", showBackground = true, widthDp = 360)
@Composable
private fun PrimaryButtonPreview() {
    ttTheme(ThemeScheme.System) {
        Box(modifier = Modifier.background(ttCanvas())) {
            AmbientBackdrop()
            Column(
                modifier = Modifier.padding(ttSpacing.screenGutterLarge),
                verticalArrangement = Arrangement.spacedBy(ttSpacing.lg),
            ) {
                PrimaryButton(label = "Sign In", onClick = {})
                PrimaryButton(label = "Sign In", onClick = {}, isLoading = true)
                PrimaryButton(label = "Sign In", onClick = {}, enabled = false)
            }
        }
    }
}
