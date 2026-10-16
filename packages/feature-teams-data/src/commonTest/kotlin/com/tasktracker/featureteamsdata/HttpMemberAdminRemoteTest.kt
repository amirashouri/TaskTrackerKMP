package com.tasktracker.featureteamsdata

import com.tasktracker.core.AppError
import com.tasktracker.core.UserID
import com.tasktracker.featureteamsdomain.MemberRole
import com.tasktracker.featureteamsdomain.UserStatus
import com.tasktracker.network.HttpMethod
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Instant

/**
 * A fixed id, so a test can name the row the server echoed back.
 *
 * Lowercase, which is the form this layer puts on the wire: a path is built from
 * `UserID.stringValue`, and that is `Uuid.toString()`'s canonical rendering, not whatever casing
 * the server happened to send in the body.
 */
private const val MEMBER_ID: String = "3f2504e0-4f89-41d3-9a0c-0305e82c3301"

/**
 * The real admin remote's wire shape.
 *
 * `MemberAdminPort` is otherwise only ever exercised through fakes, and the properties worth
 * checking here are invisible in a response: the `status` query the list needs, `requiresAuth`,
 * and whether a create is replayable. The decode half is what the DTO's `@SerialName` list
 * exists for — `JSONCoding` leaves `ignoreUnknownKeys` off, so a server key this layer does not
 * name fails the whole list rather than being skipped.
 */
class HttpMemberAdminRemoteTest {

    private fun remote(client: RecordingHttpClient) = HttpMemberAdminRemote(client)

    /**
     * The server's `userResponse`, built key by key so a test can drop one and prove what the
     * DTO's declaration list is load-bearing for. Absent keys follow the server's `omitempty`.
     */
    private fun memberObject(
        id: String = MEMBER_ID,
        email: String = "ada@example.com",
        displayName: String? = "Ada",
        position: String? = "Lead",
        avatarId: String? = null,
        status: String? = "active",
        role: String? = "admin",
        lastSeen: Long? = 1_700_000_000_000L,
        createdAt: Long = 1_600_000_000_000L,
    ): JsonObject = buildJsonObject {
        put("id", id)
        put("email", email)
        if (displayName != null) put("display_name", displayName)
        if (position != null) put("position", position)
        if (avatarId != null) put("avatar_id", avatarId)
        if (status != null) put("status", status)
        if (role != null) put("role", role)
        if (lastSeen != null) put("last_seen", lastSeen)
        put("created_at", createdAt)
    }

    private fun memberArray(entries: List<JsonObject>): String =
        buildJsonArray { entries.forEach { add(it) } }.toString()

    // ── List ────────────────────────────────────────────────────────

    @Test
    fun listingTheActiveBucketAsksForActive() = runTest {
        val client = RecordingHttpClient()
        client.respondWith(memberArray(listOf(memberObject())))

        remote(client).members(UserStatus.Active)

        val request = assertNotNull(client.requests.firstOrNull())
        assertEquals("/v1/admin/users", request.path)
        assertEquals(HttpMethod.Get, request.method)
        assertEquals("active", request.queryItems.single { it.name == "status" }.value)
    }

    /**
     * The two buckets are the segmented control's whole vocabulary, and the deleted one is the
     * only way a removed member is visible at all — the default bucket deliberately omits them.
     */
    @Test
    fun listingTheDeletedBucketAsksForDeleted() = runTest {
        val client = RecordingHttpClient()
        client.respondWith(memberArray(emptyList()))

        remote(client).members(UserStatus.Deleted)

        assertEquals(
            "deleted",
            assertNotNull(client.requests.first()).queryItems.single { it.name == "status" }.value,
        )
    }

    /**
     * `inactive` is not a bucket: the server answers `400` for it, and mapping it to `active`
     * would return a plausible wrong list to a caller who asked for a specific set. The refusal
     * is asserted together with "nothing was sent", because a throw that still fired the request
     * would be the same defect wearing an exception.
     */
    @Test
    fun listingInactiveIsRefusedRatherThanForwarded() = runTest {
        val client = RecordingHttpClient()

        assertFailsWith<AppError.Validation> {
            remote(client).members(UserStatus.Inactive)
        }
        assertTrue(client.requests.isEmpty(), "an unserviceable bucket must not reach the wire")
    }

    @Test
    fun theMemberListDecodesIntoDomainRows() = runTest {
        val client = RecordingHttpClient()
        client.respondWith(
            memberArray(
                listOf(
                    memberObject(status = "active", role = "admin"),
                    memberObject(
                        id = "6b29fc40-ca47-1067-b31d-00dd010662da",
                        email = "grace@example.com",
                        displayName = "Grace",
                        position = null,
                        status = "inactive",
                        role = "member",
                        lastSeen = null,
                    ),
                ),
            ),
        )

        val members = remote(client).members(UserStatus.Active)

        assertEquals(2, members.size)
        assertEquals("ada@example.com", members[0].email)
        assertEquals(UserStatus.Active, members[0].status)
        assertEquals(MemberRole.Admin, members[0].role)
        // `inactive` is a state a live row carries, and an absent position/last_seen read as
        // their defaults rather than failing the decode.
        assertEquals(UserStatus.Inactive, members[1].status)
        assertEquals(MemberRole.Member, members[1].role)
        assertNull(members[1].position)
        assertNull(members[1].lastSeen)
    }

    /**
     * The three translations that a domain type could not make for itself: the snake_case keys,
     * the epoch-millis `last_seen` becoming an [Instant] (the sync envelope's ISO-8601 is a
     * different contract), and `created_at` — which the server always sends, the domain `User`
     * has no field for, and the strict decoder would reject if the DTO omitted it.
     */
    @Test
    fun aMemberPayloadCarriesTheServerKeysAndEpochMillis() = runTest {
        val client = RecordingHttpClient()
        client.respondWith(memberArray(listOf(memberObject(avatarId = "avatar-9", lastSeen = 1_700_000_000_000L))))

        val user = remote(client).members(UserStatus.Active).single()

        assertEquals(MEMBER_ID, user.id.stringValue)
        assertEquals("Ada", user.displayName)
        assertEquals("Lead", user.position)
        assertEquals("avatar-9", user.avatarId)
        assertEquals(Instant.fromEpochMilliseconds(1_700_000_000_000L), user.lastSeen)
    }

    @Test
    fun anUnknownStatusReadsAsActiveAndAnUnknownRoleAsMember() = runTest {
        val client = RecordingHttpClient()
        client.respondWith(memberArray(listOf(memberObject(status = "suspended", role = "owner"))))

        val user = remote(client).members(UserStatus.Active).single()

        assertEquals(UserStatus.Active, user.status)
        assertEquals(MemberRole.Member, user.role)
    }

    /**
     * The strictness the DTO's `@SerialName` list answers to, asserted from the side that owns
     * it: a key this layer does not name is a decoding failure rather than a value it skips.
     */
    @Test
    fun anUndeclaredKeyInAMemberPayloadIsADecodingFailure() = runTest {
        val client = RecordingHttpClient()
        client.respondWith("""[{"id":"$MEMBER_ID","email":"a@b.c","tenant_id":"acme","created_at":1}]""")

        assertFailsWith<AppError.Decoding> {
            remote(client).members(UserStatus.Active)
        }
    }

    // ── Create ──────────────────────────────────────────────────────

    @Test
    fun creatingPostsTheAddressPasswordRoleAndPosition() = runTest {
        val client = RecordingHttpClient()
        client.respondWith(memberObject().toString())

        remote(client).createMember(
            email = "ada@example.com",
            password = "hunter2",
            role = MemberRole.Admin,
            position = "Lead",
        )

        val request = assertNotNull(client.requests.first())
        assertEquals("/v1/admin/users", request.path)
        assertEquals(HttpMethod.Post, request.method)
        val body = assertNotNull(request.body).decodeToString()
        assertTrue(body.contains("\"email\":\"ada@example.com\""), body)
        assertTrue(body.contains("\"password\":\"hunter2\""), body)
        assertTrue(body.contains("\"role\":\"admin\""), body)
        assertTrue(body.contains("\"position\":\"Lead\""), body)
        assertFalse(body.contains("display_name"), "the create form sends no display name")
    }

    /**
     * An absent position reaches the server the way its own `omitempty` expects — as no key,
     * not as a null the handler has to read as absence.
     */
    @Test
    fun anAbsentPositionIsOmittedFromTheCreateBody() = runTest {
        val client = RecordingHttpClient()
        client.respondWith(memberObject().toString())

        remote(client).createMember(
            email = "ada@example.com",
            password = "hunter2",
            role = MemberRole.Member,
            position = null,
        )

        val body = assertNotNull(assertNotNull(client.requests.first()).body).decodeToString()
        assertFalse(body.contains("position"), body)
    }

    /**
     * A create that lost its response and was replayed either registers a second account or is
     * refused for the email already being taken. `POST` is not replayable by default and must
     * stay that way.
     */
    @Test
    fun creatingRefusesToBeReplayed() = runTest {
        val client = RecordingHttpClient()
        client.respondWith(memberObject().toString())

        remote(client).createMember("ada@example.com", "hunter2", MemberRole.Member, null)

        assertFalse(assertNotNull(client.requests.first()).effectiveIdempotency)
    }

    @Test
    fun creatingDecodesTheReturnedMember() = runTest {
        val client = RecordingHttpClient()
        client.respondWith(memberObject(role = "admin", status = "active").toString())

        val created = remote(client).createMember("ada@example.com", "hunter2", MemberRole.Admin, "Lead")

        assertEquals("ada@example.com", created.email)
        assertEquals(MemberRole.Admin, created.role)
        assertEquals(UserStatus.Active, created.status)
    }

    // ── Delete ──────────────────────────────────────────────────────

    @Test
    fun deletingSendsNoBodyToTheMemberPath() = runTest {
        val client = RecordingHttpClient()
        client.respondWith(ByteArray(0))

        remote(client).softDeleteMember(UserID(MEMBER_ID))

        val request = assertNotNull(client.requests.first())
        assertEquals("/v1/admin/users/$MEMBER_ID", request.path)
        assertEquals(HttpMethod.Delete, request.method)
        assertNull(request.body)
    }

    /** A soft delete is idempotent: the server answers `204` whether the row was live or not. */
    @Test
    fun deletingIsReplayable() = runTest {
        val client = RecordingHttpClient()

        remote(client).softDeleteMember(UserID(MEMBER_ID))

        assertTrue(assertNotNull(client.requests.first()).effectiveIdempotency)
    }

    // ── Reactivate ──────────────────────────────────────────────────

    @Test
    fun reactivatingPatchesTheStatusToActive() = runTest {
        val client = RecordingHttpClient()
        client.respondWith(memberObject(status = "active").toString())

        remote(client).reactivateMember(UserID(MEMBER_ID))

        val request = assertNotNull(client.requests.first())
        assertEquals("/v1/admin/users/$MEMBER_ID", request.path)
        assertEquals(HttpMethod.Patch, request.method)
        assertEquals("""{"status":"active"}""", assertNotNull(request.body).decodeToString())
    }

    /**
     * Setting the status to `active` twice leaves the same row standing, so the verb's default
     * of "not replayable" is overridden: the only thing it would buy is a dropped connection
     * read as a failed reactivation.
     */
    @Test
    fun reactivatingIsReplayable() = runTest {
        val client = RecordingHttpClient()
        client.respondWith(memberObject(status = "active").toString())

        remote(client).reactivateMember(UserID(MEMBER_ID))

        assertTrue(assertNotNull(client.requests.first()).effectiveIdempotency)
    }

    @Test
    fun reactivatingDecodesTheReturnedMember() = runTest {
        val client = RecordingHttpClient()
        client.respondWith(memberObject(status = "active", role = "member").toString())

        val reactivated = remote(client).reactivateMember(UserID(MEMBER_ID))

        assertEquals(UserStatus.Active, reactivated.status)
        assertEquals("ada@example.com", reactivated.email)
    }

    // ── Every call ──────────────────────────────────────────────────

    /**
     * Every admin endpoint is behind the server's admin middleware, so every request must carry
     * a bearer token. `requiresAuth = true` is what routes it through `AuthInterceptor`; a call
     * that left it at the default would still be `true`, but one that set it `false` would go out
     * bare and answer `401` with no refresh — invisible in every other assertion here.
     */
    @Test
    fun everyAdminCallRequiresAuthentication() = runTest {
        val client = RecordingHttpClient()
        // The four calls answer different shapes: a list is an array, a write is one object, and
        // a delete is an empty `204`. Scripted in call order.
        client.respondWith("[]")
        client.respondWith(memberObject().toString())
        client.respondWith(ByteArray(0))
        client.respondWith(memberObject().toString())

        val target = UserID(MEMBER_ID)
        val admin = remote(client)
        admin.members(UserStatus.Active)
        admin.createMember("ada@example.com", "hunter2", MemberRole.Member, null)
        admin.softDeleteMember(target)
        admin.reactivateMember(target)

        assertEquals(4, client.requests.size)
        assertTrue(client.requests.all { it.requiresAuth })
    }
}
