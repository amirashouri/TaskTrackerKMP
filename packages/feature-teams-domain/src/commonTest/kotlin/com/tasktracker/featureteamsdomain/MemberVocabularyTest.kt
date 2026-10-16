package com.tasktracker.featureteamsdomain

import com.tasktracker.core.JSONCoding
import com.tasktracker.core.randomUserID
import kotlin.test.Test
import kotlin.test.assertEquals

class MemberVocabularyTest {

    @Test
    fun theUserStatusWireVocabularyMatchesTheDirectoryApi() {
        // The server's column carries exactly these three strings (entities.StatusActive,
        // StatusInactive, StatusDeleted). Pinned literally, because a round-trip against our
        // own wireValue would stay green through a typo in the spelling itself.
        assertEquals(
            listOf("active", "inactive", "deleted"),
            UserStatus.entries.map { it.wireValue },
        )
        assertEquals(UserStatus.Active, UserStatus.fromWireValue("active"))
        assertEquals(UserStatus.Inactive, UserStatus.fromWireValue("inactive"))
        assertEquals(UserStatus.Deleted, UserStatus.fromWireValue("deleted"))
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
    fun theMemberRoleWireVocabularyMatchesTheDirectoryApi() {
        // The server accepts exactly these two role strings (entities.RoleAdmin, RoleMember).
        // Pinned literally for the same reason the status vocabulary is.
        assertEquals(
            listOf("admin", "member"),
            MemberRole.entries.map { it.wireValue },
        )
        assertEquals(MemberRole.Admin, MemberRole.fromWire("admin"))
        assertEquals(MemberRole.Member, MemberRole.fromWire("member"))
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
