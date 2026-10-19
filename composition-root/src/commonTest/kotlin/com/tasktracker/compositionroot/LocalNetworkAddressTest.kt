package com.tasktracker.compositionroot

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * [apiRootRequiresLocalNetworkAccess] — one case per range it recognises, one per exclusion,
 * and the boundaries in between.
 *
 * The rule is the only thing deciding whether an Android launch shows a permission prompt, in
 * either direction: too wide and a deployment pointed at a public hostname asks for access it
 * cannot use, too narrow and the app's first request to its own server dies as a timeout. Both
 * directions are pinned here rather than left to the address list in the implementation.
 */
class LocalNetworkAddressTest {

    @Test
    fun `the Android default root is local and that is why the permission exists`() {
        assertTrue(
            apiRootRequiresLocalNetworkAccess("http://10.0.2.2:8080"),
            "10.0.2.2 is inside the emulator guest's own subnet, so it is exactly the root " +
                "Local Network Protection blocks: measured as a TCP timeout from the app uid " +
                "with the permission denied, and `ok` from a uid that holds it",
        )
    }

    @Test
    fun `loopback needs no permission so it is not local`() {
        listOf("http://127.0.0.1:8080", "http://localhost:8080", "http://[::1]:8080").forEach {
            assertFalse(
                apiRootRequiresLocalNetworkAccess(it),
                "$it is loopback: it reached the server with the permission denied (through " +
                    "adb reverse tcp:8080 tcp:8080), so a prompt for it would guard nothing",
            )
        }
    }

    @Test
    fun `the documented IPv4 ranges are local`() {
        listOf(
            "http://10.1.2.3:8080",            // 10.0.0.0/8
            "https://172.16.0.1:8443",         // 172.16.0.0/12, TLS to a LAN address
            "http://192.168.1.5:8080",         // 192.168.0.0/16
            "http://169.254.10.20:8080",       // 169.254.0.0/16, link-local
            "http://100.64.0.1:8080",          // 100.64.0.0/10, CGNAT — Tailscale's range
            "http://100.127.255.254:8080",     // the top of that /10
        ).forEach {
            assertTrue(
                apiRootRequiresLocalNetworkAccess(it),
                "$it is inside a range Android documents as local network, so its connections " +
                    "are gated by ACCESS_LOCAL_NETWORK",
            )
        }
    }

    @Test
    fun `the range boundaries are exact`() {
        listOf(
            "http://11.0.0.1",                 // just outside 10.0.0.0/8
            "http://172.15.255.255",           // just below 172.16.0.0/12
            "http://172.32.0.1",               // just above 172.16.0.0/12
            "http://192.169.0.1",              // just outside 192.168.0.0/16
            "http://169.255.0.1",              // just outside 169.254.0.0/16
            "http://100.63.255.255",           // just below 100.64.0.0/10
            "http://100.128.0.1",              // just above 100.64.0.0/10
        ).forEach {
            assertFalse(
                apiRootRequiresLocalNetworkAccess(it),
                "$it is outside every range Android defines as local network; a prompt here " +
                    "would be for a connection the platform never gates",
            )
        }
    }

    @Test
    fun `multicast and the broadcast address are local and no other 255 address is`() {
        listOf("http://224.0.0.1", "http://239.255.255.255", "http://255.255.255.255").forEach {
            assertTrue(
                apiRootRequiresLocalNetworkAccess(it),
                "$it is multicast or the broadcast address, both of which Android classifies " +
                    "as local network",
            )
        }
        assertFalse(
            apiRootRequiresLocalNetworkAccess("http://255.255.1.1"),
            "255.255.1.1 is not the broadcast address; only 255.255.255.255 is on the list, " +
                "so widening the test to any 255 address would prompt needlessly",
        )
    }

    @Test
    fun `IPv6 link-local multicast and unique-local are local`() {
        listOf(
            "http://[fe80::1]:8080",   // fe80::/10, link-local
            "http://[febf::1]:8080",   // the top of that /10
            "http://[ff02::1]:8080",   // ff00::/8, multicast
            "http://[fc00::1]:8080",   // fc00::/7, unique-local
            "http://[fd00::1]:8080",   // fd00::/8, what Thread hands out
        ).forEach {
            assertTrue(
                apiRootRequiresLocalNetworkAccess(it),
                "$it is a local-network address on the IPv6 side of Android's definition",
            )
        }
    }

    @Test
    fun `a public host or address is remote`() {
        listOf(
            "https://server.example.com",
            "https://203.0.113.7:8443",
            "http://[2606:4700:4700::1111]:8080",
        ).forEach {
            assertFalse(
                apiRootRequiresLocalNetworkAccess(it),
                "$it is a global address or a public name: asking its deployment for " +
                    "nearby-devices access would be a prompt the app cannot use",
            )
        }
    }

    @Test
    fun `an mDNS name is local and a dotted DNS name is not`() {
        listOf("http://tasktracker.local:8080", "http://nas:8080", "http://NAS:8080").forEach {
            assertTrue(
                apiRootRequiresLocalNetworkAccess(it),
                "$it is resolved through mDNS on the LAN, which ACCESS_LOCAL_NETWORK also gates",
            )
        }
        listOf("https://nas.example.com", "http://tasktracker.internal").forEach {
            assertFalse(
                apiRootRequiresLocalNetworkAccess(it),
                "$it is a DNS name whose spelling says nothing about where it resolves; the " +
                    "documented boundary is that such a root is not recognised, and §6.3's " +
                    "probe is what reports a blocked address",
            )
        }
    }

    @Test
    fun `userinfo paths and an absent port do not hide the host`() {
        assertTrue(
            apiRootRequiresLocalNetworkAccess(
                "https://user:secret@192.168.1.5:8443/v1/healthz?probe=1#top",
            ),
            "userinfo, a path, a query and a fragment are all allowed in an API root; none of " +
                "them changes that 192.168.1.5 is local",
        )
        assertFalse(
            apiRootRequiresLocalNetworkAccess("https://user:secret@server.example.com/v1"),
            "the same shape with a public name must stay remote",
        )
        assertTrue(
            apiRootRequiresLocalNetworkAccess("http://192.168.1.5"),
            "a root with no port still names a local address",
        )
        assertFalse(
            apiRootRequiresLocalNetworkAccess(""),
            "an empty value names nothing; the resolver never returns one, and a blank must " +
                "not be read as a single-label local name",
        )
    }
}
