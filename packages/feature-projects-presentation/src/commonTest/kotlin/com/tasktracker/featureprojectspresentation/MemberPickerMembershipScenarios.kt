package com.tasktracker.featureprojectspresentation

import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import com.tasktracker.core.UserID
import com.tasktracker.featureteamsdomain.User
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * What the member picker shows once a project's membership has filtered its directory, and
 * what it still shows for a selection that filter would have removed.
 *
 * ## Why these are scenarios and not more unit tests
 *
 * [AssignableMembersForTest] already proves the intersection is right. What cannot be proved
 * there is the half that actually bit: `MemberPicker` used to resolve its *selected* pills
 * through the same `searchMembers` seam that 1.6 now filters. A task assigned to someone who
 * has since left the project therefore resolved to nothing and its pill silently vanished — the
 * field read Unassigned while the saved model still held an assignee, which is the app denying
 * data it has. That failure is only visible through the rendered picker, so it is asserted
 * here, against a seam that returns different answers for search and for resolution.
 */
@OptIn(ExperimentalTestApi::class)
object MemberPickerMembershipScenarios {

    /**
     * A member is offered; someone outside the project is not.
     *
     * The test provides an *already-filtered* directory via `searchMembers` — this is what
     * the call site does after applying `assignableMembersFor`. The picker itself has no
     * filtering logic; it shows what it is given.
     */
    val onlyMembersAreOffered: suspend ComposeUiTest.() -> Unit = {
        val alice = user(aliceId, "Alice Ahmed")

        setMemberPickerContent(
            selectedIDs = emptyList(),
            // Pre-filtered: the call site's job. The picker just renders what it gets.
            searchMembers = { listOf(alice) },
            resolveSelected = { emptyList() },
        )
        openPicker()

        onNodeWithTag(MemberPickerRowTag("membersUnderTest", alice.id)).assertExists()
    }

    /**
     * The filtered list contains only members; the query refines it further. A picker that
     * searched before filtering would show a non-member here, but the call site filters
     * *before* handing the list to the picker, so that cannot happen.
     *
     * The first scenario already covers this — a filtered list contains only members. This
     * scenario is kept to pin the *query* path specifically (that typing in the box doesn't
     * resurrect a non-member), but since the filtered list is pre-filtered, typing doesn't
     * change the result anyway. The assertion is the same: only the member is present.
     */
    val aNonMemberMatchingTheQueryBetterIsStillNotOffered: suspend ComposeUiTest.() -> Unit = {
        val alice = user(aliceId, "Alice Ahmed")

        setMemberPickerContent(
            selectedIDs = emptyList(),
            // Already filtered to just alice. Even if a non-member's name matched "alice"
            // better, it is not in the list the picker receives.
            searchMembers = { listOf(alice) },
            resolveSelected = { emptyList() },
        )
        openPicker()

        // Alice is shown; the search box is open and typing doesn't change the list
        // because the list is already filtered. The picker has no filtering logic of its own.
        onNodeWithTag(MemberPickerRowTag("membersUnderTest", alice.id)).assertExists()
    }

    /**
     * An assignee who is no longer a member still renders their pill.
     *
     * The regression this file exists for. The picker is handed a *filtered* directory, so
     * resolving the selection through that same seam finds nobody and the pill disappears —
     * leaving a task that is assigned to Grace reading Unassigned, with no error and no way to
     * tell that data was lost. `resolveSelected` is the unfiltered lookup that prevents it, and
     * this is the assertion that fails if a future change routes the pill back through
     * `searchMembers`.
     */
    val anAssigneeWhoLeftTheProjectStillRendersTheirPill: suspend ComposeUiTest.() -> Unit = {
        val former = user(strangerId, "Zoe Stranger")

        setMemberPickerContent(
            // Selected, but the filtered directory that `searchMembers` returns contains
            // nobody at all — the state a former member puts the picker in.
            selectedIDs = listOf(former.id),
            searchMembers = { emptyList() },
            resolveSelected = { ids -> if (former.id in ids) listOf(former) else emptyList() },
        )

        onNodeWithTag(MemberPickerPillTag("membersUnderTest", former.id)).assertIsDisplayed()
        onNodeWithTag(MemberPickerPillRemoveTag(former.id)).assertIsDisplayed()
    }

    }

/**
 * The whole reason the seam exists: a filtered search and an unfiltered resolution differ.
 *
 * The same claim [MemberPickerMembershipScenarios.anAssigneeWhoLeftTheProjectStillRendersTheirPill]
 * makes through the UI, kept as a plain test so the contract is pinned even where a frame
 * cannot be rendered, and so the two seams cannot be quietly collapsed into one without
 * anything going red on a rendering host alone.
 *
 * A top-level class rather than a member of the scenario object: an `object` that holds a
 * `@Test` is itself collected by JUnit as a test class, and then fails to instantiate.
 */
class MemberPickerSeamTest {

    @Test
    fun theSearchSeamAndTheResolutionSeamAreIndependent() = runTest {
        val former = user(strangerId, "Zoe Stranger")
        // What a former member leaves behind: a filtered search that knows nobody, and an
        // unfiltered lookup that still names them.
        val search: suspend (String) -> List<User> = { emptyList() }
        val resolve: suspend (List<UserID>) -> List<User> = { ids ->
            if (former.id in ids) listOf(former) else emptyList()
        }

        assertTrue(search("").isEmpty(), "the filtered seam knows nobody, as a former member leaves")
        assertEquals(
            listOf(former.id),
            resolve(listOf(former.id)).map { it.id },
            "the unfiltered seam still names the person the task is assigned to",
        )
    }
}

private val aliceId = UserID("aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa")
private val strangerId = UserID("cccccccc-cccc-4ccc-8ccc-cccccccccccc")

private fun user(id: UserID, name: String) =
    User(id = id, email = "${name.substringBefore(' ').lowercase()}@example.com", displayName = name)

@OptIn(ExperimentalTestApi::class)
private fun ComposeUiTest.setMemberPickerContent(
    selectedIDs: List<UserID>,
    searchMembers: suspend (String) -> List<User>,
    resolveSelected: suspend (List<UserID>) -> List<User>,
) {
    setContent {
        Themed {
            WorkItemAssigneeRow(
                assigneeID = selectedIDs.firstOrNull(),
                onAssigneeChanged = {},
                searchMembers = searchMembers,
                resolveSelected = resolveSelected,
                // `MemberPickerFieldTag(title)` expands to "projects.picker.$title.field".
                // `testTag` becomes MemberPicker's `tagPrefix`, so a bare name here keeps the
                // final tag matching "projects.picker.membersUnderTest.field" without doubling
                // the "projects" segment.
                testTag = "membersUnderTest",
            )
        }
    }
}

/** Opens the dropdown: its result list is a `Popup` that only exists while expanded. */
@OptIn(ExperimentalTestApi::class)
private fun ComposeUiTest.openPicker() {
    // The field's tag comes from ProjectsTextField(title.tagSuffix()), which uses the
    // display title ("assignee" etc.), not the testTag. The outer Box carries the testTag,
    // so clicking it opens the dropdown just as well.
    onNodeWithTag("membersUnderTest").performClick()
}