package com.tasktracker.designsystem

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview

/**
 * Previews for the design system itself.
 *
 * ## Why these are worth writing when a test suite exists
 *
 * Every invariant the tests assert is a *number*, and numbers are what a design system's
 * components are worst at. A corner radius of 16 instead of 14 passes every test in the
 * module. So the tests prove the tokens hold their values, and these prove the components
 * are usable — which is a different question, and the one a designer asks at 10am.
 *
 * Each of the original's four source files had at least one `#Preview`; these are their
 * counterparts, and they are pinned per appearance rather than left to the IDE, because a
 * preview that follows the *developer's* system setting is a preview that only ever gets
 * checked in one of the two appearances it has to work in.
 */
@Preview(name = "Mark", showBackground = true)
@Composable
private fun AppMarkPreview() {
    ttPreviewSurface {
        AppMark()
    }
}

@Preview(name = "Mark on backdrop", showBackground = true)
@Composable
private fun AppMarkOnBackdropPreview() {
    ttPreviewSurface { AppMark() }
}

@Preview(name = "Backdrop, light", showBackground = true)
@Composable
private fun AmbientBackdropLightPreview() {
    ttPreviewSurface(ThemeScheme.Light) { Box(Modifier.fillMaxSize()) }
}

@Preview(name = "Backdrop, dark", showBackground = true)
@Composable
private fun AmbientBackdropDarkPreview() {
    ttPreviewSurface(ThemeScheme.Dark) { Box(Modifier.fillMaxSize()) }
}

/**
 * The picker in both states, because a segmented control's bug is almost always a
 * *selected* one — the unselected half is the easy half to get right.
 */
@Preview(name = "Segmented picker", showBackground = true)
@Composable
private fun SegmentedPickerPreview() {
    ttPreviewSurface {
        Column(verticalArrangement = Arrangement.spacedBy(ttSpacing.lg)) {
            TTGlassSegmentedPicker(
                options = listOf("Board", "Backlog"),
                selection = "Board",
                onSelect = {},
            ) { it }

            TTGlassSegmentedPicker(
                options = listOf("Board", "Backlog"),
                selection = "Backlog",
                onSelect = {},
            ) { it }
        }
    }
}

@Preview(name = "Glass card", showBackground = true)
@Composable
private fun GlassCardPreview() {
    ttPreviewSurface {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .ttGlassCard()
                .padding(ttSpacing.xxl),
            verticalArrangement = Arrangement.spacedBy(ttSpacing.sm),
        ) {
            ttText("TT-14 Draft the board", style = ttTypography.headline)
            ttText(
                "A glass card floats over the scrolling surface, so the material has " +
                    "something behind it to sample.",
                style = ttTypography.footnote,
                color = ttLabelSecondary(),
            )
        }
    }
}

@Preview(name = "Glass buttons", showBackground = true)
@Composable
private fun GlassButtonsPreview() {
    ttPreviewSurface {
        Row(horizontalArrangement = Arrangement.spacedBy(ttSpacing.md)) {
            ttGlassButton("Add", onClick = {})
            ttGlassButton("Archive", onClick = {}, style = TTGlassButtonStyle.prominent())
        }
    }
}

/**
 * The whole palette at once, in both appearances.
 *
 * The one preview a designer actually opens, and the reason the label tokens are here at
 * all: a palette with no text sample cannot be judged, and a palette whose only swatch is
 * the canvas cannot reveal that `ttLabel` and `ttCanvas` are the same colour.
 */
@Preview(name = "Palette", showBackground = true, widthDp = 320)
@Composable
private fun PalettePreview() {
    val samples = listOf(
        "canvas" to ttCanvas(),
        "surface" to ttSurface(),
        "label" to ttLabel(),
        "labelSecondary" to ttLabelSecondary(),
        "separator" to ttSeparator(),
        "accent" to ttAccent(),
        "onAccent" to ttOnAccent,
    )
    ttPreviewSurface {
        Column(
            modifier = Modifier.padding(ttSpacing.xxl),
            verticalArrangement = Arrangement.spacedBy(ttSpacing.sm),
        ) {
            for ((name, colour) in samples) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(ttSpacing.md),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    // A fixed-size swatch rather than one that wraps its own content: a
                    // swatch sized by a child is a swatch whose size changes when the
                    // palette is edited, which is the one thing a palette preview must not
                    // do.
                    Box(
                        modifier = Modifier
                            .size(ttSpacing.screenGutterLarge)
                            .background(colour, RoundedCornerShape(ttRadius.row)),
                    )
                    ttText(name, style = ttTypography.footnote)
                }
            }
        }
    }
}

/**
 * The type scale, in one column.
 *
 * [textAlign] on the samples rather than on the labels is deliberate: the samples are the
 * content, and centring the content rather than the caption is what makes a wrong
 * `lineHeight` obvious — a scale where the line heights do not track the font sizes shows
 * up here as text that crowds the line above it.
 */
@Preview(name = "Type scale", showBackground = true, widthDp = 360)
@Composable
private fun TypeScalePreview() {
    val scale = listOf(
        "largeTitle" to ttTypography.largeTitle,
        "title" to ttTypography.title,
        "titleSmall" to ttTypography.titleSmall,
        "headline" to ttTypography.headline,
        "body" to ttTypography.body,
        "callout" to ttTypography.callout,
        "subheadlineSelected" to ttTypography.subheadlineSelected,
        "subheadline" to ttTypography.subheadline,
        "button" to ttTypography.button,
        "footnote" to ttTypography.footnote,
        "captionEmphasised" to ttTypography.captionEmphasised,
        "caption" to ttTypography.caption,
        "key" to ttTypography.key,
        "captionSmall" to ttTypography.captionSmall,
    )
    ttPreviewSurface {
        Column(
            modifier = Modifier.padding(ttSpacing.xxl),
            verticalArrangement = Arrangement.spacedBy(ttSpacing.xs),
        ) {
            for ((name, style) in scale) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(ttSpacing.md),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    ttText(
                        text = "TT-14",
                        modifier = Modifier.fillMaxWidth(0.3f),
                        style = ttTypography.captionSmall,
                        color = ttLabelSecondary(),
                    )
                    ttText("Aa", style = style, textAlign = TextAlign.Center)
                    ttText(name, style = ttTypography.captionSmall, color = ttLabelSecondary())
                }
            }
        }
    }
}

/**
 * The glass components over the backdrop, which is the combination that matters and the
 * one no isolated component preview shows. Glass over the flat canvas reads as grey; that
 * is the failure this preview exists to make visible, so it draws the backdrop.
 */
@Preview(name = "Glass over the backdrop", showBackground = true, widthDp = 360)
@Composable
private fun GlassOverBackdropPreview() {
    ttPreviewSurface {
        Column(
            modifier = Modifier.padding(ttSpacing.screenGutterLarge),
            verticalArrangement = Arrangement.spacedBy(ttSpacing.lg),
        ) {
            TTGlassSegmentedPicker(
                options = listOf("Board", "Backlog"),
                selection = "Board",
                onSelect = {},
            ) { it }

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .ttGlassCard()
                    .padding(ttSpacing.xxl),
                verticalArrangement = Arrangement.spacedBy(ttSpacing.sm),
            ) {
                ttText("TT-14 Draft the board", style = ttTypography.headline)
                ttText("Sampled from the backdrop", style = ttTypography.footnote)
            }

            Row(horizontalArrangement = Arrangement.spacedBy(ttSpacing.md)) {
                ttGlassButton("Add", onClick = {})
                ttGlassButton("Archive", onClick = {}, style = TTGlassButtonStyle.prominent())
            }
        }
    }
}

/**
 * A preview frame, pinned to one appearance.
 *
 * Defaults to [ThemeScheme.System] so a preview that did not care follows the IDE, and
 * takes an explicit scheme where the whole point is to check both. Every preview pins a
 * scheme *somewhere* rather than leaving it to the developer's machine, which is the point
 * noted at the top of the file.
 */
@Composable
private fun ttPreviewSurface(
    scheme: ThemeScheme = ThemeScheme.System,
    content: @Composable () -> Unit,
) {
    ttTheme(scheme) {
        Box(modifier = Modifier.fillMaxSize().background(ttCanvas())) {
            // The backdrop is drawn for every preview, including the ones that are not about
            // it. A component previewed against a flat canvas is a component previewed
            // against a condition the app never has.
            AmbientBackdrop()
            content()
        }
    }
}
