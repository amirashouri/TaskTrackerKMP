package com.tasktracker.designsystem

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.tooling.preview.Preview
import com.tasktracker.resources.Res
import com.tasktracker.resources.common_back
import org.jetbrains.compose.resources.stringResource

/**
 * The one way back, for every screen that can be left.
 *
 * ## Why this is a component and not a call to [ttGlassIconButton]
 *
 * "Go back" is a control the app draws, not a gesture the platform grants. Two hosts have a
 * system back — Android's button and iOS's edge swipe — and two do not: on the desktop window
 * there is no gesture and no button at all, and the shell's navigation rail cannot stand in for
 * one (re-selecting the active tab is a no-op, and switching tabs deliberately keeps each tab's
 * stack). A screen reachable only by a push is therefore unreachable-from on the desktop unless it
 * draws its own way out, which is what this is.
 *
 * So every screen that can be pushed renders this in its leading slot, and the fact that it is one
 * composable rather than an `ttGlassIconButton` with a chevron at each call site is the point: the
 * icon, the touch target and the accessible label are decided once. A second screen that wrote its
 * own back control would be a second place for the chevron to face the wrong way, and the module's
 * `SprintSummaryScreen` already shows the drift this prevents — it passes `common_cancel` as the
 * label of a *back* arrow, because there was no shared name for the action until now.
 *
 * ## Why the circle, and why it is the icon button's
 *
 * The round glass target is `ttGlassIconButton`'s own shape ([ttGlassSurface] with `CircleShape` at
 * the hairline width) rather than a second circle described here, so the back control and the
 * screen's other header action are the same object with the same ink, blur and rim. A back button
 * that measured differently from the `+` beside it would read as a different kind of thing.
 *
 * ## [TTGlassBackButtonTag] is applied here, not by the caller
 *
 * A tag passed in by each screen would be a name per screen for one control, and a test that
 * wanted "press back" would have to know which screen it was on. One tag means one answer — the
 * precedent is [TTConfirmPopupTag], which the confirmation shares across every screen that raises
 * it. A caller's own `testTag` is overwritten, deliberately: two names for one node is the second
 * answer this file exists to remove.
 */
@Composable
fun TTGlassBackButton(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    contentDescription: String = stringResource(Res.string.common_back),
) {
    ttGlassIconButton(
        image = ttActionIcons.chevronLeft,
        contentDescription = contentDescription,
        onClick = onBack,
        modifier = modifier.testTag(TTGlassBackButtonTag),
    )
}

/** The back control's tag, shared by every screen that renders one. */
const val TTGlassBackButtonTag = "design.backButton"

@Preview(name = "Back button", showBackground = true, widthDp = 200)
@Composable
private fun GlassBackButtonPreview() {
    ttPreviewSurface(ThemeScheme.Light) {
        Column(
            modifier = Modifier.padding(ttSpacing.screenGutterLarge),
            verticalArrangement = Arrangement.spacedBy(ttSpacing.md),
        ) {
            TTGlassBackButton(onBack = {})
        }
    }
}
