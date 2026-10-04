package com.tasktracker.featureprojectspresentation

import com.tasktracker.core.randomProjectID
import com.tasktracker.domain.WorkItemStatus
import com.tasktracker.domain.sprintSummary
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * The summary's verdict, and the promise that it is *read* rather than recomputed.
 *
 * ## This is the structural test 23.5.4 asks for
 *
 * "The hit/miss is read from the value, never recomputed here" is a claim about where an
 * answer comes from, and a claim about provenance cannot be checked by looking at rendered
 * text — a screen that recomputed the comparison from `achievedStoryPoints` and
 * `goalStoryPoints` would produce identical wording for every case below.
 *
 * What *can* be checked is that the two numbers and the flag never disagree inside a real
 * [sprintSummary], and that the flag's three states map to three verdicts. The first is the
 * stronger of the two, and it is the one that would catch a screen recomputing: if a
 * summary's numbers could produce a different answer from its own flag, there would be
 * something to recompute.
 *
 * ## Why "exactly at the goal" is its own case
 *
 * `SprintGoal.didHitGoal` uses `>=`, and the `>` that gets this wrong is named in its own
 * KDoc as "the most likely comparison to be written by hand somewhere else in the app". A
 * summary of 12 against a goal of 12 is the only fixture that distinguishes them, so it is
 * here rather than being left to the domain's own test — a test that only the domain runs
 * is a test that a *second* comparison in a view could pass without ever meeting it.
 */
class SprintVerdictTest {

    /**
     * A real [sprintSummary] for one done task worth [achieved] against a goal of
     * [goalStoryPoints].
     *
     * Built through the domain rather than by hand-constructing a [com.tasktracker.domain.
     * SprintSummary], because the claim being tested is about the value the screen is handed
     * — and a hand-built one could carry numbers and a flag that disagree, which is the
     * exact defect the second-to-last test asserts cannot happen.
     *
     * The sprint and the task share a project *and* the task is put in the sprint, because
     * `sprintSummary` filters by `sprintID`. A task left out of it would produce a summary
     * of zero points and every assertion below would pass for the wrong reason.
     */
    private fun summaryOf(
        goalStoryPoints: Int,
        achieved: Int,
    ): com.tasktracker.domain.SprintSummary {
        val projectID = randomProjectID()
        val sprint = testSprint(projectID = projectID, goalStoryPoints = goalStoryPoints)
        return sprintSummary(
            sprint = sprint,
            items = listOf(
                testWorkItem(
                    projectID = projectID,
                    key = "AP-1",
                    status = WorkItemStatus.Done,
                    storyPoints = achieved,
                    sprintID = sprint.id,
                ),
            ),
        )
    }

    @Test
    fun `exactly at the goal is a hit`() {
        val summary = summaryOf(goalStoryPoints = 12, achieved = 12)

        assertEquals(true, summary.didHitGoal)
        assertEquals(SprintVerdict.Hit, sprintVerdict(summary.didHitGoal))
    }

    @Test
    fun `past the goal is still a hit`() {
        val summary = summaryOf(goalStoryPoints = 8, achieved = 14)

        assertEquals(SprintVerdict.Hit, sprintVerdict(summary.didHitGoal))
    }

    @Test
    fun `short of the goal is a miss`() {
        val summary = summaryOf(goalStoryPoints = 12, achieved = 5)

        assertEquals(SprintVerdict.Miss, sprintVerdict(summary.didHitGoal))
    }

    @Test
    fun `a sprint with no goal is neither`() {
        // Goal 0, so `didHitGoal` is `null` rather than `false`. A two-way rendering would
        // have to claim one of the two, and the claim would be invented.
        val summary = summaryOf(goalStoryPoints = 0, achieved = 5)

        assertEquals(null, summary.didHitGoal)
        assertEquals(SprintVerdict.NoGoal, sprintVerdict(summary.didHitGoal))
    }

    @Test
    fun `the flag and the two numbers never disagree`() {
        // The stronger of the two claims, and the one that says there is nothing for a view
        // to recompute: for every goal/achieved pair, the flag agrees with the comparison a
        // screen would have made for itself. If a summary could carry numbers and a flag
        // that disagree, a view recomputing from the numbers would be right some of the time
        // — and wrong the rest, with no test noticing.
        for (goal in 0..12) {
            for (achieved in 0..12) {
                val summary = summaryOf(goalStoryPoints = goal, achieved = achieved)
                val recomputed = when {
                    goal <= 0 -> null
                    achieved >= goal -> true
                    else -> false
                }
                assertEquals(
                    recomputed,
                    summary.didHitGoal,
                    "goal $goal, achieved $achieved: the flag and the numbers disagree",
                )
                assertEquals(
                    when (recomputed) {
                        true -> SprintVerdict.Hit
                        false -> SprintVerdict.Miss
                        null -> SprintVerdict.NoGoal
                    },
                    sprintVerdict(summary.didHitGoal),
                    "goal $goal, achieved $achieved",
                )
            }
        }
    }
}
