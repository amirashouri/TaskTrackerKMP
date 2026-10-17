package com.tasktracker.sharedui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import com.tasktracker.core.ProjectID

/**
 * An admin section's list-detail pair, side by side.
 *
 * ## Why this is a second composable rather than a rename of `SprintTwoPanes`
 *
 * The two are the same three boxes and the same hairline — which is why they share
 * [SprintPaneDivider] and the pane weight and gutter rather than restating a geometry — and
 * they are deliberately still two names. A two-pane layout is not a sprints thing or an admin
 * thing; what differs is *which* two screens a caller passes, and a shared `TwoPanes` with two
 * callers would be honest about that only until a third arrangement needed a different split.
 * A name each keeps the call site readable and keeps the decision in [NavigationHost], which is
 * the only place that knows which pair a tab's stack actually holds.
 *
 * ## Why two lambdas and not two screens
 *
 * For the reason `SprintTwoPanes` gives: the caller has already rendered both screens — for the
 * projects section, [NavigationHost] draws the list from the section's own route and the editor
 * from the pushed one, off the router, which is the only navigation state in this module. A
 * component handed the two screens directly would have to build them itself, which is a second
 * answer to a question the router has already answered.
 *
 * The members section's right pane is not a route at all: it is the section's create form, open
 * or closed by its model, and it is the one pane here filled from state rather than from the
 * stack.
 *
 * Nothing here is animated and nothing is remembered, so a tap, a restored stack and a
 * re-layout all land on the same pair of screens and differ only in where they are drawn.
 */
@Composable
fun AdminTwoPanes(
    listPane: @Composable () -> Unit,
    detailPane: @Composable () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.fillMaxSize(),
        horizontalArrangement = Arrangement.spacedBy(PANE_GUTTER),
    ) {
        Box(modifier = Modifier.weight(PANE_WEIGHT).fillMaxHeight()) { listPane() }
        SprintPaneDivider()
        Box(modifier = Modifier.weight(PANE_WEIGHT).fillMaxHeight()) { detailPane() }
    }
}

/**
 * The members administration screen, as the shell draws it until the feature screen lands.
 *
 * ## What this is for, and what it is not
 *
 * These tasks land in order — routes and panes first, then the domain that can list members,
 * then the screen that draws them — so there is a window in which the navigation is real and
 * the screen is not written yet. This is that window's screen: a tagged surface where the list
 * will be, so the routing and the pane arrangement are exercised by a test rather than assumed,
 * and the shell does not carry a branch that renders nothing.
 *
 * It is deliberately **empty**. A placeholder that invented copy would be a second set of words
 * for the real screen to delete, and this module owns no user-facing strings; the section's
 * title already lives in the design system as `settings_manage_members`, which is what the
 * Settings row that opens this is labelled with. The tag is the placeholder's whole contract.
 *
 * ## Why its tag carries no id, unlike its projects twin
 *
 * It did, until the member detail it named was removed (`D6`): the members section has nothing
 * you can select, so there is no "which member is this surface showing" to ask. The projects
 * twin keeps the id because a project *is* selectable — selecting one opens the editor.
 */
@Composable
fun AdminMembersPlaceholder(modifier: Modifier = Modifier) {
    Box(modifier = modifier.fillMaxSize().testTag(AdminMembersListTag))
}

/** The projects administration screen, in the same shape and for the same reasons as its members twin. */
@Composable
fun AdminProjectsPlaceholder(
    projectID: ProjectID?,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .testTag(
                if (projectID == null) {
                    AdminProjectsListTag
                } else {
                    AdminProjectDetailTag(projectID)
                },
            ),
    )
}

/**
 * The right pane of an admin section with nothing chosen.
 *
 * The counterpart to `SprintDetailPlaceholder`, and here for the same reason: the left pane is
 * the section's list, so a right pane that drew the selection *or* the list would either be
 * blank without saying why or draw the list twice. Empty rather than worded, for the reason
 * [AdminMembersPlaceholder] gives — the sentence belongs with the real screen's strings.
 */
@Composable
fun AdminDetailPlaceholder(modifier: Modifier = Modifier) {
    Box(modifier = modifier.fillMaxSize().testTag(AdminDetailPlaceholderTag))
}

/** The members section's list screen. */
const val AdminMembersListTag = "shell.admin.members"

/** The projects section's list screen. */
const val AdminProjectsListTag = "shell.admin.projects"

/** The projects section's detail screen for one project — the editor, once a project is selected. */
fun AdminProjectDetailTag(projectID: ProjectID): String =
    "shell.admin.project.${projectID.stringValue}"

/** The right pane of an admin section with nothing chosen. */
const val AdminDetailPlaceholderTag = "shell.admin.noSelection"
