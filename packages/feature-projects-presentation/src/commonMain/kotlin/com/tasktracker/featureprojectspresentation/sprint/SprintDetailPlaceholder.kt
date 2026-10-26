package com.tasktracker.featureprojectspresentation

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextAlign
import com.tasktracker.designsystem.ttBodyLabel
import com.tasktracker.designsystem.ttLabel
import com.tasktracker.designsystem.ttSpacing
import com.tasktracker.designsystem.ttText
import com.tasktracker.designsystem.ttTypography
import com.tasktracker.resources.Res
import com.tasktracker.resources.sprint_detail_placeholder_body
import com.tasktracker.resources.sprint_detail_placeholder_title
import org.jetbrains.compose.resources.stringResource

/**
 * Placeholder displayed in the wide-window secondary pane when no sprint is selected.
 */
@Composable
fun SprintDetailPlaceholder(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            modifier = Modifier
                .padding(ttSpacing.xxl)
                .testTag(SprintDetailPlaceholderTag),
            verticalArrangement = Arrangement.spacedBy(ttSpacing.sm),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            ttText(
                text = stringResource(Res.string.sprint_detail_placeholder_title),
                style = ttTypography.titleSmall,
                color = ttLabel(),
                textAlign = TextAlign.Center,
            )
            // Uses ttBodyLabel() to satisfy WCAG contrast on light canvas
            ttText(
                text = stringResource(Res.string.sprint_detail_placeholder_body),
                style = ttTypography.subheadline,
                color = ttBodyLabel(),
                textAlign = TextAlign.Center,
            )
        }
    }
}

const val SprintDetailPlaceholderTag = "sprints.detail.placeholder"