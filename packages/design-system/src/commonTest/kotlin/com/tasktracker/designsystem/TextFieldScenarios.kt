package com.tasktracker.designsystem

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.click
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.dp
import kotlin.test.assertTrue

/**
 * What counts as a tap on [ttTextField].
 *
 * ## Why this is a scenario rather than a reading
 *
 * The field's pill is 48dp tall and the single line of text inside it is about 20dp, centred.
 * That leaves a band above and below the text — most of the control's height — where the only
 * thing under the finger was glass: a tap there did nothing, and a user aiming at a form field
 * had to hit the one strip of it that happened to hold text. Geometry like that cannot be
 * reviewed by reading the composable; it has to be tapped, at a point the test chooses.
 *
 * The point is chosen in *pixels from the pill's top edge*, not at its centre, because the
 * centre is exactly the case that always worked.
 *
 * ## The requester
 *
 * The second scenario covers the other half of the same complaint: a label above the pill is
 * part of the field as far as a finger is concerned, so [ttTextField] takes a [FocusRequester]
 * a caller can hand to whatever draws that label. Nothing here asserts *which* pixel the label
 * paints on — only that asking the field to take focus from outside it works.
 */
@OptIn(ExperimentalTestApi::class)
object TextFieldScenarios {

    /** The pill, which is the control a finger aims at. */
    private const val PillTag = "ttTextField.pill"

    /** The editable node inside it. */
    private const val FieldTag = "ttTextField.field"

    /** A caller-drawn label, standing in for the field labels in the settings forms. */
    private const val LabelTag = "ttTextField.label"

    /**
     * A tap on the pill's own padding focuses the field.
     *
     * The point is 3dp in from the pill's left edge at its vertical centre: inside the glass,
     * outside the editable node's own bounds, and therefore a tap the bare `BasicTextField`
     * could not receive. The *vertical* band above and below the text is not this scenario —
     * foundation already expands a text field's touch target to 48dp, so a tap there focused
     * the field before the pill was made tappable, and a test that aimed there would pass
     * against the defect.
     */
    val aTapOnThePillsPaddingFocusesTheField: suspend ComposeUiTest.() -> Unit = {
        var focused = false
        setContent {
            ttTheme(ThemeScheme.Dark) {
                Box(modifier = Modifier.width(ScenarioWidth)) {
                    ttTextField(
                        value = "",
                        onValueChange = {},
                        hint = "the folder the command is run from",
                        modifier = Modifier.testTag(PillTag),
                        fieldModifier = Modifier.testTag(FieldTag),
                        onFocusChanged = { focused = it },
                    )
                }
            }
        }

        onNodeWithTag(PillTag).performTouchInput {
            click(Offset(3.dp.toPx(), centerY))
        }
        waitForIdle()

        assertTrue(
            focused,
            "a tap 3dp in from the pill's left edge left the field unfocused, so the pill's " +
                "own inset is not part of the control a finger can hit",
        )
    }

    /**
     * A tap on a label that holds the field's [FocusRequester] focuses the field.
     *
     * This is the seam the settings forms use: the label is the caller's, so the field cannot
     * reach it on its own — but a caller that has the requester can make the label a way in.
     */
    val aLabelHoldingTheRequesterFocusesTheField: suspend ComposeUiTest.() -> Unit = {
        var focused = false
        setContent {
            ttTheme(ThemeScheme.Dark) {
                val requester = remember { FocusRequester() }
                Column(modifier = Modifier.width(ScenarioWidth)) {
                    Box(
                        modifier = Modifier
                            .clickable { requester.requestFocus() }
                            .testTag(LabelTag),
                    ) {
                        ttText(
                            text = "Repository folder on that machine",
                            style = ttTypography.captionEmphasised,
                            color = ttLabelSecondary(),
                        )
                    }
                    ttTextField(
                        value = "",
                        onValueChange = {},
                        hint = "the folder the command is run from",
                        modifier = Modifier.testTag(PillTag),
                        fieldModifier = Modifier.testTag(FieldTag),
                        onFocusChanged = { focused = it },
                        focusRequester = requester,
                    )
                }
            }
        }

        onNodeWithTag(LabelTag).performClick()
        waitForIdle()

        assertTrue(
            focused,
            "the label handed its tap to the field's requester and the field ignored it, so " +
                "the label above every form field stays inert",
        )
    }

    /** A narrow phone column, so the pill is the width it would be on a device. */
    private val ScenarioWidth = 320.dp
}
