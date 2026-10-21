package com.tasktracker.featuresettingsdomain

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * The two vocabularies the profile crosses.
 *
 * The server stores a human label for `position` and a lower-case snake token for `presence`;
 * the client persists an enum *name* for presence. Three spellings for one idea is exactly the
 * situation where a "harmless" reuse of a parser turns an away user into an active one, so each
 * parser is pinned against the input the other one would receive.
 */
class UserDetailsMappingTest {

    @Test
    fun aPositionLabelRoundTripsCaseInsensitivelyAndToleratesSpace() {
        assertEquals(UserPosition.ProductOwner, UserPosition.fromLabel("Product owner"))
        assertEquals(UserPosition.ProductOwner, UserPosition.fromLabel("  product OWNER "))
        assertEquals(UserPosition.DataEngineer, UserPosition.fromLabel("data engineer"))
    }

    @Test
    fun aPositionWireValueStillParsesAsALabel() {
        // The admin form writes the label, but a seed or a hand-written row may carry the
        // internal spelling. Accepting both is what makes the mapping total over the values the
        // server can actually hold.
        assertEquals(UserPosition.ProductOwner, UserPosition.fromLabel("product_owner"))
    }

    @Test
    fun freeTextPositionIsNotRepresentableRatherThanEmpty() {
        // `null` means "the picker has no selection", which is different from "the user cleared
        // the position". The write path only sends a position that changed, so a free-text row
        // survives an unrelated name edit.
        assertNull(UserPosition.fromLabel("Senior Designer"))
        assertNull(UserPosition.fromLabel(""))
        assertNull(UserPosition.fromLabel("   "))
        assertNull(UserPosition.fromLabel(null))
    }

    @Test
    fun presenceIsParsedFromTheServerToken() {
        assertEquals(UserStatus.Active, UserStatus.fromPresence("active"))
        assertEquals(UserStatus.Away, UserStatus.fromPresence("away"))
        assertEquals(UserStatus.BeRightBack, UserStatus.fromPresence("be_right_back"))
        assertEquals(UserStatus.Busy, UserStatus.fromPresence("busy"))
        assertEquals(UserStatus.Away, UserStatus.fromPresence(" away "))
    }

    @Test
    fun anUnknownPresenceFallsBackToActiveRatherThanFailingTheWholeProfile() {
        assertEquals(UserStatus.Active, UserStatus.fromPresence("sleeping"))
        assertEquals(UserStatus.Active, UserStatus.fromPresence(null))
    }

    @Test
    fun thePersistedParserStillReadsEnumNamesNotServerTokens() {
        // Existing devices have `"BeRightBack"` in `DefaultsKey.UserProfile`. The pairing below
        // is the reason `fromWireValue` was not repurposed: reading the same stored value with
        // the server's vocabulary would silently turn every away user active on upgrade.
        assertEquals(UserStatus.BeRightBack, UserStatus.fromWireValue("BeRightBack"))
        assertEquals(UserStatus.Active, UserStatus.fromWireValue("be_right_back"))
    }
}
