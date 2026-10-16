package com.tasktracker.featureteamspresentation

import androidx.compose.runtime.Composable
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import com.tasktracker.core.UserID
import com.tasktracker.designsystem.TTConfirmPopupConfirmTag
import com.tasktracker.designsystem.TTConfirmPopupTag
import com.tasktracker.designsystem.TTGlassBackButtonTag
import com.tasktracker.designsystem.ThemeScheme
import com.tasktracker.designsystem.ttTheme
import com.tasktracker.featureteamsdomain.MemberRole
import com.tasktracker.featureteamsdomain.User
import com.tasktracker.featureteamsdomain.UserStatus
import com.tasktracker.featuresettingsdomain.UserPosition
import kotlin.test.assertEquals

/**
 * The members screen, as scenarios.
 *
 * ## What these cover that [MemberAdminModelTest] cannot
 *
 * The model test proves the *rules*: which bucket is default, which status offers which action,
 * what a delete does to the list. Every one of those is a value the model computes. None of them
 * is the thing this file is about, which is the **wiring** — that the screen draws the row for
 * the member it says it is drawing, hands that row the action the rule chose, and routes a tap
 * into the callback it names. A model test cannot see any of it: `memberRowAction` can be exactly
 * right while the screen calls it for the wrong row, or passes it to the wrong branch.
 *
 * So the assertions here are structural and deliberately narrow — a tag is present, a tag is
 * absent, a counter moved once. The negative assertions carry as much weight as the positive
 * ones: "the trash exists on a live row" and "the trash does not exist on a deleted row" are the
 * two halves of the acceptance rule that a deleted member offers reactivation instead of
 * deletion, and only the second half fails when the branch is inverted.
 *
 * ## Why there is no `commonTest` entry point
 *
 * These compose UI, so they need a host. A `commonTest` Compose UI class compiles on every target
 * and then fails on the Android host, because Robolectric only engages when a JUnit 4 runner is
 * attached and `@RunWith` cannot be written in `commonTest`. The assertions therefore live here,
 * and [MembersAdminViewJvmTest], [MembersAdminViewIosTest] and
 * [MembersAdminViewAndroidHostTest] are entry points that call them.
 *
 * ## No pixel measurements
 *
 * Everything asserted here survives being read off the semantics tree, which is where it should
 * be read. Ink contrast of the trash glyph and of the `+` over glass is a *rendered* property and
 * belongs to the design-system work that owns those primitives, not to this screen's wiring.
 */
@OptIn(ExperimentalTestApi::class)
object MembersAdminScenarios {

    /** An administrator: the role the list shows as a caption, not the role it gates on. */
    private val ada = User(
        id = UserID("00000000-0000-4000-8000-000000000001"),
        email = "ada@example.com",
        displayName = "Ada Lovelace",
        position = "Designer",
        status = UserStatus.Active,
        role = MemberRole.Admin,
    )

    /** A member who is gone but whose row was kept, which is the whole reason the tab exists. */
    private val alan = User(
        id = UserID("00000000-0000-4000-8000-000000000002"),
        email = "alan@example.com",
        displayName = "Alan Turing",
        status = UserStatus.Deleted,
    )

    /**
     * The chevron is the way out, and it asks the shell to take it.
     *
     * This is the one control that must exist on *every* host rather than only the two with a
     * system back: on the desktop window there is no button and no edge swipe, re-selecting the
     * active tab is a no-op, so a pushed screen that drew no back control is a screen the desktop
     * cannot leave. The assertion is therefore that the control is in the tree and that tapping it
     * dispatches the shell's command — the shell owns which route pops; this screen only asks.
     */
    val theChevronAsksTheShellToGoBack: suspend ComposeUiTest.() -> Unit = {
        var backs = 0
        screenShowing(members = listOf(ada), onBack = { backs++ })

        onNodeWithTag(TTGlassBackButtonTag).assertIsDisplayed()
        onNodeWithTag(TTGlassBackButtonTag).performClick()

        assertEquals(1, backs, "the back control must ask the shell to leave, on every host")
    }

    /**
     * The empty active bucket, which is what an administrator with no colleagues yet sees.
     *
     * The absent list is the assertion: an empty list is *replaced* by the empty state rather
     * than left as a `LazyColumn` with nothing in it, so a screen that always drew the list would
     * show a blank page with no explanation.
     */
    val anEmptyActiveBucketExplainsItself: suspend ComposeUiTest.() -> Unit = {
        screenShowing(members = emptyList())

        onNodeWithTag(MembersAdminScreenTag).assertIsDisplayed()
        onNodeWithTag(MembersAddTag).assertIsDisplayed()
        onNodeWithTag(MembersEmptyTag).assertIsDisplayed()
        onNodeWithTag(MembersListTag).assertDoesNotExist()
    }

    /**
     * A live member's row offers the trash, and must not offer reactivate.
     *
     * Both halves matter. The positive half is the feature; the negative half is what fails when
     * the action branch is inverted, because an active member would then show a Reactivate button
     * that does nothing the server wants.
     */
    val anActiveRowOffersTheTrashAndNotTheReactivate: suspend ComposeUiTest.() -> Unit = {
        screenShowing(members = listOf(ada))

        onNodeWithTag(MembersListTag).assertIsDisplayed()
        onNodeWithTag(memberRowTag(ada.id)).assertIsDisplayed()
        onNodeWithTag(memberDeleteButtonTag(ada.id)).assertIsDisplayed()
        onNodeWithTag(memberReactivateButtonTag(ada.id)).assertDoesNotExist()
        onNodeWithTag(MembersEmptyTag).assertDoesNotExist()
    }

    /**
     * A deleted member's row offers reactivate, and must not offer the trash.
     *
     * The mirror of the scenario above, and the acceptance rule verbatim: in the Deleted bucket
     * the row "offers **reactivate** instead of delete". Deleting an already-deleted row would be
     * a second soft delete of a row that is already gone.
     */
    val aDeletedRowOffersReactivateAndNotTheTrash: suspend ComposeUiTest.() -> Unit = {
        screenShowing(bucket = MembersBucket.Deleted, members = listOf(alan))

        onNodeWithTag(memberRowTag(alan.id)).assertIsDisplayed()
        onNodeWithTag(memberReactivateButtonTag(alan.id)).assertIsDisplayed()
        onNodeWithTag(memberDeleteButtonTag(alan.id)).assertDoesNotExist()
    }

    /**
     * Tapping the trash raises the confirmation and sends nothing.
     *
     * This is the rule the confirm popup exists for, and it can only be checked here: the model's
     * `requestDelete` is trivially non-sending in isolation, but the *screen* is what decides that
     * the trash routes to it rather than straight to `confirmDelete`.
     */
    val theTrashRaisesAConfirmationAndSendsNothing: suspend ComposeUiTest.() -> Unit = {
        var requested: User? = null
        var confirms = 0
        screenShowing(
            members = listOf(ada),
            onRequestDelete = { requested = it },
            onConfirmDelete = { confirms++ },
        )

        onNodeWithTag(memberDeleteButtonTag(ada.id)).performClick()

        assertEquals(ada.id, requested?.id, "the trash must name the row it belongs to")
        assertEquals(0, confirms, "the trash must not delete: that is the confirmation's job")
    }

    /**
     * A raised confirmation names the member, and its confirm button dispatches once.
     *
     * The title is asserted by *content* rather than by tag, because the wording is the point:
     * "Delete Ada Lovelace?" is what tells the administrator which row the button will act on,
     * and a popup titled "Delete?" over a list of twenty people is a popup that has to be
     * dismissed to be read.
     */
    val theConfirmationNamesTheMemberAndDispatches: suspend ComposeUiTest.() -> Unit = {
        var confirms = 0
        var cancels = 0
        screenShowing(
            members = listOf(ada),
            pendingDeletion = ada,
            onConfirmDelete = { confirms++ },
            onCancelDelete = { cancels++ },
        )

        onNodeWithTag(TTConfirmPopupTag).assertIsDisplayed()
        onNodeWithText("Delete Ada Lovelace?").assertIsDisplayed()

        onNodeWithTag(TTConfirmPopupConfirmTag).performClick()

        assertEquals(1, confirms, "the confirmation dispatches its command exactly once")
        assertEquals(0, cancels)
    }

    /** The segment control asks for the bucket it names — the Deleted one, from the Active one. */
    val theDeletedSegmentAsksForTheDeletedBucket: suspend ComposeUiTest.() -> Unit = {
        var asked: MembersBucket? = null
        screenShowing(members = listOf(ada), onSelectBucket = { asked = it })

        onNodeWithTag(membersBucketTag(MembersBucket.Deleted)).performClick()

        assertEquals(
            MembersBucket.Deleted,
            asked,
            "the Deleted segment must ask for the deleted bucket, not for a status the port refuses",
        )
    }

    /**
     * The add form sends the address, the password, and the role that was chosen.
     *
     * The default role is asserted as part of the flow: the form opens on *Member*, and only the
     * deliberate tap on the Admin segment changes that. A form that opened on Admin would hand out
     * the privilege on a mis-tap, which is the one thing this screen must not do.
     */
    val theAddFormSendsWhatWasTyped: suspend ComposeUiTest.() -> Unit = {
        var email: String? = null
        var password: String? = null
        var role: MemberRole? = null
        screenShowing(
            members = emptyList(),
            isAddFormOpen = true,
            onCreate = { e, p, r, _ ->
                email = e
                password = p
                role = r
            },
        )

        onNodeWithTag(AddMemberDialogTag).assertIsDisplayed()
        onNodeWithTag(AddMemberEmailFieldTag).performTextInput("grace@example.com")
        onNodeWithTag(AddMemberPasswordFieldTag).performTextInput("correct horse battery")
        onNodeWithTag(addMemberRoleOptionTag(MemberRole.Admin)).performClick()
        onNodeWithTag(AddMemberCreateTag).performClick()

        assertEquals("grace@example.com", email)
        assertEquals("correct horse battery", password)
        assertEquals(MemberRole.Admin, role)
    }

    /**
     * The position dropdown opens, offers the vocabulary, and sends the chosen title.
     *
     * The dropdown's list only exists while it is expanded — it is a `Popup` — so this also pins
     * that the trigger opens something rather than being decorative. The value that leaves is the
     * *display name*: the server stores `position` as free text and the member list renders it
     * verbatim, so "Manager" is what the next administrator must see, not "manager".
     */
    val thePositionDropdownSendsTheChosenTitle: suspend ComposeUiTest.() -> Unit = {
        var position: String? = null
        screenShowing(
            members = emptyList(),
            isAddFormOpen = true,
            onCreate = { _, _, _, p -> position = p },
        )

        onNodeWithTag(AddMemberEmailFieldTag).performTextInput("grace@example.com")
        onNodeWithTag(AddMemberPasswordFieldTag).performTextInput("correct horse battery")
        onNodeWithTag(AddMemberPositionRowTag).performClick()
        onNodeWithTag(memberPositionOptionTag(UserPosition.Manager)).performClick()
        onNodeWithTag(AddMemberCreateTag).performClick()

        assertEquals("Manager", position)
    }

    /**
     * A failure is shown in the screen's own wording rather than re-worded.
     *
     * The string here is the shape of an [com.tasktracker.core.AppError] message, and the point is
     * that the screen prints it and does not paraphrase it: the same failure must not read
     * differently here and in a banner two screens away.
     */
    val aFailureIsShownAsItWasWorded: suspend ComposeUiTest.() -> Unit = {
        screenShowing(
            members = emptyList(),
            errorMessage = "That email is already registered.",
        )

        onNodeWithText("That email is already registered.").assertIsDisplayed()
    }
}

/**
 * Puts the screen on the test's stage in a theme, with every callback defaulted.
 *
 * The defaults are the point: a display-only scenario passes the state it is about and nothing
 * else, so the call site reads as the state rather than as twenty empty lambdas. A scenario that
 * *dispatches* passes only the counter it is watching.
 */
@OptIn(ExperimentalTestApi::class)
private fun ComposeUiTest.screenShowing(
    bucket: MembersBucket = MembersBucket.Active,
    members: List<User> = emptyList(),
    isLoading: Boolean = false,
    errorMessage: String? = null,
    pendingDeletion: User? = null,
    isAddFormOpen: Boolean = false,
    isCreating: Boolean = false,
    createErrorMessage: String? = null,
    onBack: () -> Unit = {},
    onSelectBucket: (MembersBucket) -> Unit = {},
    onRequestDelete: (User) -> Unit = {},
    onCancelDelete: () -> Unit = {},
    onConfirmDelete: () -> Unit = {},
    onReactivate: (User) -> Unit = {},
    onOpenAddForm: () -> Unit = {},
    onCloseAddForm: () -> Unit = {},
    onCreate: (String, String, MemberRole, String?) -> Unit = { _, _, _, _ -> },
    onDismissError: () -> Unit = {},
) {
    setContent {
        Themed {
            MembersAdminScreen(
                bucket = bucket,
                members = members,
                isLoading = isLoading,
                errorMessage = errorMessage,
                pendingDeletion = pendingDeletion,
                isAddFormOpen = isAddFormOpen,
                isCreating = isCreating,
                createErrorMessage = createErrorMessage,
                onBack = onBack,
                onSelectBucket = onSelectBucket,
                onRequestDelete = onRequestDelete,
                onCancelDelete = onCancelDelete,
                onConfirmDelete = onConfirmDelete,
                onReactivate = onReactivate,
                onOpenAddForm = onOpenAddForm,
                onCloseAddForm = onCloseAddForm,
                onCreate = onCreate,
                onDismissError = onDismissError,
            )
        }
    }
}

/**
 * The theme every scenario here is drawn in, so previews and tests agree.
 *
 * `content =` is named rather than positional: `ttTheme` takes `scheme` and an `accentPalette`
 * before its content, and a positional call would bind the lambda to the wrong parameter.
 */
@Composable
private fun Themed(content: @Composable () -> Unit) {
    ttTheme(ThemeScheme.Dark, content = content)
}
