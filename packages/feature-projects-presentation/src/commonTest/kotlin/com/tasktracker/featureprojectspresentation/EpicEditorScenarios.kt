package com.tasktracker.featureprojectspresentation

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.requiredWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.tasktracker.core.TestDateProvider
import com.tasktracker.designsystem.ThemeScheme
import com.tasktracker.designsystem.ttTheme
import kotlin.test.assertEquals

/**
 * The Epic editor's page layout.
 *
 * The editor is a single reading column rather than a dashboard, so a desktop window must not
 * stretch it edge to edge. It uses the Backlog's width: the form fills a phone, stops at
 * [BacklogMaxContentWidth] on a wide host, and sits centred in the window.
 */
@OptIn(ExperimentalTestApi::class)
object EpicEditorScenarios {

    /**
     * On a desktop-wide window the Epic editor is capped at the Backlog's reading width and
     * centred, and a phone-width window still fills it.
     *
     * The screen tag is on the capped column itself, so its width is the reading width; the
     * separate frame tag makes the centring check independent of the test host's own window,
     * which can be narrower than the forced width. A cap that stopped short of 960 would pass
     * a merely "less than the window" assertion, so the width is asserted exactly.
     */
    val wideLayoutClampsContentWidthAndCenters: suspend ComposeUiTest.() -> Unit = {
        val width = mutableStateOf(DESKTOP_WIDTH.dp)
        setContent {
            Box(
                modifier = Modifier
                    .requiredWidth(width.value)
                    .testTag(EpicEditorFrameTag),
            ) {
                epicEditor()
            }
        }
        waitForIdle()

        val frame = onNodeWithTag(EpicEditorFrameTag).fetchSemanticsNode().boundsInRoot
        val screen = onNodeWithTag(EpicEditorScreenTag).fetchSemanticsNode()
        assertEquals(
            expected = densityPixels(BacklogMaxContentWidth),
            actual = screen.size.width.toFloat(),
            absoluteTolerance = 2f,
            message = "the Epic editor is ${screen.size.width}px wide where the Backlog's " +
                "${BacklogMaxContentWidth} reading width is ${densityPixels(BacklogMaxContentWidth)}px",
        )
        assertEquals(
            expected = frame.left + (frame.width - screen.size.width) / 2f,
            actual = screen.boundsInRoot.left,
            absoluteTolerance = 2f,
            message = "the Epic editor is not centred in the screen: " +
                "frame=$frame editor=${screen.boundsInRoot}",
        )

        runOnIdle { width.value = PHONE_WIDTH.dp }
        waitForIdle()

        val phoneFrame = onNodeWithTag(EpicEditorFrameTag).fetchSemanticsNode().boundsInRoot
        val phoneScreen = onNodeWithTag(EpicEditorScreenTag).fetchSemanticsNode()
        assertEquals(
            expected = densityPixels(PHONE_WIDTH.dp),
            actual = phoneScreen.size.width.toFloat(),
            absoluteTolerance = 2f,
            message = "the Epic editor is ${phoneScreen.size.width}px wide on a " +
                "${PHONE_WIDTH}dp window, so the desktop cap leaked into the phone layout",
        )
        assertEquals(
            expected = phoneFrame.left + (phoneFrame.width - phoneScreen.size.width) / 2f,
            actual = phoneScreen.boundsInRoot.left,
            absoluteTolerance = 2f,
            message = "the Epic editor is not centred on a phone-width window",
        )
    }

    @Composable
    private fun epicEditor() {
        val model = remember {
            EpicEditorModel(
                existing = null,
                repository = FakeProjectRepository(),
                dateProvider = TestDateProvider(),
            )
        }
        ttTheme(ThemeScheme.Light) {
            EpicEditorScreen(
                model = model,
                onBack = {},
                onSave = {},
            )
        }
    }
}

private const val EpicEditorFrameTag = "projects.epic.editor.frame"
private const val DESKTOP_WIDTH = 1400
private const val PHONE_WIDTH = 393

@OptIn(ExperimentalTestApi::class)
private fun ComposeUiTest.densityPixels(dp: Dp): Float = with(density) { dp.toPx() }
