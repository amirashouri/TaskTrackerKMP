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

const val TTGlassBackButtonTag = "design.backButton"

/**
 * Standard back button component.
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
