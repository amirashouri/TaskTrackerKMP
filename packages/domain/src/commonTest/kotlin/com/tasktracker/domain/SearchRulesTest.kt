package com.tasktracker.domain

import com.tasktracker.core.WorkItemKey
import com.tasktracker.core.randomProjectID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertSame
import kotlin.test.assertTrue
import kotlin.time.Instant

/**
 * What the app means by "a task matches what I typed".
 *
 * ## Why the negative cases carry this file
 *
 * A matcher that returns `true` too often is the failure mode nobody reports: the list looks
 * filtered, the row the user wanted is somewhere in it, and the query is quietly being read
 * against a field that is not on screen. So every "does not match" here is a specific field the
 * rule refuses to look at — details, tags, assignee — and each one is a row that would otherwise
 * appear under a query it does not visibly answer.
 */
class SearchRulesTest {

    /** A fixed instant, so the fixture is the same on every host and every run. */
    private val origin = Instant.fromEpochSeconds(1_700_000_000)

    private fun item(
        key: String,
        title: String,
        details: String = "",
        assigneeID: String? = null,
    ) = WorkItem(
        projectID = randomProjectID(),
        key = WorkItemKey(key),
        title = title,
        details = details,
        assigneeID = assigneeID?.let { com.tasktracker.core.UserID(it) },
        createdAt = origin,
        updatedAt = origin,
    )

    // ---------------------------------------------------------------------------------
    // The two fields that are searched
    // ---------------------------------------------------------------------------------

    @Test
    fun aKeyMatchesInAnyCase() {
        val task = item("TT-142", title = "Fix the login form")

        assertTrue(WorkItemSearch.matches(task, "TT-142"), "the exact key should match")
        assertTrue(WorkItemSearch.matches(task, "tt-142"), "and in lower case")
        assertTrue(WorkItemSearch.matches(task, "142"), "and by its number alone")
        assertTrue(WorkItemSearch.matches(task, "TT-1"), "and by a prefix, which is how typing works")
    }

    @Test
    fun aTitleMatchesInAnyCase() {
        val task = item("TT-142", title = "Fix the Login Form")

        assertTrue(WorkItemSearch.matches(task, "login"), "a word from the title should match")
        assertTrue(WorkItemSearch.matches(task, "LOGIN FORM"), "and in any case")
        assertTrue(WorkItemSearch.matches(task, "the login"), "and across a word boundary")
    }

    // ---------------------------------------------------------------------------------
    // The fields that are not
    // ---------------------------------------------------------------------------------

    @Test
    fun detailsAreNotSearched() {
        val task = item("TT-142", title = "Fix the login form", details = "reproduced on Safari only")

        assertFalse(
            WorkItemSearch.matches(task, "Safari"),
            "a word that only appears in the details would match a card whose visible text " +
                "does not contain it, which reads as a broken filter",
        )
    }

    @Test
    fun anAssigneeIsNotSearched() {
        val task = item(
            "TT-142",
            title = "Fix the login form",
            assigneeID = "00000000-0000-4000-8000-000000000001",
        )

        assertFalse(
            WorkItemSearch.matches(task, "Amir"),
            "assignee names are not in the item at all — they are resolved by presentation",
        )
        assertFalse(
            WorkItemSearch.matches(task, "00000000-0000-4000-8000-000000000001"),
            "and the raw id is not a thing a user types",
        )
    }

    /**
     * A key typed without its separator is a different string, and stays one.
     *
     * This is the one negative that a well-meaning "be helpful" change would break: stripping
     * `-` from both sides makes `TT142` find `TT-142`, and it also makes `TT-1` find `TT-14`,
     * `TT-140` and everything else whose digits start the same way.
     */
    @Test
    fun aKeyWithoutItsSeparatorDoesNotMatch() {
        val task = item("TT-142", title = "Fix the login form")

        assertFalse(WorkItemSearch.matches(task, "TT142"))
    }

    @Test
    fun aQueryThatMatchesNothingMatchesNothing() {
        val task = item("TT-142", title = "Fix the login form")

        assertFalse(WorkItemSearch.matches(task, "billing"))
    }

    // ---------------------------------------------------------------------------------
    // Filtering
    // ---------------------------------------------------------------------------------

    @Test
    fun aBlankQueryIsEverythingAndIsTheSameList() {
        val items = listOf(item("TT-1", "One"), item("TT-2", "Two"))

        assertSame(
            items,
            WorkItemSearch.filter(items, ""),
            "a blank query should return the list it was given, not a copy — the screens call " +
                "this on every recomposition while nobody is searching",
        )
        assertSame(items, WorkItemSearch.filter(items, "   "), "whitespace is blank")
    }

    @Test
    fun aQueryKeepsTheOrderItWasGiven() {
        val items = listOf(
            item("TT-3", "Third login fix"),
            item("TT-1", "First login fix"),
            item("TT-2", "Unrelated"),
        )

        assertEquals(
            listOf("TT-3", "TT-1"),
            WorkItemSearch.filter(items, "login").map { it.key.rawValue },
            "the backlog sorts by a user-chosen basis, so filtering must not re-order",
        )
    }

    @Test
    fun aQueryIsTrimmedBeforeItIsMatched() {
        val items = listOf(item("TT-1", "Fix the login form"), item("TT-2", "Unrelated"))

        assertEquals(
            listOf("TT-1"),
            WorkItemSearch.filter(items, "  login  ").map { it.key.rawValue },
            "a trailing space from a paste should not empty the list",
        )
    }
}
