package com.tasktracker.featuresettingspresentation

import com.tasktracker.core.AppError

/**
 * One member an administrator can mint a reset code for, as this sheet needs them.
 *
 * ## Why the sheet does not hold a `User`
 *
 * The directory's `User` lives in `:packages:feature-teams-domain`, and this module is not
 * allowed to depend on it — `verifyArchitectureGraph` refuses the edge, and it is right to:
 * the settings feature's job is a password, not a member directory, and the padlock on the
 * members screen is where that directory is administered. So the shell, which is allowed to
 * see both sides, reduces a `User` to these three strings and hands them down. What the sheet
 * needs is exactly a name to show, an address to disambiguate it, and an id to send back.
 */
data class ResetCodeCandidate(
    /** The opaque id the mint is addressed to. Never parsed here. */
    val id: String,
    /** The name the row and the delivery sentence use. */
    val label: String,
    /** The address under it — two members can share a display name. */
    val detail: String,
)

/**
 * A freshly minted reset code and the candidate it was minted for.
 *
 * One value rather than two fields: a code without its member cannot be worded (the sentence
 * says who it is for) and a member without a code has nothing to show, so the pair is only ever
 * raised and cleared together.
 */
data class MintedResetCode(
    val forMember: ResetCodeCandidate,
    val code: String,
)

/**
 * Everything the generator draws, in one value.
 *
 * The same shape `ServerAddressEditor` uses instead of six loose flags: the screen is a function
 * of its arguments, and the shell hands it one value. `candidates` is empty until the load has
 * answered, which is why [isLoadingCandidates] is separate from "there are none".
 */
data class ResetCodePanel(
    val candidates: List<ResetCodeCandidate> = emptyList(),
    val isLoadingCandidates: Boolean = false,
    val selectedID: String? = null,
    val minted: MintedResetCode? = null,
    val isGenerating: Boolean = false,
    val failure: AppError? = null,
) {
    /** The chosen candidate, resolved from [selectedID] so the two cannot disagree. */
    val selected: ResetCodeCandidate?
        get() = candidates.firstOrNull { it.id == selectedID }
}
