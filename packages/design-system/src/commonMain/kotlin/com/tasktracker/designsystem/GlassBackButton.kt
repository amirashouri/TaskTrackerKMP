package com.tasktracker.designsystem

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
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
