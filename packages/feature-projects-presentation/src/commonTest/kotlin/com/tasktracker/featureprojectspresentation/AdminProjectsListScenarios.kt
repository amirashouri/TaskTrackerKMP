package com.tasktracker.featureprojectspresentation

import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.tasktracker.core.ProjectID
import com.tasktracker.core.UserID
import com.tasktracker.domain.Project
import com.tasktracker.featureprojectsdomain.AdminProject
import com.tasktracker.featureprojectsdomain.ProjectAdminStatus
import com.tasktracker.featureprojectsdomain.ProjectSummary
import com.tasktracker.resources.Res
import com.tasktracker.resources.admin_projects_segment_active
import com.tasktracker.resources.admin_projects_segment_archived
import kotlin.test.assertEquals
import kotlin.time.Instant
import org.jetbrains.compose.resources.getString

/**
 * The admin projects list, as a scenario: the bucket segments and the rows each one reads.
 *
 * ## What this covers that the transport tests cannot
 *
 * `HttpAdminProjectsRemoteTest` proves the request is *shaped* right — `status=active` and
 * `status=archived` in both directions — and that is a claim about the wire. This file is about
 * the half above it: that tapping a segment makes the screen ask for that bucket again and draw
 * the rows that came back, and nothing from the bucket it left. A screen whose picker only moved
 * its own highlight would pass every transport test ever written while showing one bucket under
 * two headings — which is the reported bug this scenario exists for.
 *
 * ## Why both directions and the way back
 *
 * The switch from Active to Archived is the feature; the switch back is what fails when the
 * effect is keyed on the selection *once*. One direction proves the tap was heard, both prove
 * every tap is.
 *
 * There is no `commonTest` entry point for the same reason the module's other suites have none:
 * an Android-host Compose UI test needs Robolectric under a JUnit 4 runner, which `commonTest`
 * cannot express. [ProjectsUiJvmTest], [ProjectsUiIosTest] and [ProjectsUiAndroidHostTest]
 * call this.
 */
@OptIn(ExperimentalTestApi::class)
object AdminProjectsListScenarios {

    /**
     * Each segment reads its own bucket, and the rows on screen are the bucket's own.
     *
     * The port is the assertion surface as well as the data source: `projectsCalls` says which
     * buckets the screen asked for, in order, so the claim "the segment re-read the server" is a
     * recorded call rather than an inference from whichever rows happened to appear.
     */
    val eachBucketSegmentReadsItsOwnBucket: suspend ComposeUiTest.() -> Unit = {
        val live = adminProject(name = "Apollo", keyPrefix = "AP", isArchived = false)
        val filed = adminProject(name = "Zephyr", keyPrefix = "ZE", isArchived = true)
        val port = RecordingAdminProjectsPort(
            byStatus = mapOf(
                ProjectAdminStatus.Active to listOf(live),
                ProjectAdminStatus.Archived to listOf(filed),
            ),
        )

        setContent {
            Themed {
                AdminProjectsScreen(
                    port = port,
                    onProjectSelected = {},
                    onBack = {},
                    onOpenAddForm = {},
                )
            }
        }
        waitForIdle()

        assertEquals(
            listOf(ProjectAdminStatus.Active),
            port.projectsCalls,
            "the screen must open on the active bucket — the segment highlights Active first, " +
                "and a list that showed the archived rows under that highlight would be lying " +
                "about which bucket it read",
        )
        onNodeWithTag(ProjectRowTag(ProjectSummary(live.project, itemCount = 0)))
            .assertIsDisplayed()
        onNodeWithTag(ProjectRowTag(ProjectSummary(filed.project, itemCount = 0)))
            .assertDoesNotExist()

        onNodeWithText(getString(Res.string.admin_projects_segment_archived)).performClick()
        waitForIdle()

        assertEquals(
            listOf(ProjectAdminStatus.Active, ProjectAdminStatus.Archived),
            port.projectsCalls,
            "tapping Archived must re-read the server for that bucket — a segment that only " +
                "moved its own highlight would leave the active rows on screen under an " +
                "Archived heading",
        )
        onNodeWithTag(ProjectRowTag(ProjectSummary(filed.project, itemCount = 0)))
            .assertIsDisplayed()
        onNodeWithTag(ProjectRowTag(ProjectSummary(live.project, itemCount = 0)))
            .assertDoesNotExist()

        onNodeWithText(getString(Res.string.admin_projects_segment_active)).performClick()
        waitForIdle()

        assertEquals(
            listOf(ProjectAdminStatus.Active, ProjectAdminStatus.Archived, ProjectAdminStatus.Active),
            port.projectsCalls,
            "the way back is a read like any other: a picker that only reacts to the segment it " +
                "has not yet shown would stick on Archived and show archived rows under an " +
                "Active heading",
        )
        onNodeWithTag(ProjectRowTag(ProjectSummary(live.project, itemCount = 0)))
            .assertIsDisplayed()
        onNodeWithTag(ProjectRowTag(ProjectSummary(filed.project, itemCount = 0)))
            .assertDoesNotExist()
    }
}

/** One row for the bucket a scenario is about, stamped with its own status. */
private fun adminProject(
    name: String,
    keyPrefix: String,
    isArchived: Boolean,
): AdminProject {
    val stamp = Instant.fromEpochSeconds(1_700_000_000)
    val project = Project(
        id = ProjectID("00000000-0000-4000-8000-00000000000${if (isArchived) 2 else 1}"),
        keyPrefix = keyPrefix,
        name = name,
        details = "",
        memberIDs = listOf(UserID("00000000-0000-4000-8000-000000000003")),
        isArchived = isArchived,
        nextItemNumber = 1,
        createdAt = stamp,
        updatedAt = stamp,
    )
    return AdminProject(project = project, version = RecordingAdminProjectsPort.SEEDED_VERSION)
}
