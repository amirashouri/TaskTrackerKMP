package com.tasktracker.featureteamspresentation

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.v2.runComposeUiTest
import kotlin.test.Test

/**
 * [MembersAdminScenarios] on the JVM.
 *
 * The JVM host is the fast loop for this screen: the same nine scenarios run on the iOS simulator
 * ([MembersAdminViewIosTest]) and the Android host ([MembersAdminViewAndroidHostTest]), and this
 * one runs in seconds with no simulator.
 *
 * There is no `commonTest` entry point on purpose: a Compose UI test on the Android host needs
 * Robolectric, `@RunWith` is not expressible in `commonTest`, and a `commonTest` class runs on
 * *every* target — so one set of wrappers there would fail on the host even with the host's own
 * file beside it. Every assertion lives in [MembersAdminScenarios]; these are entry points only.
 *
 * [MemberAdminModelTest] is the other half of this module and needs no wrappers: it touches no
 * composition, so it runs as an ordinary `commonTest` class on all three targets.
 */
@OptIn(ExperimentalTestApi::class)
class MembersAdminViewJvmTest {

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
    fun theChevronAsksTheShellToGoBack() =
        runComposeUiTest(block = MembersAdminScenarios.theChevronAsksTheShellToGoBack)
}
