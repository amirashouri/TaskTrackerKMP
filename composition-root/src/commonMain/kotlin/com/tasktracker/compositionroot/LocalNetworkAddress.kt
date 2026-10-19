package com.tasktracker.compositionroot

/**
 * Whether reaching [apiBaseUrl] needs Android's local-network permission.
 *
 * ## Why this exists
 *
 * Android 17 gates an app's access to its own local network behind a runtime permission
 * (`android.permission.ACCESS_LOCAL_NETWORK`, Local Network Protection). The gate is
 * enforced deep in the networking stack and it fails *silently*: a blocked connection is
 * not refused, it times out, and the client reports the timeout as `AppError.Transport` —
 * the same sentence a server that is genuinely down produces. Measured on an API 37
 * emulator with this app's default root, `http://10.0.2.2:8080`: every port from the app's
 * uid timed out (`8080`, `5432`, `80`, `53`), while the identical probe from the `shell`
 * uid answered, and the same URL in Chrome — which holds the permission — rendered `ok`.
 *
 * The permission is therefore requested at launch, and only when the build's root is a
 * local-network address: a deployment pointed at a public hostname never touches the local
 * network, and prompting it for "nearby devices" access would ask for something the app
 * cannot use. That decision has to be a pure function of the URL, so it is pinned on the
 * fast host rather than only on a device whose permission state has been arranged.
 *
 * ## What counts as local
 *
 * Android's "Local Network Definition" lists IPv4 `169.254.0.0/16`, `100.64.0.0/10`
 * (CGNAT — which is also the range a Tailscale address falls in), `10.0.0.0/8`,
 * `172.16.0.0/12`, `192.168.0.0/16`, multicast `224.0.0.0/4` and `255.255.255.255`; for
 * IPv6, link-local, multicast, and "stub networks like Thread" — Thread addresses are
 * unique-local, so `fc00::/7` here. Names ending in `.local`, and single-label names, are
 * local too: they resolve through mDNS on the LAN, which the same permission covers.
 *
 * Loopback is **not** on that list and is deliberately excluded: `http://127.0.0.1:8080`
 * reached the server with the permission denied (through `adb reverse tcp:8080 tcp:8080`),
 * so asking for it there would be a prompt with nothing behind it.
 *
 * ## The boundary this cannot see
 *
 * A hostname that *resolves* to a local address — `nas.tailnet.ts.net` over MagicDNS, say
 * — is not recognisable from its spelling, and deciding it would mean resolving the name:
 * a network round trip, not a rule. Such a root is classified as remote and its first
 * request waits for a timeout. The place that catches it is the runtime-URL validation in
 * `docs/PLAN.md` §6.3, which probes the address before saving it and so reports the
 * failure while the user is still looking at the field.
 */
fun apiRootRequiresLocalNetworkAccess(apiBaseUrl: String): Boolean =
    hostOf(apiBaseUrl)?.let(::isLocalNetworkHost) ?: false

/**
 * The host of an API root, or `null` when the string has nothing readable as one.
 *
 * Hand-rolled rather than delegated: this module has no HTTP dependency (Ktor lives in
 * `:packages:network`), and an API root can only ever be
 * `scheme://[user[:password]@]host[:port][/path][?query][#fragment]`. The scheme is
 * optional so that a value a runtime field has not normalised yet is still classified.
 */
private fun hostOf(apiBaseUrl: String): String? {
    val authority = apiBaseUrl.substringAfter("://", apiBaseUrl)
        .substringBefore('/')
        .substringBefore('?')
        .substringBefore('#')
    val hostPort = authority.substringAfterLast('@')
    if (hostPort.isEmpty()) return null
    // Bracketed IPv6 (`[fe80::1]:8080`) needs the brackets stripped before its colons can
    // be read as anything other than a port separator.
    return if (hostPort.startsWith('[')) {
        hostPort.substringAfter('[').substringBefore(']').ifEmpty { null }
    } else {
        hostPort.substringBefore(':').ifEmpty { null }
    }
}

/** [host], lower-cased, classified against Android's local-network definition. */
private fun isLocalNetworkHost(host: String): Boolean {
    val name = host.lowercase()
    if (name.isEmpty() || name == "localhost") return false
    ipv4Octets(name)?.let { return isLocalIpv4(it) }
    if (name.contains(':')) return isLocalIpv6(name)
    // `nas` and `tasktracker.local` are mDNS territory: no other kind of resolution names a
    // machine on the same LAN, and a dotted name is a DNS lookup that can point anywhere.
    return !name.contains('.') || name.endsWith(".local")
}

/** The four octets of a dotted-quad address, or `null` when [host] is not one. */
private fun ipv4Octets(host: String): List<Int>? {
    val parts = host.split('.')
    if (parts.size != 4) return null
    return parts.map { part -> part.toIntOrNull()?.takeIf { it in 0..255 } ?: return null }
}

private fun isLocalIpv4(octets: List<Int>): Boolean {
    val (a, b, c, d) = octets
    return when {
        a == 10 -> true                              // 10.0.0.0/8
        a == 100 && b in 64..127 -> true             // 100.64.0.0/10, CGNAT and Tailscale
        a == 172 && b in 16..31 -> true              // 172.16.0.0/12
        a == 192 && b == 168 -> true                 // 192.168.0.0/16
        a == 169 && b == 254 -> true                 // 169.254.0.0/16, link-local
        a in 224..239 -> true                        // 224.0.0.0/4, multicast
        a == 255 && b == 255 && c == 255 && d == 255 -> true // the broadcast address
        else -> false
    }
}

private fun isLocalIpv6(host: String): Boolean = when {
    host == "::1" -> false                           // loopback, exempt like its IPv4 form
    host.startsWith("fe8") || host.startsWith("fe9") ||
        host.startsWith("fea") || host.startsWith("feb") -> true // fe80::/10, link-local
    host.startsWith("ff") -> true                    // ff00::/8, multicast
    host.startsWith("fc") || host.startsWith("fd") -> true // fc00::/7, unique-local
    else -> false
}
