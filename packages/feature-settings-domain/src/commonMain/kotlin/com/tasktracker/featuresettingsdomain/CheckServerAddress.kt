package com.tasktracker.featuresettingsdomain

/**
 * Asks a candidate server whether it is a TaskTracker server, without a session.
 *
 * A port rather than a network call in the domain module: the rule that decides *whether* to
 * probe belongs here, and the Ktor client that knows how belongs in the data layer. The
 * method is deliberately narrow — one question, one boolean — so a fake is three lines and
 * the probe cannot grow a second responsibility by accident.
 */
fun interface ServerAddressProbe {
    /** `true` only when [baseUrl]'s liveness endpoint answers the expected body. */
    suspend fun answersOk(baseUrl: String): Boolean
}

/**
 * The whole "check before saving" operation: syntax first, then the network.
 *
 * The order matters. Probing a string that is not a URL would spend a round trip to report a
 * typo, and worse, would let a malformed value reach a URL builder whose exception text is
 * not something a person can act on. The pure rule runs first and an invalid address never
 * touches the probe.
 *
 * A failed probe is [ServerAddressValidation.Reason.Unreachable]. The distinction between
 * "DNS failed", "the port is closed" and "something answered but not `ok`" is not useful at
 * this field: in every case the address is not usable now, and the person's next move is the
 * same — check the address, the server, or the network.
 */
class CheckServerAddress(private val probe: ServerAddressProbe) {

    suspend fun check(input: String): ServerAddressValidation =
        when (val parsed = validateServerAddress(input)) {
            is ServerAddressValidation.Invalid -> parsed
            is ServerAddressValidation.Valid ->
                if (probe.answersOk(parsed.baseUrl)) {
                    parsed
                } else {
                    ServerAddressValidation.Invalid(ServerAddressValidation.Reason.Unreachable)
                }
        }
}
