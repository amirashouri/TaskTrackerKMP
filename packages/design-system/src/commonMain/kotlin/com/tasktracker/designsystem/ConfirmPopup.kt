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

const val TTConfirmPopupTag = "design.confirmPopup"
const val TTConfirmPopupCancelTag = "design.confirmPopup.cancel"
const val TTConfirmPopupConfirmTag = "design.confirmPopup.confirm"

/**
 * Confirmation popup drawn on glass with Cancel and Confirm actions.
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
            .background(Color.Transparent, RoundedCornerShape(0.dp))
            .pointerInput(Unit) {},
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
