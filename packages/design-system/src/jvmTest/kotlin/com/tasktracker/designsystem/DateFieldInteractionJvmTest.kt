package com.tasktracker.designsystem

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.v2.runComposeUiTest
import kotlin.test.Test

/** [DateFieldInteractionScenarios] on the JVM. */
@OptIn(ExperimentalTestApi::class)
class DateFieldInteractionJvmTest {

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
