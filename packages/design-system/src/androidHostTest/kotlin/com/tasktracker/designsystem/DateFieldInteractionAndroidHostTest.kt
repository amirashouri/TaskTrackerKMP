package com.tasktracker.designsystem

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.v2.runComposeUiTest
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * [DateFieldInteractionScenarios] on the Android host, under Robolectric.
 *
 * These are popup interactions and semantics-tree geometry, not ink, so this host can verify
 * all thirteen. The two density-in-window scenarios that need a second composition root stay
 * in [DateFieldPopupLayoutTest] and remain gated on the hosts that can compose a popup twice.
 */
@OptIn(ExperimentalTestApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class DateFieldInteractionAndroidHostTest {

    @Test
    fun aFieldWithNoValueAndNoTodayOpensOnTheCurrentMonth() =
        runComposeUiTest(block = DateFieldInteractionScenarios.aFieldWithNoValueAndNoTodayOpensOnTheCurrentMonth)

    @Test
    fun theMonthArrowsMoveTheGridWhenThereIsNoValueAndNoToday() =
        runComposeUiTest(block = DateFieldInteractionScenarios.theMonthArrowsMoveTheGridWhenThereIsNoValueAndNoToday)

    @Test
    fun thePreviousMonthArrowGoesBack() =
        runComposeUiTest(block = DateFieldInteractionScenarios.thePreviousMonthArrowGoesBack)

    @Test
    fun tappingTheMonthAndYearOpensTheYearsAndHidesTheDays() =
        runComposeUiTest(block = DateFieldInteractionScenarios.tappingTheMonthAndYearOpensTheYearsAndHidesTheDays)

    @Test
    fun pickingAYearMovesTheGridToThatYearAndBackToTheDays() =
        runComposeUiTest(block = DateFieldInteractionScenarios.pickingAYearMovesTheGridToThatYearAndBackToTheDays)

    @Test
    fun choosingAYearKeepsTheMonth() =
        runComposeUiTest(block = DateFieldInteractionScenarios.choosingAYearKeepsTheMonth)

    @Test
    fun theYearsOnOfferAreTheWindowAndNotTheWholeCalendar() =
        runComposeUiTest(block = DateFieldInteractionScenarios.theYearsOnOfferAreTheWindowAndNotTheWholeCalendar)

    @Test
    fun aFieldWithAValueOpensOnTheValue() =
        runComposeUiTest(block = DateFieldInteractionScenarios.aFieldWithAValueOpensOnTheValue)

    @Test
    fun theYearScreenOccupiesTheSameSpaceAsTheCalendar() =
        runComposeUiTest(block = DateFieldInteractionScenarios.theYearScreenOccupiesTheSameSpaceAsTheCalendar)

    @Test
    fun thePopupIsTheWidthOfTheCalendarAndNotTheWidthOfTheWindow() =
        runComposeUiTest(block = DateFieldInteractionScenarios.thePopupIsTheWidthOfTheCalendarAndNotTheWidthOfTheWindow)

    @Test
    fun aDayCellIsStill44dpWideInsideTheFixedWidthPopup() =
        runComposeUiTest(block = DateFieldInteractionScenarios.aDayCellIsStill44dpWideInsideTheFixedWidthPopup)

    @Test
    fun doneClosesThePickerWithoutChoosingADate() =
        runComposeUiTest(block = DateFieldInteractionScenarios.doneClosesThePickerWithoutChoosingADate)

    @Test
    fun doneIsOfferedOnTheYearScreenAsWell() =
        runComposeUiTest(block = DateFieldInteractionScenarios.doneIsOfferedOnTheYearScreenAsWell)
}
