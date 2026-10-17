package com.tasktracker.featureteamspresentation

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.tasktracker.designsystem.ttLabel
import com.tasktracker.featureteamsdomain.MemberRole

/**
 * [AddMemberForm] over a scrim, centred — the phone's presentation of the form.
 *
 * ## What this adds, and why it is only this
 *
 * The form itself is [AddMemberForm]; this composable is the container that makes it a popup.
 * The split exists because a wide window draws the *same panel* in a pane beside the list, where
 * a scrim over a half-empty pane would darken a list the form is not covering. Two arrangements,
 * one form.
 *
 * ## Drawn inline rather than with `Dialog`, for the reason the projects confirmation gives
 *
 * Compose Multiplatform's `Dialog` is platform-backed and does not render under
 * `runComposeUiTest` on every target, so a form built from it would be untestable on at least
 * one of the three hosts. Drawing it as an overlay in the same tree keeps it in the semantics
 * pass, where a tag finds it like anything else.
 */
@Composable
fun AddMemberDialog(
    isCreating: Boolean,
    errorMessage: String?,
    onCreate: (email: String, password: String, role: MemberRole, position: String?) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(ttLabel().copy(alpha = SCRIM_ALPHA)),
        contentAlignment = Alignment.Center,
    ) {
        AddMemberForm(
            isCreating = isCreating,
            errorMessage = errorMessage,
            onCreate = onCreate,
            onDismiss = onDismiss,
        )
    }
}

/** The scrim behind the form: dark enough to separate it, light enough to still see the list. */
private const val SCRIM_ALPHA = 0.35f
