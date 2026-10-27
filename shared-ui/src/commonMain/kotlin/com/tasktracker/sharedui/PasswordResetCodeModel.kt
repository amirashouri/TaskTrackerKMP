package com.tasktracker.sharedui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.tasktracker.core.AppError
import com.tasktracker.featureteamsdomain.MemberAdminPort
import com.tasktracker.featureteamsdomain.User
import com.tasktracker.featureteamsdomain.UserStatus
import com.tasktracker.featuresettingspresentation.MintedResetCode
import com.tasktracker.featuresettingspresentation.ResetCodeCandidate
import com.tasktracker.featuresettingspresentation.ResetCodePanel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlin.coroutines.cancellation.CancellationException

/**
 * Mints a password-reset code for a member the administrator picks, from the settings sheet.
 *
 * ## Why this model lives in the shell
 *
 * The change-password sheet needs to offer an administrator "mint a code for this member", and
 * the two halves of that live in modules that cannot see each other: the sheet is in
 * `:packages:feature-settings-presentation`, the directory is `:packages:feature-teams-domain`,
 * and `verifyArchitectureGraph` refuses an edge between them — deliberately, because the
 * settings feature's business is a password and the member directory is administered on the
 * members screen. The shell is the one module allowed to see both, and binding a port for a
 * screen that cannot name it is exactly what it already does for `SettingsPort` and
 * `MemberAdminPort`.
 *
 * So the shell holds the few values and reduces a `User` to the three strings the sheet is
 * allowed to know ([ResetCodeCandidate]). The alternative — teaching the architecture table
 * that the settings feature may reach the directory — was rejected: the padlock on the members
 * screen is where that directory is administered, and the sheet only needs a name to show.
 *
 * ## Why this is not `MemberAdminModel`
 *
 * The members screen already mints codes, and this does not replace it — the padlock on a row
 * is still the shortest path from "I am looking at this person" to a code for them. What it
 * adds is the other entrance: an administrator already in Settings, changing their own
 * password, can hand a code to a member without leaving the sheet. `MemberAdminModel` also owns
 * buckets, deletion, creation and the add form; none of that belongs to a sheet.
 *
 * ## The load is deferred, and that is the point
 *
 * [ensureMembersLoaded] rather than a load in `observe`: the directory read happens when the
 * administrator actually selects the generator segment, not on every visit to Settings. It is
 * idempotent, so re-entering the segment costs nothing, and a failed load leaves the list empty
 * with [ResetCodePanel.failure] set — the next ask retries, because the flag that stops a second
 * read is set by an answer rather than by an attempt.
 *
 * ## Failures are held as the error, never as a sentence
 *
 * For the reason `SettingsModel.signOutFailure` documents: a model is a plain class and must
 * stay testable without a composition, so it cannot read a resource.
 */
class PasswordResetCodeModel(private val port: MemberAdminPort) {

    /** Everything the sheet draws. The one value the shell hands down. */
    var panel: ResetCodePanel by mutableStateOf(ResetCodePanel())
        private set

    /** The members as the port returned them, so a selection can be resolved back to an id. */
    private var members: List<User> = emptyList()

    /** Whether a load has ever succeeded, so the deferred load runs once. */
    private var hasLoaded = false

    private var loading: Job? = null

    /**
     * Reads the directory if it has not been read yet.
     *
     * Called from the generator's own composition, so the request is tied to the segment being
     * on screen rather than to the Settings tab being visited. A call while one is in flight is
     * ignored, and so is one after a success.
     */
    fun ensureMembersLoaded(scope: CoroutineScope) {
        if (hasLoaded || loading?.isActive == true) return
        loading = scope.launch { load() }
    }

    /**
     * Cancels the read in flight, for a destination that is leaving the tree.
     *
     * The same rule every model in this app follows: the scope is the caller's, so the caller is
     * what ends the work. A load that outlived its sheet would write into a model nothing is
     * showing, and the next visit builds a fresh one anyway — which is also why [hasLoaded]
     * staying true across a cancellation would be wrong, and is why it is only set by an answer.
     */
    fun stopObserving() {
        loading?.cancel()
        loading = null
    }

    /**
     * Chooses the member a code will be minted for.
     *
     * A code already on screen is cleared, because it belongs to the member that was selected
     * when it was minted: leaving it there would let an administrator copy a code for one person
     * while the sheet names another.
     */
    fun select(candidate: ResetCodeCandidate) {
        panel = panel.copy(selectedID = candidate.id, minted = null, failure = null)
    }

    /**
     * Mints a code for the selected member and holds it for the sheet.
     *
     * Returns early with no request when nothing is selected — the button is disabled in that
     * state, and a guard here is what keeps that rule true for any other caller. The previous
     * code is cleared before the request rather than after it, so a slow mint cannot leave the
     * last code on screen looking like the answer to this tap.
     */
    suspend fun generate() {
        val chosen = panel.selected ?: return
        val member = members.firstOrNull { it.id.stringValue == chosen.id } ?: return
        panel = panel.copy(isGenerating = true, failure = null, minted = null)
        try {
            panel = panel.copy(
                minted = MintedResetCode(
                    forMember = chosen,
                    code = port.generateResetCode(member.id),
                ),
            )
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (failure: AppError) {
            panel = panel.copy(failure = failure)
        } finally {
            panel = panel.copy(isGenerating = false)
        }
    }

    private suspend fun load() {
        panel = panel.copy(isLoadingCandidates = true, failure = null)
        try {
            val directory = port.members(UserStatus.Active)
            members = directory
            hasLoaded = true
            panel = panel.copy(candidates = directory.map { it.toCandidate() })
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (failure: AppError) {
            // The rows are dropped rather than kept: a stale directory would offer a member who
            // may have been deleted since, and the code would be refused after the hand-off.
            members = emptyList()
            panel = panel.copy(candidates = emptyList(), failure = failure)
        } finally {
            panel = panel.copy(isLoadingCandidates = false)
        }
    }
}

/**
 * A directory row as the sheet is allowed to see it.
 *
 * `preferredGreeting` rather than `displayName` because the delivery sentence names the person
 * the way the members list does — the same three-line rule, applied once, at the boundary where
 * the richer type stops being visible.
 */
private fun User.toCandidate(): ResetCodeCandidate = ResetCodeCandidate(
    id = id.stringValue,
    label = preferredGreeting,
    detail = email,
)
