package com.tasktracker.featuresettingsdomain

/**
 * The answer to "may this server address be saved?".
 *
 * A value, not an exception: a mistyped address is an ordinary thing for a person to type,
 * and the Settings field has to show *which* part is wrong. The reasons are a closed set so
 * the presentation layer's `when` is exhaustive — adding one here is a compile error there,
 * not a sentence that silently falls back to "something went wrong".
 */
sealed interface ServerAddressValidation {

    /** [baseUrl] is normalised and safe to persist. */
    data class Valid(val baseUrl: String) : ServerAddressValidation

    data class Invalid(val reason: Reason) : ServerAddressValidation

    enum class Reason {
        /** Nothing but whitespace was entered. */
        Blank,

        /** No `scheme://` at all, as in `example.com:8080`. */
        MissingScheme,

        /** A scheme other than `http` or `https`. */
        UnsupportedScheme,

        /** A scheme was present, but there is no host to connect to. */
        MissingHost,

        /**
         * `http` to a host that is not on the local network.
         *
         * Cleartext to a public host would put the session token on the wire for anyone on
         * the path; a local address is the `lan` provider's own deployment and is allowed.
         * This is a policy decision, not a parse failure — the URL is otherwise well formed.
         */
        CleartextNotPermitted,

        /** Anything else that is not a URL this client can use. */
        Malformed,

        /** The address parsed, but no TaskTracker server answered its health probe. */
        Unreachable,
    }
}

/**
 * Validates [input] as the TaskTracker API root, without touching the network.
 *
 * Pure and synchronous on purpose: the whole table runs on the fast host, and the probe that
 * cannot be pure lives behind [ServerAddressProbe] in [CheckServerAddress].
 *
 * ## The cleartext rule
 *
 * `https` is accepted for any readable host. `http` is accepted only for a host that is on
 * the local network — loopback, the emulator's `10.0.2.2`, RFC1918, link-local, CGNAT (which
 * includes Tailscale addresses), and `.local`/single-label names. That is the default `lan`
 * provider's deployment: plain HTTP on a home or office network. A public hostname over
 * `http` is refused, because the alternative is sending a bearer token through every network
 * between the two devices.
 *
 * Android's own cleartext allow-list is narrower (`localhost` and `10.0.2.2` only). It is a
 * platform gate, documented in the parent plan, and deliberately not widened here. A LAN
 * address passes this rule and then fails the probe on Android — which is the right place
 * to report it, while the user is still looking at the field.
 *
 * ## Normalisation
 *
 * The value returned in [ServerAddressValidation.Valid] is what 6.2 will persist: surrounding
 * whitespace trimmed, scheme and host lower-cased, and trailing slashes removed. A path
 * prefix (`https://host/api`) is preserved because the API root may be mounted below `/`.
 */
fun validateServerAddress(input: String): ServerAddressValidation {
    val trimmed = input.trim()
    if (trimmed.isEmpty()) return invalid(ServerAddressValidation.Reason.Blank)
    // An interior space is never valid, and checking it here keeps the authority parser
    // from having to distinguish "a hostname with a space" from a path.
    if (trimmed.any { it.isWhitespace() }) return invalid(ServerAddressValidation.Reason.Malformed)

    val separator = trimmed.indexOf("://")
    if (separator <= 0) return invalid(ServerAddressValidation.Reason.MissingScheme)

    val scheme = trimmed.substring(0, separator).lowercase()
    if (scheme != "http" && scheme != "https") {
        return invalid(ServerAddressValidation.Reason.UnsupportedScheme)
    }

    val remainder = trimmed.substring(separator + 3)
    if (remainder.isEmpty()) return invalid(ServerAddressValidation.Reason.MissingHost)

    val authorityEnd = remainder.indexOfFirst { it == '/' || it == '?' || it == '#' }
    val authority = if (authorityEnd < 0) remainder else remainder.substring(0, authorityEnd)
    val pathAndMore = if (authorityEnd < 0) "" else remainder.substring(authorityEnd)

    if (authority.isEmpty() || authority.startsWith(':')) {
        return invalid(ServerAddressValidation.Reason.MissingHost)
    }
    // Userinfo in a base URL is a credential the app would have to store and log; the API
    // root is not where a password belongs. A query or fragment is not part of a root either.
    if ('@' in authority) return invalid(ServerAddressValidation.Reason.Malformed)
    if ('?' in pathAndMore || '#' in pathAndMore) {
        return invalid(ServerAddressValidation.Reason.Malformed)
    }

    val parsed = parseAuthority(authority) ?: return invalid(ServerAddressValidation.Reason.Malformed)

    parsed.port?.let { port ->
        if (port.any { !it.isDigit() }) return invalid(ServerAddressValidation.Reason.Malformed)
        val number = port.toIntOrNull()
        if (number == null || number !in 1..65535) {
            return invalid(ServerAddressValidation.Reason.Malformed)
        }
    }

    if (scheme == "http" && !cleartextIsAllowed(parsed.host)) {
        return invalid(ServerAddressValidation.Reason.CleartextNotPermitted)
    }

    val host = if (parsed.bracketed) "[${parsed.host.lowercase()}]" else parsed.host.lowercase()
    val port = parsed.port?.let { ":$it" } ?: ""
    val path = pathAndMore.trimEnd('/')

    return ServerAddressValidation.Valid("$scheme://$host$port$path")
}

private fun invalid(reason: ServerAddressValidation.Reason): ServerAddressValidation.Invalid =
    ServerAddressValidation.Invalid(reason)

private data class Authority(val host: String, val port: String?, val bracketed: Boolean)

/**
 * Splits `host[:port]` or `[ipv6][:port]`, or returns `null` when it is neither.
 *
 * Hand-rolled because this module has no URL type in common code and may not depend on Ktor.
 * The grammar it accepts is the one an API root needs, not the whole RFC: no userinfo (checked
 * by the caller), no empty labels, and a bracketed host must actually be an IPv6 literal.
 */
private fun parseAuthority(authority: String): Authority? {
    if (authority.startsWith('[')) {
        val close = authority.indexOf(']')
        if (close < 0) return null
        val host = authority.substring(1, close)
        if (host.isEmpty() || ':' !in host) return null
        if (host.any { !it.isLetterOrDigit() && it != ':' && it != '.' && it != '%' }) return null
        val rest = authority.substring(close + 1)
        val port = when {
            rest.isEmpty() -> null
            rest.startsWith(':') -> rest.substring(1).ifEmpty { return null }
            else -> return null
        }
        return Authority(host = host, port = port, bracketed = true)
    }

    // More than one colon without brackets is not a host-and-port pair; it is an IPv6
    // address someone forgot to bracket, and Ktor would read it differently than intended.
    if (authority.count { it == ':' } > 1) return null

    val host = authority.substringBefore(':')
    if (!isReadableDnsName(host)) return null
    val port = if (':' in authority) authority.substringAfter(':') else null
    if (port != null && port.isEmpty()) return null
    return Authority(host = host, port = port, bracketed = false)
}

/** A DNS name or IPv4 literal the client can hand to a URL builder. */
private fun isReadableDnsName(host: String): Boolean {
    if (host.isEmpty()) return false
    val labels = host.split('.')
    if (labels.any { it.isEmpty() || it.startsWith('-') || it.endsWith('-') }) return false
    if (labels.any { label -> label.any { !it.isLetterOrDigit() && it != '-' } }) return false

    // Four all-numeric labels are an IPv4 literal, and `999.1.1.1` is not one. Without this
    // it would pass as a DNS name and fail at the probe instead of at the field.
    if (labels.size == 4 && labels.all { label -> label.all(Char::isDigit) }) {
        for (label in labels) {
            val octet = label.toIntOrNull() ?: return false
            if (octet !in 0..255) return false
        }
    }
    return true
}

/**
 * Whether plain HTTP to [host] stays on the local network.
 *
 * The ranges are Android's "Local Network Definition" minus multicast and broadcast, plus
 * loopback (which that definition excludes because it needs no permission, but which is
 * also not a public path). CGNAT matters twice here: it is Tailscale's address range, and a
 * tailnet is as private as a LAN.
 */
private fun cleartextIsAllowed(host: String): Boolean {
    val name = host.lowercase()
    if (name == "localhost" || name == "::1") return true
    ipv4Octets(name)?.let { return isLocalIpv4(it) }
    if (name.contains(':')) return isLocalIpv6(name)
    // A dotted name is a DNS lookup that can point anywhere; `nas` and `nas.local` resolve
    // through mDNS on the same LAN.
    return !name.contains('.') || name.endsWith(".local")
}

private fun ipv4Octets(host: String): List<Int>? {
    val parts = host.split('.')
    if (parts.size != 4) return null
    return parts.map { part -> part.toIntOrNull()?.takeIf { it in 0..255 } ?: return null }
}

private fun isLocalIpv4(octets: List<Int>): Boolean {
    val first = octets[0]
    val second = octets[1]
    return when {
        first == 10 -> true                                  // 10.0.0.0/8
        first == 127 -> true                                 // 127.0.0.0/8, loopback
        first == 100 && second in 64..127 -> true            // 100.64.0.0/10, CGNAT/Tailscale
        first == 172 && second in 16..31 -> true             // 172.16.0.0/12
        first == 192 && second == 168 -> true                // 192.168.0.0/16
        first == 169 && second == 254 -> true                // 169.254.0.0/16, link-local
        else -> false
    }
}

private fun isLocalIpv6(host: String): Boolean {
    val name = host.substringBefore('%').lowercase()
    return name == "::1" ||
        name.startsWith("fe8") || name.startsWith("fe9") ||
        name.startsWith("fea") || name.startsWith("feb") || // fe80::/10, link-local
        name.startsWith("fc") || name.startsWith("fd")       // fc00::/7, unique-local
}
