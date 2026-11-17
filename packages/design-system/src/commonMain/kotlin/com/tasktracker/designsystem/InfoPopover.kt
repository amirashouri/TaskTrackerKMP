package com.tasktracker.designsystem

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupPositionProvider
import androidx.compose.ui.window.PopupProperties
import kotlin.math.roundToInt

const val TTInfoButtonTag = "design.info.button"
const val TTInfoPopupTag = "design.info.popup"

/**
 * A small glass `!` button that explains something in a bubble, for rows whose value alone
 * cannot say what it means.
 *
 * ## Why a popup and not more row text
 *
 * The row already has one line to give its value. An explanation beside it would either wrap
 * that line into a paragraph inside a list, or push the value out of reach — and on a setting
 * like the agent's state the sentence is worth reading once, not always taking up space. So the
 * row keeps its short value and this button carries the longer sentence behind a tap.
 *
 * The bubble is a [Popup] anchored to the button rather than a sheet: it is one sentence about
 * the control it sits next to, and covering the screen for that would be heavier than the
 * question. [InfoPopupPositionProvider] places it under the button when there is room and above
 * it otherwise, clamped to the window so a row near a screen edge still gets a readable bubble.
 *
 * @param text the sentence the bubble shows.
 * @param contentDescription what the button announces, since a bare `!` says nothing to a
 * screen reader.
 */
@Composable
fun ttInfoButton(
    text: String,
    contentDescription: String,
    modifier: Modifier = Modifier,
    tint: Color = ttLabel(),
) {
    var expanded by remember { mutableStateOf(false) }
    val density = LocalDensity.current
    val positionProvider = remember(density) {
        InfoPopupPositionProvider(
            gapPx = with(density) { InfoPopupGap.roundToPx() },
            marginPx = with(density) { InfoPopupMargin.roundToPx() },
        )
    }

    Box(modifier = modifier) {
        ttGlassIconButton(
            image = ttActionIcons.info,
            contentDescription = contentDescription,
            onClick = { expanded = !expanded },
            tint = tint,
            modifier = Modifier.testTag(TTInfoButtonTag),
            size = InfoButtonSize,
            iconSize = InfoButtonGlyphSize,
        )
        if (expanded) {
            Popup(
                popupPositionProvider = positionProvider,
                onDismissRequest = { expanded = false },
                // Focusable so the platform's own dismissal works: a tap outside the bubble,
                // the Android back gesture, or Escape on desktop all close it without the
                // caller needing a scrim of its own.
                properties = PopupProperties(focusable = true),
            ) {
                TTGlassSurface(
                    shape = RoundedCornerShape(ttRadius.banner),
                    modifier = Modifier
                        .widthIn(max = InfoPopupMaxWidth)
                        .testTag(TTInfoPopupTag)
                        .semantics { liveRegion = LiveRegionMode.Polite },
                ) {
                    ttText(
                        text = text,
                        style = ttTypography.footnote,
                        color = ttLabel(),
                        modifier = Modifier.padding(
                            horizontal = ttSpacing.lg,
                            vertical = ttSpacing.md,
                        ),
                    )
                }
            }
        }
    }
}

/** The gap between the button and its bubble, and the bubble's distance from either edge. */
private val InfoPopupGap: Dp = ttSpacing.sm
private val InfoPopupMargin: Dp = ttSpacing.md

/** Wide enough for a sentence, narrow enough to stay a bubble rather than a panel. */
private val InfoPopupMaxWidth: Dp = 280.dp

/** The button matches a row's icon well, so it annotates the row instead of topping it. */
private val InfoButtonSize: Dp = 30.dp
private val InfoButtonGlyphSize: Dp = 16.dp

/**
 * Centres the bubble on its button, below it when the window has room and above it when the
 * row is near the bottom. The horizontal clamp is what keeps the sentence readable at the end of
 * a row: the button sits at the trailing edge, so a centred bubble would otherwise hang off the
 * screen.
 */
private class InfoPopupPositionProvider(
    private val gapPx: Int,
    private val marginPx: Int,
) : PopupPositionProvider {
    override fun calculatePosition(
        anchorBounds: IntRect,
        windowSize: IntSize,
        layoutDirection: LayoutDirection,
        popupContentSize: IntSize,
    ): IntOffset {
        val anchorCenter = (anchorBounds.left + anchorBounds.right) / 2f
        val x = (anchorCenter - popupContentSize.width / 2f).roundToInt()
            .coerceIn(
                marginPx,
                (windowSize.width - popupContentSize.width - marginPx).coerceAtLeast(marginPx),
            )
        val below = anchorBounds.bottom + gapPx
        val above = anchorBounds.top - gapPx - popupContentSize.height
        val y = when {
            below + popupContentSize.height <= windowSize.height - marginPx -> below
            above >= marginPx -> above
            else -> (windowSize.height - popupContentSize.height - marginPx)
                .coerceAtLeast(marginPx)
        }
        return IntOffset(x, y)
    }
}
