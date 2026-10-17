package com.tasktracker.featureteamspresentation

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.v2.runComposeUiTest
import kotlin.test.Test

/**
 * [MembersAdminScenarios] on the iOS simulator.
 *
 * Identical entry points to [MembersAdminViewJvmTest] and [MembersAdminViewAndroidHostTest], and
 * the reason they are three files rather than one in `commonTest` is the Android host's
 * Robolectric runner, which `commonTest` cannot carry — see [MembersAdminViewJvmTest].
 */
@OptIn(ExperimentalTestApi::class)
class MembersAdminViewIosTest {

    @Test
    fun anEmptyActiveBucketExplainsItself() =
        runComposeUiTest(block = MembersAdminScenarios.anEmptyActiveBucketExplainsItself)

    @Test
    fun anActiveRowOffersTheTrashAndNotTheReactivate() =
        runComposeUiTest(block = MembersAdminScenarios.anActiveRowOffersTheTrashAndNotTheReactivate)

    @Test
    fun aDeletedRowOffersReactivateAndNotTheTrash() =
        runComposeUiTest(block = MembersAdminScenarios.aDeletedRowOffersReactivateAndNotTheTrash)

    @Test
    fun theTrashRaisesAConfirmationAndSendsNothing() =
        runComposeUiTest(block = MembersAdminScenarios.theTrashRaisesAConfirmationAndSendsNothing)

    @Test
    fun theConfirmationNamesTheMemberAndDispatches() =
        runComposeUiTest(block = MembersAdminScenarios.theConfirmationNamesTheMemberAndDispatches)

    @Test
    fun theDeletedSegmentAsksForTheDeletedBucket() =
        runComposeUiTest(block = MembersAdminScenarios.theDeletedSegmentAsksForTheDeletedBucket)

    @Test
    fun theAddFormSendsWhatWasTyped() =
        runComposeUiTest(block = MembersAdminScenarios.theAddFormSendsWhatWasTyped)

    @Test
    fun thePositionDropdownSendsTheChosenTitle() =
        runComposeUiTest(block = MembersAdminScenarios.thePositionDropdownSendsTheChosenTitle)

    @Test
    fun aFailureIsShownAsItWasWorded() =
        runComposeUiTest(block = MembersAdminScenarios.aFailureIsShownAsItWasWorded)

    @Test
    fun aListWithTheFormBesideItOffersNoAddButton() =
        runComposeUiTest(block = MembersAdminScenarios.aListWithTheFormBesideItOffersNoAddButton)

    @Test
    fun theOverlayFormOffersCancelAndAsksForIt() =
        runComposeUiTest(block = MembersAdminScenarios.theOverlayFormOffersCancelAndAsksForIt)

    @Test
    fun aFormWithNothingToDismissDrawsNoCancel() =
        runComposeUiTest(block = MembersAdminScenarios.aFormWithNothingToDismissDrawsNoCancel)

    @Test
    fun theChevronAsksTheShellToGoBack() =
        runComposeUiTest(block = MembersAdminScenarios.theChevronAsksTheShellToGoBack)
}
