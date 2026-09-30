package com.tasktracker.featureprojectsdomain

import com.tasktracker.core.WorkItemKey
import com.tasktracker.core.graphemeClusterCount
import com.tasktracker.core.randomProjectID
import com.tasktracker.domain.WorkItem
import com.tasktracker.domain.WorkItemRules
import com.tasktracker.domain.WorkItemStatus
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.time.Instant

/**
 * What a board card is allowed to offer to reveal.
 *
 * The interesting case is not "has details" but "has details that are not only
 * whitespace". Local writes trim, so that state should be unreachable from the editor — but
 * a pulled record is written straight into the store, which is how a server can hand the
 * board a disclosure that opens onto nothing.
 */
class WorkItemPreviewTest {

    private val origin = Instant.fromEpochSeconds(1_700_000_000)

    private fun item(details: String, title: String = "A title") = WorkItem(
        projectID = randomProjectID(),
        key = WorkItemKey("TT-1"),
        title = title,
        details = details,
        status = WorkItemStatus.Todo,
        createdAt = origin,
        updatedAt = origin,
    )

    @Test
    fun `an item with no details has nothing to expand`() {
        assertFalse(WorkItemPreview(item(details = "")).hasMoreToShow)
    }

    @Test
    fun `whitespace-only details are nothing to expand`() {
        // A pulled record skips trimming, which is how these reach a preview at all. The
        // last one is a non-breaking space, which Kotlin's `trim` also removes — a detail
        // made of it is just as blank, and a disclosure onto it would look like a bug.
        for (details in listOf(" ", "\n", "  \n\t ", " ")) {
            assertFalse(
                WorkItemPreview(item(details = details)).hasMoreToShow,
                "a disclosure onto \"$details\" reveals nothing",
            )
        }
    }

    @Test
    fun `a description offers the affordance`() {
        assertTrue(WorkItemPreview(item(details = "Server wins.")).hasMoreToShow)
    }

    @Test
    fun `the title is passed through untouched because clamping is the view's line limit`() {
        val long = "t".repeat(WorkItemRules.MAX_TITLE_LENGTH)

        val preview = WorkItemPreview(item(details = "", title = long))

        assertEquals(long, preview.title)
        assertEquals(WorkItemRules.MAX_TITLE_LENGTH, graphemeClusterCount(preview.title))
    }

    @Test
    fun `a single character of description is previewable`() {
        val preview = WorkItemPreview(item(details = "d"))

        assertTrue(preview.hasMoreToShow)
        assertEquals(1, preview.details.length)
    }

    @Test
    fun `a long description is previewable rather than rejected`() {
        // Read side only: a pulled record bypasses `normaliseDetails`, so this must not throw
        // on a length a local write would have refused.
        val details = "d".repeat(WorkItemRules.MAX_DETAILS_LENGTH)

        val preview = WorkItemPreview(item(details = details))

        assertTrue(preview.hasMoreToShow)
        assertEquals(WorkItemRules.MAX_DETAILS_LENGTH, preview.details.length)
    }

    @Test
    fun `details are trimmed before being offered up`() {
        val preview = WorkItemPreview(item(details = "  spaced out \n"))

        assertEquals("spaced out", preview.details)
    }
}
