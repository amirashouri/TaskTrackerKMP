package com.tasktracker.designsystem

import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

/**
 * The type scale, named.
 *
 * ## Why this file exists when the original had nothing
 *
 * The original wrote `.font(.system(size: 30, weight: .semibold))` in exactly one place
 * and used Apple's *semantic* styles everywhere else — `largeTitle`, `headline`, `body`,
 * `subheadline`, `footnote`, `caption`, `caption2` — which meant every label in the app
 * scaled with Dynamic Type for free and nobody had to think about it. That is the right
 * instinct and it is preserved here; what was missing was a place to put the sizes, so
 * "semantic" was a convention nobody could check.
 *
 * ## `sp` is what makes this Dynamic Type
 *
 * The correspondence is exact rather than approximate. Compose scales every `sp` by the
 * user's font-size accessibility setting, the same way UIKit scales a text style, and
 * these point values are Apple's own for the default content size category. So at the
 * default setting a label is physically the same size as its iOS counterpart, and at
 * 200% both platforms grow it the same way. A `TextStyle` in bare `dp` would look identical
 * on both platforms *only* until the user changed their text size, at which point the
 * iOS build would grow and this one would not.
 *
 * The one hard-coded size in the original is reproduced faithfully as a **fixed** size in
 * `AppMark`, because that glyph is a picture rather than a label and lives in a fixed
 * 68dp tile; see `Brand.kt` for why it must not scale.
 *
 * ## Weights
 *
 * The original paired most roles with a weight, so each role here carries the weight the
 * app actually used for it rather than leaving the caller to remember. Two roles are
 * available at two weights and both are named, because the picker swaps a segment's weight
 * with its selected state and that swap has to be a named value.
 */
@Immutable
object ttTypography {

    /** 34sp — the app's name, on the splash and on sign-in. Bold in the original. */
    val largeTitle: TextStyle = TextStyle(
        fontSize = 34.sp,
        fontWeight = FontWeight.Bold,
        lineHeight = 41.sp,
    )

    /** 22sp — a screen's own title. Not used by the original; here so a title ramp exists. */
    val title: TextStyle = TextStyle(
        fontSize = 22.sp,
        fontWeight = FontWeight.SemiBold,
        lineHeight = 28.sp,
    )

    /** 20sp — a section heading inside a screen. Not used by the original. */
    val titleSmall: TextStyle = TextStyle(
        fontSize = 20.sp,
        fontWeight = FontWeight.SemiBold,
        lineHeight = 25.sp,
    )

    /** 17sp, semibold — a row's leading label: a board column's name, a project's name. */
    val headline: TextStyle = TextStyle(
        fontSize = 17.sp,
        fontWeight = FontWeight.SemiBold,
        lineHeight = 22.sp,
    )

    /** 17sp — running body text. A backlog row's description, an archived project. */
    val body: TextStyle = TextStyle(
        fontSize = 17.sp,
        fontWeight = FontWeight.Normal,
        lineHeight = 22.sp,
    )

    /** 16sp — body text one step down. Not used by the original. */
    val callout: TextStyle = TextStyle(
        fontSize = 16.sp,
        fontWeight = FontWeight.Normal,
        lineHeight = 21.sp,
    )

    /** 15sp — a supporting label under a title, and a picker segment. */
    val subheadline: TextStyle = TextStyle(
        fontSize = 15.sp,
        fontWeight = FontWeight.Normal,
        lineHeight = 20.sp,
    )

    /** 15sp semibold — the selected state of a picker segment. See [ttTypography]. */
    val subheadlineSelected: TextStyle = subheadline.copy(fontWeight = FontWeight.SemiBold)

    /** 15sp medium — a glass button's label. */
    val button: TextStyle = TextStyle(
        fontSize = 15.sp,
        fontWeight = FontWeight.Medium,
        lineHeight = 20.sp,
    )

    /** 13sp — metadata: a card's due date, a count, a timestamp. */
    val footnote: TextStyle = TextStyle(
        fontSize = 13.sp,
        fontWeight = FontWeight.Normal,
        lineHeight = 18.sp,
    )

    /** 12sp — the dense metadata row, and a badge. */
    val caption: TextStyle = TextStyle(
        fontSize = 12.sp,
        fontWeight = FontWeight.Normal,
        lineHeight = 16.sp,
    )

    /** 12sp semibold — a badge, and a section eyebrow. */
    val captionEmphasised: TextStyle = caption.copy(fontWeight = FontWeight.SemiBold)

    /** 11sp — the densest text in the app, and the base for monospaced keys. */
    val captionSmall: TextStyle = TextStyle(
        fontSize = 11.sp,
        fontWeight = FontWeight.Normal,
        lineHeight = 14.sp,
    )

    /**
     * 11sp, medium, monospaced digits — a project key such as `TT-14`.
     *
     * Monospaced digits rather than a fully monospaced family, because the key's *numbers*
     * are what must not shift as they change (`TT-9` to `TT-10` must not move the row), and
     * monospacing the letters as well would make the key look like code for no gain. This
     * is the original's `.caption2.monospacedDigit()`.
     */
    val key: TextStyle = TextStyle(
        fontSize = 11.sp,
        fontWeight = FontWeight.Medium,
        lineHeight = 14.sp,
        fontFeatureSettings = "tnum",
    )

    /**
     * Every size in the scale, ascending.
     *
     * Exposed so a test can assert the scale is ordered and that no two roles share a size
     * by accident — the two ways a scale rots are a role added out of order and a role
     * copied from its neighbour, and neither is visible at a call site.
     */
    val all: List<TextStyle> = listOf(
        captionSmall,
        key,
        caption,
        captionEmphasised,
        footnote,
        button,
        subheadline,
        subheadlineSelected,
        callout,
        body,
        headline,
        titleSmall,
        title,
        largeTitle,
    )
}

/**
 * This style in a monospaced family, keeping its size and weight.
 *
 * For text whose *width* must not change with its content — a key, a counter, a fixed-width
 * column. A row whose label is a name does not want this: monospacing a name makes the
 * word-shape harder to read, not easier, and the original only used it where the content
 * was a code.
 */
fun TextStyle.ttMonospaced(): TextStyle = copy(fontFamily = FontFamily.Monospace)

/**
 * Every digit the same width, in the original font.
 *
 * The narrower of the two options and the right default for anything numeric that changes
 * while the user watches it — a count that goes from 9 to 10 should not shift the label
 * beside it, and a board column that re-sorts under a tap then reads as a glitch.
 */
fun TextStyle.ttMonospacedDigits(): TextStyle = copy(fontFeatureSettings = "tnum")

/**
 * A label, drawn from the scale.
 *
 * `BasicText`, not `Text`, because `Text` is Material3's and this design system has no
 * Material3 — the same reason there is no `Scaffold`, no `Surface` and no `Card` here.
 *
 * Every argument has a token for a default, which is the point of the function existing:
 * a call site that passes a raw `Color` or a raw size is a call site that has opted out of
 * the design system, and there should be as few of those as possible rather than as many as
 * the API allows. [color] defaults to [ttLabel] rather than to white or to a literal,
 * which is the mistake this file's own comment on [ttLabel] describes.
 */
@Composable
fun ttText(
    text: String,
    modifier: Modifier = Modifier,
    style: TextStyle = ttTypography.body,
    color: Color = ttLabel(),
    textAlign: TextAlign? = null,
    maxLines: Int = Int.MAX_VALUE,
    overflow: TextOverflow = TextOverflow.Clip,
) {
    BasicText(
        text = text,
        modifier = modifier,
        // Alignment goes *into the style*, not into `BasicText`: the composable has no
        // `textAlign` parameter in Compose Multiplatform 1.12.1 (Material3's `Text` does,
        // and this design system has no Material3). Folding it into the style is also what
        // makes it survive a caller that passes their own `style` with an alignment set.
        style = style.copy(
            color = color,
            textAlign = textAlign ?: style.textAlign,
        ),
        maxLines = maxLines,
        // Truncation, unlike alignment, is a real parameter on `BasicText` and is passed
        // straight through. It cannot be folded into the style, because `TextStyle` in
        // Compose Multiplatform 1.12.1 has no `overflow` property at all - the property
        // lives on `ParagraphStyle`, and `BasicText` exposes it directly. A `maxLines`
        // without it clips mid-glyph, so a truncated title ends on a half letter.
        overflow = overflow,
    )
}
