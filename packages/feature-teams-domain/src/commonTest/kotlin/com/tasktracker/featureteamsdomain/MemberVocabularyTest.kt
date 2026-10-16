package com.tasktracker.featureteamsdomain

import com.tasktracker.core.JSONCoding
import com.tasktracker.core.randomUserID
import kotlin.test.Test
import kotlin.test.assertEquals

class MemberVocabularyTest {

    @Test
    fun everyUserStatusRoundTripsItsWireValue() {
        for (status in UserStatus.entries) {
            assertEquals(
                status,
                UserStatus.fromWireValue(status.wireValue),
                "UserStatus.${status.name} must round-trip through its wire value",
            )
        }
    }

    @Test
    fun anUnknownUserStatusReadsAsActive() {
        assertEquals(UserStatus.Active, UserStatus.fromWireValue("suspended"))
        assertEquals(UserStatus.Active, UserStatus.fromWireValue(null))
    }

    @Test
    fun userStatusKeepsItsKotlinNameAsItsSerializedForm() {
        // A persisted User row stores this enum by Kotlin name; the HTTP spelling lives in
        // wireValue. Moving the wire spelling onto a @SerialName would silently change the
        // on-device format of every stored directory entry, so the two are pinned apart here.
        assertEquals(
            "\"Active\"",
            JSONCoding.json.encodeToString(UserStatus.serializer(), UserStatus.Active),
        )
        assertEquals(
            "\"Deleted\"",
            JSONCoding.json.encodeToString(UserStatus.serializer(), UserStatus.Deleted),
        )
    }

    @Test
    fun everyMemberRoleRoundTripsItsWireValue() {
        for (role in MemberRole.entries) {
            assertEquals(
                role,
                MemberRole.fromWire(role.wireValue),
                "MemberRole.${role.name} must round-trip through its wire value",
            )
        }
    }

    @Test
    fun anUnknownMemberRoleReadsAsMember() {
        assertEquals(MemberRole.Member, MemberRole.fromWire("owner"))
        assertEquals(MemberRole.Member, MemberRole.fromWire(null))
    }

    @Test
    fun aUserWithoutARoleDecodesAsMember() {
        // A cached row written before the role field existed, or a payload that omits it,
        // must still decode — as the lowest privilege rather than as a failure.
        val json = """{"id":"${randomUserID()}","email":"amir@example.com"}"""

        val user = JSONCoding.json.decodeFromString(User.serializer(), json)

        assertEquals(MemberRole.Member, user.role)
    }

    @Test
    fun aUserRoleRoundTripsThroughJson() {
        val original = User(
            id = randomUserID(),
            email = "amir@example.com",
            role = MemberRole.Admin,
        )

        val restored = JSONCoding.json.decodeFromString(
            User.serializer(),
            JSONCoding.json.encodeToString(User.serializer(), original),
        )

        assertEquals(MemberRole.Admin, restored.role)
        assertEquals(original, restored)
    }
}
