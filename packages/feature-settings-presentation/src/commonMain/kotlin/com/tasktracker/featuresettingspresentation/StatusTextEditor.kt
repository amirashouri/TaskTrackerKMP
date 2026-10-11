package com.tasktracker.featuresettingspresentation

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import com.tasktracker.designsystem.ttAccent
import com.tasktracker.designsystem.ttGlassSurface
import com.tasktracker.designsystem.ttLabel
import com.tasktracker.designsystem.ttLabelSecondary
import com.tasktracker.designsystem.ttRadius
import com.tasktracker.designsystem.ttSpacing
import com.tasktracker.designsystem.ttText
import com.tasktracker.designsystem.ttTypography

const val SettingsStatusTextRowTag = "settings.statusText"

@Composable
internal fun StatusTextEditor(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    // No label: the hint inside the field already says what this is,
    // and a label beside it just squeezed the field into half the row.
    Box(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .padding(vertical = ttSpacing.sm)
            .ttGlassSurface(shape = RoundedCornerShape(ttRadius.row)),
        // A `Box` defaults to `TopStart`, which pinned the single-line field to the top of
        // the 36dp content area and left the text riding high in the pill. Centring it
        // vertically is the whole fix; the field is `fillMaxWidth`, so the horizontal
        // placement is unchanged.
        contentAlignment = Alignment.Center,
    ) {
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            modifier = Modifier.fillMaxWidth(),
            textStyle = ttTypography.body.copy(color = ttLabel()),
            cursorBrush = SolidColor(ttAccent()),
            singleLine = true,
            maxLines = 1,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onAny = {}),
            decorationBox = { inner ->
                Box(
                    modifier = Modifier.padding(
                        horizontal = ttSpacing.sm,
                        vertical = ttSpacing.xs,
                    ),
                ) {
                    if (value.isEmpty()) {
                        ttText(
                            text = "E.g. on vacation, sick — why are you away?",
                            style = ttTypography.body,
                            color = ttLabelSecondary(),
                        )
                    }
                    inner()
                }
            },
        )
    }
}
