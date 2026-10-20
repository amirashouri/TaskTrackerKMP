package com.tasktracker.featuresettingsdomain

import com.tasktracker.featuresettingsdomain.ServerAddressValidation.Reason
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * The address table.
 *
 * Every case is a value in and a value out, so the whole table runs on every host. The
 * cleartext cases are the security boundary: a public host over `http` must never be saved,
 * while the default `lan` provider's private address must be.
 */
class ServerAddressTest {

    @Test
    fun httpsIsAcceptedForAnyReadableHost() {
        assertValid("https://example.com", "https://example.com")
        assertValid("https://example.com:8443", "https://example.com:8443")
        assertValid("https://example.com/api", "https://example.com/api")
        assertValid("https://192.168.1.5", "https://192.168.1.5")
        assertValid("https://[2001:db8::1]:8443", "https://[2001:db8::1]:8443")
    }

    @Test
    fun cleartextIsAcceptedOnTheLocalNetwork() {
        val local = listOf(
            "http://localhost:8080",
            "http://127.0.0.1:8080",
            "http://127.1.2.3",
            "http://10.0.2.2:8080",
            "http://10.1.2.3",
            "http://172.16.0.1",
            "http://172.31.255.254",
            "http://192.168.1.5:8080",
            "http://100.64.0.1",
            "http://100.127.255.254",
            "http://169.254.1.1",
            "http://nas",
            "http://nas.local:8080",
            "http://[::1]:8080",
            "http://[fe80::1]",
            "http://[fd00::1]",
            "http://[fc00::1]",
        )

        for (address in local) {
            assertEquals(
                ServerAddressValidation.Valid(address.trimEnd('/')),
                validateServerAddress(address),
                "expected $address to be a local cleartext address",
            )
        }
    }

    @Test
    fun cleartextIsRefusedOnAPublicHost() {
        val public = listOf(
            "http://example.com",
            "http://8.8.8.8",
            "http://172.32.0.1",
            "http://192.169.1.1",
            "http://100.128.0.1",
            "http://[2001:db8::1]",
        )

        for (address in public) {
            assertInvalid(Reason.CleartextNotPermitted, address)
        }
    }

    @Test
    fun blankInputIsItsOwnReason() {
        assertInvalid(Reason.Blank, "")
        assertInvalid(Reason.Blank, "   ")
        assertInvalid(Reason.Blank, "\n\t")
    }

    @Test
    fun aMissingOrUnsupportedSchemeIsRefused() {
        assertInvalid(Reason.MissingScheme, "example.com:8080")
        assertInvalid(Reason.MissingScheme, "://example.com")
        assertInvalid(Reason.MissingScheme, "https:/example.com")
        assertInvalid(Reason.UnsupportedScheme, "ftp://example.com")
        assertInvalid(Reason.UnsupportedScheme, "ssh://example.com")
    }

    @Test
    fun aUrlWithNoHostIsRefused() {
        assertInvalid(Reason.MissingHost, "https://")
        assertInvalid(Reason.MissingHost, "https:///path")
        assertInvalid(Reason.MissingHost, "http://:8080")
        assertInvalid(Reason.MissingHost, "  https://  ")
    }

    @Test
    fun malformedAddressesAreRefusedAsMalformed() {
        val malformed = listOf(
            "https://user:secret@example.com",
            "https://example.com?x=1",
            "https://example.com#fragment",
            "https://example.com:0",
            "https://example.com:65536",
            "https://example.com:abc",
            "https://example.com:8080:90",
            "https://exa mple.com",
            "https://[fe80::1",
            "https://[fe80::1]extra",
            "https://[example.com]",
            "https://.",
            "https://example..com",
            "https://-example.com",
            "https://999.1.1.1",
        )

        for (address in malformed) {
            assertInvalid(Reason.Malformed, address)
        }
    }

    @Test
    fun theReturnedAddressIsNormalised() {
        assertValid("https://example.com", "  HTTPS://Example.COM/  ")
        assertValid("https://example.com:8443/api", "HTTPS://Example.COM:8443/api/")
        assertValid("http://10.0.2.2:8080", " HTTP://10.0.2.2:8080 ")
        assertValid("http://[fe80::1]:8080", "http://[FE80::1]:8080/")
    }

    private fun assertValid(expected: String, input: String) {
        assertEquals(ServerAddressValidation.Valid(expected), validateServerAddress(input), "input: $input")
    }

    private fun assertInvalid(expected: Reason, input: String) {
        assertEquals(ServerAddressValidation.Invalid(expected), validateServerAddress(input), "input: $input")
    }
}
