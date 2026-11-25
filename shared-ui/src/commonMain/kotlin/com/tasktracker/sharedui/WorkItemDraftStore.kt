package com.tasktracker.sharedui

import com.tasktracker.core.ProjectID
import com.tasktracker.featureprojectspresentation.WorkItemDraft

/**
 * One unsaved create per project, kept for as long as the signed-in shell is composed.
 *
 * ## Why the shell owns this rather than the editor
 *
 * A tab switch disposes the editor's whole destination. A `remember` inside it would die with
 * the form, which is the loss this store exists to prevent, so the state has to live above the
 * route tree. [NavigationHost] is the lowest composable that survives every tab and route
 * change in a session.
 *
 * ## Why it is keyed by project and not by route
 *
 * Two ways into a create — the board lane and the desktop's New Task — are two different
 * routes for the same act. Keying on the route would make each one keep its own half-finished
 * task, and the user would have to remember which button held it. There is one draft per
 * project, and whichever create opens next restores it.
 *
 * ## Scope
 *
 * Session-only by design. The request is about losing a form on a tab switch; persisting a
 * draft through a relaunch would be a storage feature with its own migration, and pretending
 * this store does that would be worse than saying so. It is cleared by an explicit Cancel or a
 * successful Save, and dropped with the shell on sign-out.
 */
internal class WorkItemDraftStore {

    private val drafts = mutableMapOf<String, WorkItemDraft>()

    /** Removes and returns the draft for [projectID], so one restore cannot be handed out twice. */
    fun take(projectID: ProjectID): WorkItemDraft? = drafts.remove(projectID.stringValue)

    fun put(projectID: ProjectID, draft: WorkItemDraft) {
        drafts[projectID.stringValue] = draft
    }

    fun clear(projectID: ProjectID) {
        drafts.remove(projectID.stringValue)
    }
}
