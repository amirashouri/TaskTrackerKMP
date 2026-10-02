package com.tasktracker.featureprojectspresentation

import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * How a project screen's scenarios address what they are asserting on.
 *
 * ## Why this file exists
 *
 * The first run of these scenarios failed six times, and all six were the test's fault
 * rather than the screens'. Two habits were doing it, and both are easy to get wrong again
 * while writing the next scenario, so they are pinned down here once.
 *
 * ### 1. A clickable card merges everything inside it
 *
 * `WorkItemCard`'s root is clickable, and clickable means `mergeDescendants = true` for the
 * whole subtree. The card's own tag is on that merged node and is found normally, but the
 * *assignee*, *due date* and *priority badge* tags are inside it and exist **only in the
 * unmerged tree** - so `onNodeWithTag(WorkItemCardAssigneeTag)` finds nothing and the failure
 * reads `could not find any node`, with the hint `Are you missing useUnmergedNode = true?`
 * printed underneath.
 *
 * That is not a wart to work around, it is the merge doing its job: a screen reader should
 * hear one card, not a card and three of its innards. [onNested] is the honest way to reach
 * a specific element inside a merged node.
 *
 * ### 2. A screen has more than one copy of most text
 *
 * `onNodeWithText` requires **exactly one** match, and a real board breaks that constantly:
 * "Nothing here yet" appears once per empty lane, a count of `0` once per empty lane, and a
 * project row's "Delete" is the same word as the confirmation's. The failures said
 * `Expected exactly '1' node but found '4' nodes` and named the duplicates, which is the test
 * being precise for once - the fix is to stop searching for text and to *address the node by
 * tag, then read its text*.
 *
 * [assertText] is that habit: it fails if the wording changes, and it cannot silently start
 * matching a different element. Where a scenario genuinely means "all of them" - every lane
 * drawn, even empty - [countOfTag] is the tool, because a count is unambiguous where a string
 * is not.
 */
/**
 * The node with [tag], searched in the unmerged tree.
 *
 * Use for an element *inside* something clickable. Use plain `onNodeWithTag` for a top-level
 * element, which is found as-is and does not need this.
 */
@OptIn(ExperimentalTestApi::class)
internal fun ComposeUiTest.onNested(tag: String): SemanticsNodeInteraction =
    onNodeWithTag(tag, useUnmergedTree = true)

/** The text of the node carrying [tag], or `""` if it has none. */
@OptIn(ExperimentalTestApi::class)
internal fun ComposeUiTest.textOf(tag: String, useUnmerged: Boolean = false): String =
    onNodeWithTag(tag, useUnmergedTree = useUnmerged)
        .fetchSemanticsNode()
        .config[SemanticsProperties.Text]
        ?.joinToString(separator = "") { it.text }
        .orEmpty()

/**
 * Asserts the node carrying [tag] says exactly [expected].
 *
 * Reads the tagged node rather than searching for the string, so a wording change fails
 * loudly and a second element with the same words cannot satisfy the assertion by accident.
 */
@OptIn(ExperimentalTestApi::class)
internal fun ComposeUiTest.assertText(
    tag: String,
    expected: String,
    useUnmerged: Boolean = false,
) {
    val actual = textOf(tag, useUnmerged)
    assertEquals(
        expected,
        actual,
        "the node tagged '$tag' should say \"$expected\", but says \"$actual\"",
    )
}

/** How many nodes carry [tag]. For "every one of these is drawn", not "this one is drawn". */
@OptIn(ExperimentalTestApi::class)
internal fun ComposeUiTest.countOfTag(tag: String, useUnmerged: Boolean = false): Int =
    onAllNodesWithTag(tag, useUnmergedTree = useUnmerged).fetchSemanticsNodes().size

/**
 * Asserts [tag] appears on screen [expected] times, naming the tag in the failure.
 *
 * The count and the tag are the whole assertion; the message exists so a failure says which
 * of several similar expectations went wrong.
 */
@OptIn(ExperimentalTestApi::class)
internal fun ComposeUiTest.assertTagCount(tag: String, expected: Int, useUnmerged: Boolean = false) {
    val actual = countOfTag(tag, useUnmerged)
    assertTrue(
        actual == expected,
        "expected $expected node(s) tagged '$tag', found $actual",
    )
}
