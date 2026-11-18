package com.tasktracker.featureprojectspresentation

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.tasktracker.designsystem.AppMarkCheck
import com.tasktracker.designsystem.ttAccent
import com.tasktracker.designsystem.ttOnAccent
import com.tasktracker.resources.Res
import com.tasktracker.resources.sprint_summary_hit
import org.jetbrains.compose.resources.stringResource

/**
 * Solid accent disc with the app mark's checkmark, marking a sprint that reached its point goal.
 *
 * A glyph rather than a pill of words, because the badge is a *state*, not a sentence: the
 * sprint's card and its summary already say how many points were committed and achieved, so
 * the only thing left to add is the verdict, and a check is the shortest way to say it.
 *
 * ## The check is the mark's own drawing
 *
 * [AppMarkCheck] shares `drawAppMarkCheck` with [com.tasktracker.designsystem.AppMark], so the
 * badge carries the same two round-capped segments — same cap shape, same round joint, same
 * stroke-to-width ratio — as the tile on the splash and sign-in screens, scaled up to fill its
 * box. A redrawn "similar" check was the first attempt and is exactly the kind of second
 * drawing that drifts: round caps were what made it read as the app's mark rather than as a
 * generic tick.
 *
 * ## Why the fill is opaque accent and not an accent wash
 *
 * The first version was the empty state's recipe — accent at 12% over the card — and it was
 * tried at this size and read as a faint smudge: at 28dp the wash is one hairline of colour
 * around a glyph, so the accent was effectively invisible. An opaque accent fill is the one
 * fill in this app that carries ink, and [ttOnAccent] is the pairing contrast-tested against it
 * in every palette — the same pairing as the mark's own tile, the date field's selected day and
 * the prominent button.
 *
 * The description is set on the disc rather than on the glyph, so a reader is told "goal met"
 * once: on a sprint card the check is the only thing stating the verdict, and a screen reader
 * that skipped it would read the point totals and leave the user to do the comparison. Callers
 * decide *whether* to show the badge from [com.tasktracker.domain.SprintSummary.didHitGoal]
 * (or [SprintListRow.didHitGoal]); this never compares the two totals itself.
 */
@Composable
fun SprintCompleteBadge(modifier: Modifier = Modifier) {
    val description = stringResource(Res.string.sprint_summary_hit)
    Box(
        modifier = modifier
            .size(SPRINT_COMPLETE_BADGE_SIZE)
            .clip(CircleShape)
            .background(ttAccent())
            // A Canvas has no semantics of its own, so the verdict is described here. Not
            // `clearAndSetSemantics`: the disc has nothing under it to clear, and clearing
            // would also drop the test tag below.
            .semantics { contentDescription = description }
            .testTag(SprintCompleteBadgeTag),
        contentAlignment = Alignment.Center,
    ) {
        AppMarkCheck(
            tint = ttOnAccent,
            modifier = Modifier.size(SPRINT_COMPLETE_GLYPH_SIZE),
        )
    }
}

const val SprintCompleteBadgeTag = "sprints.completeBadge"

/**
 * 28dp — a peer of the two-line sprint title it sits beside on the card's title row, not a
 * subscript to it. At 18dp the check was legible only if you already knew it was there.
 */
private val SPRINT_COMPLETE_BADGE_SIZE = 28.dp

/**
 * 18dp for the check inside the disc, which leaves a 5dp ring.
 *
 * The check now fills its box (see [AppMarkCheck]), so this is the mark's full width rather
 * than a fraction of a glyph box: 18dp of ink at the mark's own stroke-to-width ratio is a
 * visibly chunky tick, and 5dp of accent around it keeps the disc reading as a badge.
 */
private val SPRINT_COMPLETE_GLYPH_SIZE = 18.dp
