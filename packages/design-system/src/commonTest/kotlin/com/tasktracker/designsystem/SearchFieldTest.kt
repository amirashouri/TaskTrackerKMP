package com.tasktracker.designsystem

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.runComposeUiTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The shared search field's structure: what it draws, where its tag sits, and what it reports.
 *
 * ## What this asserts, and what it deliberately does not
 *
 * These are semantics-tree claims — the hint exists, the clear control exists *only* with a
 * value, typing reaches the editable node, the pill is the control's real height. The glyph's
 * silhouette is a *pixel* claim and lives in [TabIconsRenderTest], because a magnifier that
 * draws nothing passes every assertion below: the node is there, the path data is there, and the
 * ink is not.
 *
 * ## Why every scenario types rather than reading a value
 *
 * The field is controlled, so its text is whatever the caller passed. A test that rendered
 * `value = "backlog"` and found "backlog" would pass against a field that ignored
 * `onValueChange` entirely. Typing and then asserting the *clear control appeared* proves the
 * whole loop in one step: the edit reached `onValueChange`, the caller's new value came back,
 * and the field re-rendered from it.
 *
 * ## The height assertion
 *
 * `ttTextField` documents a defect the field copies had: padding applied *before* the surface,
 * so a 48dp control drew a ~36dp pill inside a 48dp box. The same mistake is one modifier swap
 * away here, and it is invisible to every other test — the field is still tappable, still the
 * right width, and simply looks smaller than the row it sits in. Measured on the pill, which is
 * the surface.
 */
@OptIn(ExperimentalTestApi::class)
class SearchFieldTest {

    @Test
    fun `an empty field shows the hint and no clear control`() = searchFieldTest {
        fieldShowing(value = "")

        onNodeWithTag(TTSearchFieldHintTag, useUnmergedTree = true).assertIsDisplayed()
        onNodeWithTag(TTSearchFieldClearTag).assertDoesNotExist()
    }

    /**
     * Typing reaches the editable node, and the hint and clear control both follow the value.
     *
     * Three claims in one composition, which is why it is one test: a field that kept its own
     * copy of the text would show the hint gone and the cross present while the caller still
     * held `""` — and that field is the one this test exists to fail.
     */
    @Test
    fun `typing reaches the field and reveals the clear control`() = searchFieldTest {
        fieldShowing(value = "")

        onNodeWithTag(SEARCH_FIELD_TEST_TAG).performTextInput("backlog")
        waitForIdle()

        onNodeWithTag(TTSearchFieldHintTag, useUnmergedTree = true).assertDoesNotExist()
        onNodeWithTag(TTSearchFieldClearTag).assertIsDisplayed()
    }

    /** The clear control is the only thing that empties the field, and it reports `""`. */
    @Test
    fun `the clear control empties the field`() = searchFieldTest {
        fieldShowing(value = "backlog")

        onNodeWithTag(TTSearchFieldClearTag).performClick()
        waitForIdle()

        onNodeWithTag(TTSearchFieldHintTag, useUnmergedTree = true).assertIsDisplayed()
        onNodeWithTag(TTSearchFieldClearTag).assertDoesNotExist()
    }

    /**
     * `modifier` is the pill and `fieldModifier` is the field, and they are two nodes.
     *
     * The text input above is the routing half — a tag moved onto the pill would have no
     * `SetText` action and would fail there rather than here. This is the other half: the pill
     * exists as its own node, so a caller can size or tag the control around the field without
     * displacing the field's own tag.
     */
    @Test
    fun `the pill and the field are two nodes`() = searchFieldTest {
        fieldShowing(value = "")

        onNodeWithTag(PILL_TEST_TAG).assertIsDisplayed()
        onNodeWithTag(SEARCH_FIELD_TEST_TAG).assertIsDisplayed()
    }

    /**
     * The pill is the control's real height, not a surface drawn inside a padded box.
     *
     * Held against [ttSpacing.fieldMinHeight] and the host's own density rather than a pixel
     * count, so it means the same thing on a JVM host at 1.0 and an Android host at 3.0.
     */
    @Test
    fun `the pill is at least the field's minimum height`() = searchFieldTest {
        fieldShowing(value = "")

        val pill = onNodeWithTag(PILL_TEST_TAG).fetchSemanticsNode().boundsInRoot
        val minimum = with(density) { ttSpacing.fieldMinHeight.toPx() }

        assertTrue(
            pill.height >= minimum - HALF_PIXEL,
            "the surface is ${pill.height}px tall against a ${minimum}px minimum, so the " +
                "padding went on before the glass rather than after it",
        )
    }

    /** A disabled field says so, which is what makes it ignore input rather than look inert. */
    @Test
    fun `a disabled field is not enabled`() = searchFieldTest {
        fieldShowing(value = "backlog", enabled = false)

        onNodeWithTag(SEARCH_FIELD_TEST_TAG).assertIsNotEnabled()
    }

    /**
     * Focus is reported to the caller, because a screen uses it to dismiss its own chrome.
     *
     * The ring and the wash are the same tokens [ttTextField] uses and are a pixel claim; what
     * is assertable here is that the transition reaches `onFocusChanged` at all, which is the
     * part a caller can act on.
     */
    @Test
    fun `focusing the field reports it`() = searchFieldTest {
        var focused: Boolean? = null
        setContent {
            ttTheme(ThemeScheme.Light) {
                var value by remember { mutableStateOf("") }
                ttSearchField(
                    value = value,
                    onValueChange = { value = it },
                    hint = "Search tasks",
                    modifier = Modifier.testTag(PILL_TEST_TAG),
                    fieldModifier = Modifier.testTag(SEARCH_FIELD_TEST_TAG),
                    onFocusChanged = { focused = it },
                )
            }
        }

        onNodeWithTag(SEARCH_FIELD_TEST_TAG).performClick()
        waitForIdle()

        assertEquals(true, focused, "a focused field should have reported focus")
    }

    /**
     * One field, in the appearance the app ships, tagged for both of its nodes.
     *
     * The value is composition state rather than a captured Kotlin `var`: a field rendered from
     * a captured value would never change, so the typing scenario above would assert nothing.
     */
    private fun ComposeUiTest.fieldShowing(
        value: String,
        enabled: Boolean = true,
    ) {
        setContent {
            ttTheme(ThemeScheme.Light) {
                var text by remember { mutableStateOf(value) }
                ttSearchField(
                    value = text,
                    onValueChange = { text = it },
                    hint = "Search tasks",
                    modifier = Modifier.testTag(PILL_TEST_TAG),
                    fieldModifier = Modifier.testTag(SEARCH_FIELD_TEST_TAG),
                    enabled = enabled,
                )
            }
        }
    }

    /**
     * A composition on a host that can host one.
     *
     * The assumption comes before `runComposeUiTest`, not inside it: the Android host's stub
     * `android.jar` kills `runComposeUiTest` in its own setup, before a test body runs, and
     * `commonTest` reaches that target without a Robolectric runner. Asking first records a
     * skip there rather than seven failures that say nothing about this field — the same
     * mechanism [rasterising] and `DateFieldInteractionTest` use, for the same reason.
     */
    private fun searchFieldTest(body: ComposeUiTest.() -> Unit) {
        assumeHostRasterisesText()
        runComposeUiTest(block = body)
    }

    private companion object {
        /** The tag on the text field, passed through `fieldModifier` by every scenario. */
        const val SEARCH_FIELD_TEST_TAG = "search.field.test"

        /** The tag on the pill, passed through `modifier`. */
        const val PILL_TEST_TAG = "search.pill.test"

        /** Layout works in whole pixels; a comparison in floats must not report touching edges. */
        const val HALF_PIXEL = 0.5f
    }
}
