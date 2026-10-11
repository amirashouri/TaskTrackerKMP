package com.tasktracker.designsystem

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.tooling.preview.Preview
import com.tasktracker.resources.Res
import com.tasktracker.resources.story_points_format
import com.tasktracker.resources.story_points_unestimated
import org.jetbrains.compose.resources.stringResource

/**
 * A story-point estimate: the number, or the absence of one.
 *
 * ## A numeral, not a pill
 *
 * The obvious treatment for a number on a card is a small filled capsule, because that is
 * what badges are for. It is wrong here for two reasons that are both about quantity.
 *
 * **A card is not one thing that can hold one badge.** Fifty pills across a board column is
 * a wall of capsules, and the eye reads a wall of capsules as fifty *statuses* rather than
 * fifty quantities — so the badge grammar is borrowed for a role it does not have. The same
 * cards already carry a priority badge, and two badges competing on one card is how the
 * priority becomes the thing you stop seeing.
 *
 **A quantity should read as a number.** Points are an input to a sum, so they should look
 * like digits and sit in the card's metadata row with the key and the due date, at
 * [ttTypography.caption] — the role the scale already gives to "a count". A pill says
 * "this thing has a state"; a numeral in the metadata line says "this thing is worth five".
 *
 * ## It takes no tint
 *
 * There is deliberately **no** `tint` parameter, and that is the whole of 23.4.2's test. A
 * board card is `ttGlassCard(tint = …)` for its *status*, and a component with a tint
 * parameter invites the call site to pass the card's tint — which would paint every estimate
 * in the colour of the column it happens to be in and turn a quantity into a status again.
 * With no parameter, "does not take the card's tint" is structural rather than a convention
 * somebody has to remember.
 *
 * ## `null` is unestimated, and it is not zero
 *
 * `null` means nobody has said. `0` means somebody said zero, which is a real answer: a
 * one-line chore worth nothing is worth nothing, and rendering it as "unestimated" would
 * throw that decision away. So the two draw differently, and the component owns that rather
 * than leaving each call site to invent a reading — a card and a backlog row disagreeing
 * about what a dash means is the failure this prevents.
 *
 * The unestimated reading is an em dash at [ttTypography.captionSmall] on
 * [ttLabelSecondary], which measures a step below the number in both size and ink. It is
 * *not* `ttBodyLabel` on a surface: this sits on the card's own glass fill, and the numbers
 * `GlassSurfacePixelTest` records for that pairing — **2.88:1** in light, **6.01:1** in dark —
 * are why the numeral itself is [ttLabel] rather than the secondary ink the other card
 * metadata uses. An estimate is a quantity somebody typed, not decoration.
 *
 * ## Monospaced digits
 *
 * [ttTypography.caption] with [ttMonospacedDigits], because the estimate changes while the
 * user watches it on a card they are re-ranking: `9` becoming `10` must not reflow the
 * metadata row beside it. A proportional "10" is a wider "10" and the card's key slides.
 */
@Composable
fun TTStoryPoints(
    points: Int?,
    modifier: Modifier = Modifier,
) {
    if (points == null) {
        ttText(
            text = stringResource(Res.string.story_points_unestimated),
            style = ttStoryPointsStyle(null),
            color = ttLabelSecondary(),
            maxLines = 1,
            modifier = modifier.testTag(TTStoryPointsTag),
        )
    } else {
        ttText(
            text = stringResource(Res.string.story_points_format, points),
            style = ttStoryPointsStyle(points),
            color = ttLabel(),
            maxLines = 1,
            modifier = modifier.testTag(TTStoryPointsTag),
        )
    }
}

/**
 * The type role for a story-point reading, as a pure function of whether there is one.
 *
 * Extracted from the composable for one reason: 23.4.2's promise is *"a numeral in
 * [ttTypography.caption]"*, and that is a claim about a `TextStyle`, which is a value. A
 * claim about a value can be asserted directly; the same claim made through a rendered frame
 * would have to be re-derived by looking at glyph heights, which is a worse test of a
 * smaller thing.
 */
internal fun ttStoryPointsStyle(points: Int?): androidx.compose.ui.text.TextStyle =
    if (points == null) ttTypography.captionSmall
    else ttTypography.caption.ttMonospacedDigits()

/** The story-point estimate's own tag, for a test that means *this* number and not the card's. */
const val TTStoryPointsTag = "design.storyPoints"

@Preview(name = "Story points, every state", showBackground = true, widthDp = 360)
@Composable
private fun TTStoryPointsPreview() {
    ttTheme(ThemeScheme.System) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(ttCanvas()),
        ) {
            AmbientBackdrop()
            Column(
                modifier = Modifier.padding(ttSpacing.screenGutterLarge),
                verticalArrangement = Arrangement.spacedBy(ttSpacing.lg),
            ) {
                // On a glass card, because that is where the component actually goes, and a
                // preview against a flat canvas would hide the fill it has to survive.
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .ttGlassCard()
                        .padding(ttSpacing.xxl),
                    verticalArrangement = Arrangement.spacedBy(ttSpacing.sm),
                ) {
                    ttText("TT-14 Draft the migration", style = ttTypography.headline, color = ttLabel())
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(ttSpacing.md),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        ttText("TT-14", style = ttTypography.key, color = ttLabelSecondary())
                        TTStoryPoints(points = 5)
                        TTStoryPoints(points = 10)
                        TTStoryPoints(points = null)
                    }
                }
            }
        }
    }
}