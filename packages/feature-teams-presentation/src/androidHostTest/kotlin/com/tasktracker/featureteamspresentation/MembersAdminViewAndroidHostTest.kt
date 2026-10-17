package com.tasktracker.featureteamspresentation

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.v2.runComposeUiTest
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * [MembersAdminScenarios] on the Android host, under Robolectric.
 *
 * The counterpart to [MembersAdminViewJvmTest] and [MembersAdminViewIosTest]. Only the entry
 * points are here; every assertion lives in [MembersAdminScenarios].
 *
 * Robolectric only engages when a JUnit 4 runner is attached, and `@RunWith` cannot be written in
 * `commonTest` — which is why the entry points are split per host rather than shared.
 * [GraphicsMode.NATIVE] is what actually rasterises the composition; in Robolectric's legacy mode
 * nothing is drawn and any assertion depending on a node having been laid out fails on geometry
 * rather than on behaviour.
 */
@OptIn(ExperimentalTestApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class MembersAdminViewAndroidHostTest {

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
