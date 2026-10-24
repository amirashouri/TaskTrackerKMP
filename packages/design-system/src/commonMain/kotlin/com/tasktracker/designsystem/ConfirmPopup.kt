package com.tasktracker.designsystem

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.background
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import com.tasktracker.resources.Res
import com.tasktracker.resources.common_cancel
import org.jetbrains.compose.resources.stringResource

// ---------------------------------------------------------------------------------------------
// Test tags
// ---------------------------------------------------------------------------------------------

/** The popup's own surface. A single constant, so the tag is a property of the component. */
const val TTConfirmPopupTag = "design.confirmPopup"

/** The cancel button, so a test can dismiss without reaching for the text. */
const val TTConfirmPopupCancelTag = "design.confirmPopup.cancel"

/** The confirm button, so a test can accept without reaching for the text. */
const val TTConfirmPopupConfirmTag = "design.confirmPopup.confirm"

/**
 * A small confirmation popup drawn on glass, with Cancel and Confirm buttons.
 *
 * ## Why it is a design-system component rather than a feature inline
 *
 * This app has three inline confirmation surfaces that all spell the same shape:
 * `RemoveProjectConfirmation` in `:feature-projects-presentation`,
 * `SprintActionConfirmation` in `:feature-projects-presentation`, and the date-field
 * footer. Two of them sit on `ttGlassSurface` with no `base`, which means the fill ramp's
 * shipped alpha is `FILL_ALPHA * FILL_ALPHA` = 0.3025 (NOTE: [ttGlass.FILL_ALPHA] is deprecated
 * and now equals [ttGlass.GlassThickness.STANDARD.baseAlpha]) and the text is read through a third
 * of an accent wash. That is the defect this popup exists to fix: a confirmation the user
 * cannot read is a confirmation they have already accepted by accident.
 *
 * A design-system popup gets the opaque base every readable control in this app uses —
 * `ttSurface()` at `1f - ttGlassLensAlpha()` — so the label and the secondary body sit on
 * the same near-opaque field colour as the rest of the surface and the contrast numbers are
 * the ones `Typography.kt` already documents. The shape, radius, spacing and typography are
 * the tokens, not literals, so one edit to any of them changes every popup that uses this.
 *
 * ## The two buttons, and why they are not symmetric
 *
 * Cancel is always the resting glass button. Confirm carries the burden of saying what will
 * happen, and can be either the resting glass style or a prominent tinted one. The tint is
 * optional on purpose: a destructive confirm is red and prominent, a neutral confirm is
 * accent and prominent, and a "got it" confirm can be plain glass. The caller chooses, and
 * this popup does not decide which outcome is which — that is the caller's job and the reason
 * the body text is a parameter here rather than a when-branch on an enum.
 */
@Composable
fun ConfirmPopup(
    title: String,
    body: String,
    cancelLabel: String = stringResource(Res.string.common_cancel),
    confirmLabel: String,
    onCancel: () -> Unit,
    onConfirm: () -> Unit,
    modifier: Modifier = Modifier,
    confirmTint: Color? = null,
    confirmStyle: TTGlassButtonStyle = TTGlassButtonStyle.prominent(confirmTint),
    tag: String = TTConfirmPopupTag,
) {
    val shape = RoundedCornerShape(ttRadius.surface)

    Box(
        modifier = modifier
            .fillMaxSize()
            // Block touches from passing through to underlying content
            .background(Color.Transparent, RoundedCornerShape(0.dp))
            .pointerInput(Unit) {
                // Consume all pointer events to prevent click-through
            },
        contentAlignment = Alignment.Center,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .ttGlassSurface(
                    shape = shape,
                    base = ttSurface().copy(alpha = 1f - ttGlassLensAlpha()),
                )
                .padding(ttSpacing.formStack)
                .testTag(tag),
            verticalArrangement = Arrangement.spacedBy(ttSpacing.lg),
        ) {
            ttText(
                text = title,
                style = ttTypography.titleSmall,
                color = ttLabel(),
            )
            ttText(
                text = body,
                style = ttTypography.callout,
                color = ttLabelSecondary(),
                textAlign = TextAlign.Start,
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(ttSpacing.sm, Alignment.End),
            ) {
                ttGlassButton(
                    label = cancelLabel,
                    onClick = onCancel,
                    modifier = Modifier.testTag(TTConfirmPopupCancelTag),
                )
                ttGlassButton(
                    label = confirmLabel,
                    onClick = onConfirm,
                    modifier = Modifier.testTag(TTConfirmPopupConfirmTag),
                    style = confirmStyle,
                )
            }
        }
    }
}
