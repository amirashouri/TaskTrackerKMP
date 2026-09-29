package com.tasktracker.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Instant

class JWTTest {

    private val header = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9"
    private val payload =
        "eyJlbWFpbCI6ImFtaXJAZXhhbXBsZS5jb20iLCJleHAiOjE3NjAwMDAwMDAsImlhdCI6MTc1OTk5OTYwMCwianRpIjoidG9rLTEiLCJzdWIiOiIwMTk1NmYwMC0xMDAwLTEwMDAtMDAwMC0wMDAwMDAwMDAwMDAiLCJ0eXAiOiJhY2Nlc3MifQ"
    private val valid = "$header.$payload.c2lnbmF0dXJl"

    @Test
    fun parsesAllStandardClaims() {
        val claims = assertNotNull(JWT.parse(valid))
        assertEquals("01956f00-1000-1000-0000-000000000000", claims.subject)
        assertEquals("amir@example.com", claims.email)
        assertEquals("access", claims.tokenType)
        assertEquals("tok-1", claims.tokenID)
        assertEquals(Instant.fromEpochSeconds(1_760_000_000), claims.expiry)
        assertEquals(Instant.fromEpochSeconds(1_759_999_600), claims.issuedAt)
    }

    @Test
    fun rejectsStructurallyInvalidTokensInsteadOfThrowing() {
        // The whole reason `parse` returns null: a malformed token comes from a
        // keychain or a server, and throwing here would turn "please sign in again"
        // into an unhandled crash.
        assertNull(JWT.parse(""))
        assertNull(JWT.parse("only.two"))
        assertNull(JWT.parse("a.!!!not-base64!!!.c"))
    }

    @Test
    fun aTokenWithoutExpYieldsNullExpiryNotACrash() {
        assertNull(JWT.expiry("$header.eyJzdWIiOiJhYmMifQ.sig"))
    }

    @Test
    fun expAlsoParsesWhenTheServerSendsItAsAString() {
        val encoded = JWT.encodeBase64URL("""{"sub":"a","exp":"1760000000"}""".encodeToByteArray())
        assertEquals(Instant.fromEpochSeconds(1_760_000_000), JWT.expiry("$header.$encoded.s"))
    }

    @Test
    fun fractionalExpIsRejectedRatherThanTruncated() {
        // A fractional `exp` is not a thing. Truncating it into a plausible instant
        // would make a malformed token look valid for a whole extra second.
        val encoded = JWT.encodeBase64URL("""{"sub":"a","exp":"1760000000.5"}""".encodeToByteArray())
        assertNull(JWT.expiry("$header.$encoded.s"))
    }

    @Test
    fun base64URLRoundTripsWithoutPadding() {
        val data = byteArrayOf(0xFB.toByte(), 0xFF.toByte(), 0xBE.toByte(), 1, 2, 3)
        val encoded = JWT.encodeBase64URL(data)
        assertFalse('=' in encoded)
        assertFalse('+' in encoded)
        assertFalse('/' in encoded)
        assertTrue(JWT.decodeBase64URL(encoded)!!.contentEquals(data))
    }
}

class DateCodingTest {

    @Test
    fun wireFormatIsWholeSecondUtc() {
        assertEquals(
            "2025-10-09T08:53:20Z",
            DateCoding.format(Instant.fromEpochSeconds(1_760_000_000)),
        )
    }

    @Test
    fun wholeSecondValuesRoundTripExactly() {
        for (seconds in listOf(0L, 1_760_000_000L, 2_000_000_000L)) {
            val value = Instant.fromEpochSeconds(seconds)
            assertEquals(value, DateCoding.parse(DateCoding.format(value)))
        }
    }

    @Test
    fun roundingMakesAValueAFaithfulInverseOfTheWireFormat() {
        val precise = Instant.fromEpochSeconds(1_760_000_000, 123_000_000)
        val rounded = precise.roundedToSecond()
        assertEquals(rounded, DateCoding.parse(DateCoding.format(rounded)))
        // The reason rounding exists: without it the value does NOT survive.
        assertFalse(precise == DateCoding.parse(DateCoding.format(precise)))
    }

    @Test
    fun acceptsFractionalSecondsFromAServerThatSendsThem() {
        assertEquals(
            Instant.fromEpochSeconds(1_760_000_000, 500_000_000),
            DateCoding.parse("2025-10-09T08:53:20.500Z"),
        )
    }

    @Test
    fun acceptsAnOffsetInPlaceOfTheZDesignator() {
        assertEquals(
            Instant.fromEpochSeconds(1_760_000_000),
            DateCoding.parse("2025-10-09T09:53:20+01:00"),
        )
    }

    @Test
    fun rejectsUnparseableInput() {
        assertNull(DateCoding.parse("not-a-date"))
        assertNull(DateCoding.parse(""))
    }
}

class JSONCodingTest {

    @kotlinx.serialization.Serializable
    private data class Payload(
        val name: String,
        // The annotation is not optional. Without it kotlinx silently uses its own
        // `Instant` serializer, which emits fractional seconds and quietly breaks
        // the whole-second wire format.
        @kotlinx.serialization.Serializable(with = InstantWireSerializer::class)
        val at: Instant,
    )

    @Test
    fun sharedCodersAgreeOnTheWireFormat() {
        val payload = Payload("TT", Instant.fromEpochSeconds(1_760_000_000).roundedToSecond())
        val text = JSONCoding.json.encodeToString(Payload.serializer(), payload)
        assertEquals(
            """{"name":"TT","at":"2025-10-09T08:53:20Z"}""",
            text,
        )
        assertEquals(
            payload,
            JSONCoding.json.decodeFromString(Payload.serializer(), text),
        )
    }

    @Test
    fun decodingSurfacesAnAppErrorForABadDate() {
        // The point of the custom serializer throwing `AppError` rather than a bare
        // `SerializationException`: a bad date in a server payload must arrive at the
        // data layer as the same type as every other failure, so one `catch` covers
        // the boundary.
        val failure = assertFailsWith<AppError> {
            JSONCoding.json.decodeFromString(Payload.serializer(), """{"name":"TT","at":"yesterday"}""")
        }
        assertTrue(failure is AppError.Decoding)
    }

    @Test
    fun unknownKeysAreAnErrorRatherThanASilentlyNullField() {
        // `ignoreUnknownKeys` is off on purpose. A field renamed on the server has to
        // fail at the boundary; decoding it as null turns a protocol change into a
        // field that is null on every device.
        assertFailsWith<kotlinx.serialization.SerializationException> {
            JSONCoding.json.decodeFromString(Payload.serializer(), """{"name":"TT"}""")
        }
    }
}

class AppErrorTest {

    @Test
    fun classifiesRetryability() {
        assertTrue(AppError.Offline.isRetryable)
        assertTrue(AppError.Timeout.isRetryable)
        assertTrue(AppError.Server(503, "").isRetryable)
        assertFalse(AppError.Forbidden.isRetryable)
        assertFalse(AppError.Conflict("task").isRetryable)
    }

    @Test
    fun onlyAuthErrorsForceASignOut() {
        assertTrue(AppError.Unauthorized.requiresSignOut)
        assertTrue(AppError.Forbidden.requiresSignOut)
        assertFalse(AppError.Offline.requiresSignOut)
        assertFalse(AppError.Server(500, "").requiresSignOut)
    }

    @Test
    fun connectivityFailuresAreDistinguishableFromLogicalOnes() {
        assertTrue(AppError.Transport("no route").isConnectivityFailure)
        assertFalse(AppError.Validation("empty title").isConnectivityFailure)
    }

    @Test
    fun everyCaseHasUserFacingText() {
        val all = listOf(
            AppError.Offline, AppError.Transport("x"), AppError.Timeout,
            AppError.Unauthorized, AppError.Forbidden, AppError.Server(500, "boom"),
            AppError.Decoding("x"), AppError.Conflict("task"), AppError.Validation("x"),
            AppError.Cancelled, AppError.Storage("x"), AppError.Unknown("x"),
        )
        assertTrue(all.all { !it.message.isNullOrBlank() })
    }

    @Test
    fun everyCaseHasADistinctLogForm() {
        val all = listOf(
            AppError.Offline, AppError.Transport("x"), AppError.Timeout,
            AppError.Unauthorized, AppError.Forbidden, AppError.Server(500, "boom"),
            AppError.Decoding("x"), AppError.Conflict("task"), AppError.Validation("x"),
            AppError.Cancelled, AppError.Storage("x"), AppError.Unknown("x"),
        )
        // Duplicates in a log form would make two different failures indistinguishable
        // when grepping, which is the only thing the log form is for.
        assertEquals(all.size, all.map { it.logText }.toSet().size)
    }

    @Test
    fun visibleTextAndLogFormAreDifferentShapes() {
        // The reason `logText` exists at all: `Throwable.toString()` would put the
        // user-facing sentence in the log, where a compact token is wanted.
        assertEquals("transport(no route)", AppError.Transport("no route").logText)
        assertEquals("Could not reach the server. no route", AppError.Transport("no route").message)
    }
}

class TestDateProviderTest {

    @Test
    fun advancesOnlyWhenAsked() = kotlinx.coroutines.test.runTest {
        val clock = TestDateProvider(Instant.fromEpochSeconds(100))
        assertEquals(Instant.fromEpochSeconds(100), clock.now())
        clock.advance(50)
        assertEquals(Instant.fromEpochSeconds(150), clock.now())
        clock.set(Instant.fromEpochSeconds(0))
        assertEquals(Instant.fromEpochSeconds(0), clock.now())
    }
}
