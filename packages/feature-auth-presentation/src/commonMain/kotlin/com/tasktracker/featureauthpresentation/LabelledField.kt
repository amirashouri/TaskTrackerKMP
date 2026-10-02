package com.tasktracker.featureauthpresentation

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.tasktracker.designsystem.AmbientBackdrop
import com.tasktracker.designsystem.ThemeScheme
import com.tasktracker.designsystem.ttAccent
import com.tasktracker.designsystem.ttCanvas
import com.tasktracker.designsystem.ttLabelSecondary
import com.tasktracker.designsystem.ttRadius
import com.tasktracker.designsystem.ttSeparator
import com.tasktracker.designsystem.ttSpacing
import com.tasktracker.designsystem.ttSurface
import com.tasktracker.designsystem.ttText
import com.tasktracker.designsystem.ttTheme
import com.tasktracker.designsystem.ttTypography

/**
 * A labelled text field on a rounded card, with a focus ring.
 *
 * Built rather than borrowed for two reasons, both from the original: the stock styles cannot
 * express a focus ring that reads as "you are here" against a tinted backdrop, and a plain
 * field is needed so the *card* supplies the shape rather than the field supplying its own.
 *
 * ## `isFocused` is a value, not a focus binding
 *
 * The original takes a plain `Bool` and this keeps it. The field owns the focus — that is
 * what the parent's `FocusRequester` is for — and this component only wants to *draw* the
 * consequence. Threading a focus binding through would make this type generic over which
 * field is focused, for no benefit to either caller.
 *
 * The ring animates rather than switching, because the original animates the change
 * (`.animation(.easeInOut(duration: 0.15), value: isFocused)`) and a border that snaps
 * between two colours is a visible flash on every tap into a field.
 */
@Composable
fun LabelledField(
    title: String,
    isFocused: Boolean,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    val accent = ttAccent()
    val separator = ttSeparator()
    val ringColor by animateColorAsState(
        targetValue = if (isFocused) accent else separator,
        animationSpec = tween(FOCUS_ANIMATION_MILLIS),
        label = "LabelledField ring",
    )
    val shape = RoundedCornerShape(ttRadius.control)

    Column(
        modifier = modifier
            .clip(shape)
            .background(ttSurface())
            .border(
                width = if (isFocused) FOCUSED_RING_WIDTH else RESTING_RING_WIDTH,
                color = ringColor,
                shape = shape,
            )
            .padding(horizontal = ttSpacing.xxl, vertical = ttSpacing.xl),
        verticalArrangement = Arrangement.spacedBy(ttSpacing.sm),
    ) {
        ttText(
            text = title,
            style = ttTypography.captionEmphasised,
            color = ttLabelSecondary(),
        )
        content()
    }
}

/**
 * The focus ring is thicker as well as a different colour.
 *
 * Not decoration. Accent-vs-separator alone is a hue difference, and a hue difference is not
 * legible to a viewer with the common red/green deficiency — so the ring would be invisible
 * to some users in exactly the state where they most need it. The 1.5/1 split is the
 * original's, and it is the reason the original animates the *width* as well as the colour.
 */
private val FOCUSED_RING_WIDTH: Dp = 1.5.dp
private val RESTING_RING_WIDTH: Dp = 1.dp
private const val FOCUS_ANIMATION_MILLIS = 150

@Preview(name = "Labelled field", showBackground = true, widthDp = 360)
@Composable
private fun LabelledFieldPreview() {
    ttTheme(ThemeScheme.System) {
        Box(modifier = Modifier.fillMaxWidth().background(ttCanvas())) {
            AmbientBackdrop()
            Column(
                modifier = Modifier.padding(ttSpacing.screenGutterLarge),
                verticalArrangement = Arrangement.spacedBy(ttSpacing.xl),
            ) {
                LabelledField(title = "Email", isFocused = false) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        ttText("amir@example.com", style = ttTypography.body)
                    }
                }
                LabelledField(title = "Password", isFocused = true) {
                    ttText("••••••••", style = ttTypography.body)
                }
            }
        }
    }
}
