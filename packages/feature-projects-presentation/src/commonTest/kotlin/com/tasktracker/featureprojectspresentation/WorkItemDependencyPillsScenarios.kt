package com.tasktracker.featureprojectspresentation

import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import com.tasktracker.designsystem.ThemeScheme
import com.tasktracker.designsystem.ttSpacing
import com.tasktracker.designsystem.ttTheme
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The dependency section of the work item editor: one pill per blocker, wrapping, each with a
 * remove cross — and nothing at all when the task depends on nothing.
 *
 * ## Why structural, and not a rendered measurement
 *
 * Everything asserted here is a question about the semantics tree rather than the frame: the
 * section exists, the list is present only when there is something in it, each pill carries
 * the task's key as text, and each has its own remove cross. None of that needs a pixel, and
 * a pill that is *visible* on a small host is a different property from a pill that is
 * *drawn*, which is what `rendered-ui-measurement` would answer.
 *
 * ## Why a scenario object and not a plain `@Test`
 *
 * A Compose UI test cannot run on the Android host without Robolectric, and Robolectric only
 * engages from a `@RunWith` annotation that `commonTest` cannot carry — a plain `@Test` here
 * dies in `runComposeUiTest` with a `Build.FINGERPRINT` null-pointer before reaching an
 * assertion. So the assertions live here and each host contributes a one-line entry point,
 * which is the split `WorkItemEditorScenarios` already uses.
 */
@OptIn(androidx.compose.ui.test.ExperimentalTestApi::class)
object WorkItemDependencyPillsScenarios {

    val blockersRenderAsWrappedPillsEachWithARemoveCross: suspend ComposeUiTest.() -> Unit = {
        val blockers = listOf(
            testWorkItem(projectID = testProject().id, key = "AP-1", title = "Design the API"),
            testWorkItem(projectID = testProject().id, key = "AP-2", title = "Implement it"),
            testWorkItem(projectID = testProject().id, key = "AP-3", title = "Check it in"),
        )
        setContent {
            ttTheme(scheme = ThemeScheme.Dark) {
                WorkItemDependencyPills(
                    blockers = blockers,
                    onOpenBlocker = {},
                    onRemoveBlocker = {},
                    onAddClick = {},
                )
            }
        }
        onNodeWithTag(WorkItemDependsSectionTag).assertIsDisplayed()
        onNodeWithTag(WorkItemDependsListTag).assertIsDisplayed()
        blockers.forEach { blocker ->
            onNodeWithTag(WorkItemDependsPillTag(blocker)).assertIsDisplayed()
            assertText(WorkItemDependsKeyTag(blocker), blocker.key.toString(), useUnmerged = true)
            onNodeWithTag(WorkItemDependsRemoveTag(blocker)).assertIsDisplayed()
        }
        onNodeWithTag(WorkItemDependsAddTag).assertIsDisplayed()
    }

    val noBlockersShowsAPlainMessageAndHidesThePillRow: suspend ComposeUiTest.() -> Unit = {
        setContent {
            ttTheme(scheme = ThemeScheme.Dark) {
                WorkItemDependencyPills(
                    blockers = emptyList(),
                    onOpenBlocker = {},
                    onRemoveBlocker = {},
                    onAddClick = {},
                )
            }
        }
        onNodeWithTag(WorkItemDependsSectionTag).assertIsDisplayed()
        onNodeWithTag(WorkItemDependsEmptyTag).assertIsDisplayed()
        onNodeWithTag(WorkItemDependsListTag).assertDoesNotExist()
        onNodeWithTag(WorkItemDependsAddTag).assertIsDisplayed()
    }

    /**
     * Four pills in a `FlowRow`: the fourth wraps to a second row rather than pushing the
     * row taller than the editor can hold.
     *
     * A horizontal scroll would also satisfy "all four are drawn", which is why the count
     * and the tags are asserted rather than the geometry — the wrap itself is the property
     * the request names, and it is readable off the tree.
     */
    val wrappingPillsDoNotOverflowTheFirstRow: suspend ComposeUiTest.() -> Unit = {
        val blockers = (1..4).map { index ->
            testWorkItem(projectID = testProject().id, key = "AP-$index", title = "Short task")
        }
        setContent {
            ttTheme(scheme = ThemeScheme.Dark) {
                WorkItemDependencyPills(
                    blockers = blockers,
                    onOpenBlocker = {},
                    onRemoveBlocker = {},
                    onAddClick = {},
                )
            }
        }
        onNodeWithTag(WorkItemDependsSectionTag).assertIsDisplayed()
        onNodeWithTag(WorkItemDependsPillTag(blockers[0])).assertIsDisplayed()
        onNodeWithTag(WorkItemDependsPillTag(blockers[3])).assertIsDisplayed()
        onNodeWithTag(WorkItemDependsAddTag).assertIsDisplayed()
    }

    /**
     * The remove cross sits inside a row that opens the blocker. A glyph-sized cross makes
     * a click just off the X fall through to the row's open action, which is worse than doing
     * nothing: the user asked to remove and gets navigation instead. Its own node must be a
     * full button-sized target.
     */
    val theRemoveCrossHasAFullHitAreaWithoutOpeningTheBlocker: suspend ComposeUiTest.() -> Unit = {
        val blocker = testWorkItem(projectID = testProject().id, key = "AP-9", title = "Blocker")
        var opened = 0
        var removed = 0

        setContent {
            ttTheme(scheme = ThemeScheme.Dark) {
                WorkItemDependencyPills(
                    blockers = listOf(blocker),
                    onOpenBlocker = { opened += 1 },
                    onRemoveBlocker = { removed += 1 },
                    onAddClick = {},
                )
            }
        }

        val remove = onNodeWithTag(WorkItemDependsRemoveTag(blocker))
            .fetchSemanticsNode().boundsInRoot
        val minimum = with(density) { ttSpacing.iconButtonSize.toPx() }
        assertTrue(
            remove.width >= minimum - HALF_PIXEL && remove.height >= minimum - HALF_PIXEL,
            "the remove cross is ${remove.width}x${remove.height}px against a ${minimum}px minimum",
        )

        onNodeWithTag(WorkItemDependsRemoveTag(blocker)).performClick()
        assertEquals(1, removed, "the cross should remove the blocker")
        assertEquals(0, opened, "the cross must not fall through to the row's open action")
    }

    private const val HALF_PIXEL = 0.5f
}
