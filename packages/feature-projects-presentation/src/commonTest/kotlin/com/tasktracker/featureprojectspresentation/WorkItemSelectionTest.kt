package com.tasktracker.featureprojectspresentation

import com.tasktracker.core.WorkItemID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * The shared selection set, on its own.
 *
 * The model tests exercise it through the backlog, but the type is what the board spec reuses, and
 * the rules that matter there are properties of this class: order is the user's, membership is
 * idempotent, and [WorkItemSelection.retaining] is what turns a batch outcome into "only the
 * failures stay ticked".
 */
class WorkItemSelectionTest {

    private val first = WorkItemID("00000000-0000-4000-8000-000000000001")
    private val second = WorkItemID("00000000-0000-4000-8000-000000000002")
    private val third = WorkItemID("00000000-0000-4000-8000-000000000003")

    @Test
    fun `selection follows the order the user picked`() {
        val selection = WorkItemSelection() + second + first + third

        assertEquals(listOf(second, first, third), selection.ids)
        assertEquals(3, selection.size)
    }

    @Test
    fun `selecting the same item twice does not duplicate it`() {
        val selection = WorkItemSelection() + first + first

        assertEquals(listOf(first), selection.ids)
    }

    @Test
    fun `toggle adds then removes`() {
        val selected = WorkItemSelection().toggle(first)
        assertTrue(first in selected)

        val unselected = selected.toggle(first)
        assertFalse(first in unselected)
        assertTrue(unselected.isEmpty)
    }

    @Test
    fun `retaining keeps only the given ids in selection order`() {
        val selection = WorkItemSelection(listOf(third, first, second))

        val retained = selection.retaining(listOf(first, second))

        assertEquals(listOf(first, second), retained.ids)
    }

    @Test
    fun `retaining an unchanged set returns the same instance`() {
        val selection = WorkItemSelection(listOf(first, second))

        assertEquals(selection, selection.retaining(listOf(first, second, third)))
    }

    @Test
    fun `clearing an empty selection does not allocate a new one`() {
        val empty = WorkItemSelection()

        assertEquals(empty, empty.cleared())
        assertTrue(WorkItemSelection(listOf(first)).cleared().isEmpty)
    }

    @Test
    fun `asSet is membership without the order`() {
        val selection = WorkItemSelection(listOf(second, first))

        assertEquals(setOf(first, second), selection.asSet())
    }
}
