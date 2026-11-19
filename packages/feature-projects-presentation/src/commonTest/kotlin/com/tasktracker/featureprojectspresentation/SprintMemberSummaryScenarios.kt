package com.tasktracker.featureprojectspresentation

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.tasktracker.core.UserID
import com.tasktracker.core.randomProjectID
import com.tasktracker.core.randomUserID
import com.tasktracker.designsystem.ThemeScheme
import com.tasktracker.designsystem.ttTheme
import com.tasktracker.domain.SprintMember
import com.tasktracker.domain.WorkItemStatus
import com.tasktracker.domain.sprintParticipation
import com.tasktracker.domain.sprintSummary
import com.tasktracker.domain.WorkItem
import com.tasktracker.featureteamsdomain.User
import com.tasktracker.featureteamsdomain.UserStatus
import kotlin.test.assertTrue

/**
 * The two screens introduced by the roster feature: a member section inside the sprint
 * summary, and the fuller per-member report reached from it.
 *
 * These are composed scenarios rather than model tests because the contract is visual: a
 * correct [com.tasktracker.domain.SprintParticipation] that no screen renders is still a
 * broken feature, and a tappable link that is not wired is invisible to a unit assertion.
 */
@OptIn(ExperimentalTestApi::class)
object SprintMemberSummaryScenarios {

    private fun aFixture(): Triple<com.tasktracker.domain.Sprint, SprintMember, List<WorkItem>> {
        val projectID = randomProjectID()
        val sprint = testSprint(projectID = projectID, goalStoryPoints = 8)
        val userID = randomUserID()
        val member = SprintMember(
            id = SprintMember.idFor(sprint.id, userID),
            projectID = projectID,
            sprintID = sprint.id,
            userID = userID,
            availableStoryPoints = 8,
            createdAt = sprint.createdAt,
            updatedAt = sprint.updatedAt,
        )
        val finished = testWorkItem(
            projectID = projectID,
            key = "AP-1",
            title = "Ship the migration",
            status = WorkItemStatus.Done,
            assigneeID = userID,
            storyPoints = 5,
            sprintID = sprint.id,
        )
        val unassigned = testWorkItem(
            projectID = projectID,
            key = "AP-2",
            title = "Unclaimed work",
            status = WorkItemStatus.Done,
            storyPoints = 2,
            sprintID = sprint.id,
        )
        return Triple(sprint, member, listOf(finished, unassigned))
    }

    val theSummaryListsMembersAndTheirFinishedPoints: suspend ComposeUiTest.() -> Unit = {
        val (sprint, member, items) = aFixture()
        val summary = sprintSummary(sprint, items, listOf(member))
        var opened = false

        setContent {
            ttTheme(scheme = ThemeScheme.Dark) {
                SprintSummaryScreen(
                    summary = summary,
                    onBack = {},
                    memberNames = mapOf(member.userID to "Ada"),
                    onOpenMemberProgress = { opened = true },
                )
            }
        }

        onNodeWithTag(SprintSummaryMembersTag).assertIsDisplayed()
        onNodeWithTag(SprintSummaryMemberRowTag(member.id)).assertIsDisplayed()
        onNodeWithText("Ada").assertIsDisplayed()
        onNodeWithText("5 of 8 pts").assertIsDisplayed()
        // The finished task is asserted on the progress screen: the summary also lists it in
        // its Delivered section, so a bare text match here would have two valid answers.

        onNodeWithTag(SprintSummaryMemberProgressTag)
            .assertIsDisplayed()
            .performClick()
        assertTrue(opened, "Member progress should open the progress screen")
    }

    val theProgressScreenShowsCapacityAndUnclaimedWork: suspend ComposeUiTest.() -> Unit = {
        val (sprint, member, items) = aFixture()
        val view = SprintMemberProgressView(
            sprintName = sprint.name,
            participation = sprintParticipation(listOf(member), items),
            memberNames = mapOf(member.userID to "Ada"),
        )

        setContent {
            ttTheme(scheme = ThemeScheme.Dark) {
                SprintMemberProgressScreen(view = view, onBack = {})
            }
        }

        onNodeWithTag(SprintMemberProgressRowTag(member.id)).assertIsDisplayed()
        onNodeWithTag(SprintMemberProgressUnassignedTag).assertIsDisplayed()
        onNodeWithText("5 of 8 pts").assertIsDisplayed()
        onNodeWithText("Ship the migration").assertIsDisplayed()
        onNodeWithText("Unclaimed work").assertIsDisplayed()
    }

    /**
     * One capacity belongs to one person, so the roster picker is single-select.
     *
     * Picking a second name replaces the first rather than adding a pill beside it: the sheet
     * owns one `availablePoints` value, and a selection of two would have to apply one number to
     * two people on save.
     */
    val theMemberSheetPicksExactlyOnePersonForOneCapacity: suspend ComposeUiTest.() -> Unit = {
        val ada = User(id = randomUserID(), email = "ada@example.com", status = UserStatus.Active)
        val grace = User(id = randomUserID(), email = "grace@example.com", status = UserStatus.Active)
        val selected = mutableStateOf<UserID?>(null)

        setContent {
            ttTheme(scheme = ThemeScheme.Dark) {
                SprintMemberSheet(
                    visible = true,
                    isEditing = false,
                    selectedUserID = selected.value,
                    availablePoints = "",
                    isBusy = false,
                    onSelectedUserIDChanged = { selected.value = it },
                    onAvailablePointsChanged = {},
                    onSave = {},
                    onRemove = null,
                    onDismissRequest = {},
                    onDismissed = {},
                    searchMembers = { listOf(ada, grace) },
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }

        onNodeWithTag("sprints.member.picker").performClick()
        waitForIdle()
        onNodeWithTag(MemberPickerRowTag("sprints.member.picker", ada.id)).performClick()
        waitForIdle()

        onNodeWithTag("sprints.member.picker").performClick()
        waitForIdle()
        onNodeWithTag(MemberPickerRowTag("sprints.member.picker", grace.id)).performClick()
        waitForIdle()

        assertTrue(selected.value == grace.id, "the second pick replaces the first, it does not add")
        onNodeWithTag(MemberPickerPillTag("sprints.member.picker", grace.id)).assertIsDisplayed()
        onNodeWithTag(MemberPickerPillTag("sprints.member.picker", ada.id)).assertDoesNotExist()
    }

    val theMemberSheetOnlySavesAWholeNonNegativeCapacity: suspend ComposeUiTest.() -> Unit = {
        val userID = randomUserID()
        val availablePoints = mutableStateOf("-2")
        var saved = false

        setContent {
            ttTheme(scheme = ThemeScheme.Dark) {
                SprintMemberSheet(
                    visible = true,
                    isEditing = false,
                    selectedUserID = userID,
                    availablePoints = availablePoints.value,
                    isBusy = false,
                    onSelectedUserIDChanged = {},
                    onAvailablePointsChanged = { availablePoints.value = it },
                    onSave = { saved = true },
                    onRemove = null,
                    onDismissRequest = {},
                    onDismissed = {},
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }

        onNodeWithTag(SprintMemberSheetTag).assertIsDisplayed()
        onNodeWithTag(SprintMemberCapacityTag).assertIsDisplayed()
        onNodeWithTag(SprintMemberInvalidPointsTag).assertIsDisplayed()
        onNodeWithTag(SprintMemberSaveTag).assertIsDisplayed().assertIsNotEnabled()

        // Recompose through the value the sheet receives. A second setContent call would
        // attempt to set the Android host Activity's content twice, which the test
        // environment rejects.
        runOnIdle { availablePoints.value = "5" }
        waitForIdle()

        onNodeWithTag(SprintMemberSaveTag).assertIsEnabled().performClick()
        assertTrue(saved, "a valid capacity should enable Save")
    }
}
