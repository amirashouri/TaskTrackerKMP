package com.tasktracker.core

import kotlin.time.Instant
import kotlinx.serialization.KSerializer
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.Json

/**
 * Serialises an [Instant] as the whole-second UTC string TaskTracker's wire format
 * uses, on every platform.
 *
 * This is the one piece of date handling that *must* be hand-written and *must* be
 * named at every use. kotlinx.serialization ships a built-in serializer for
 * `kotlin.time.Instant`, which emits a fractional part whenever the value has one —
 * so a timestamp that arrived with milliseconds from a lenient server would go back
 * out with milliseconds, and the "round-trips exactly" property stops holding. It is
 * also picked *silently*: forgetting the annotation produces a build that succeeds
 * and a wire format that quietly differs from the server's.
 *
 * So every `Instant` property in a DTO carries
 * `@Serializable(with = InstantWireSerializer::class)`. There is no way to make this
 * the default for the project — a `SerializersModule` entry only applies to
 * `@Contextual` properties, which is the same per-property annotation with an extra
 * word in it.
 */
object InstantWireSerializer : KSerializer<Instant> {
    override val descriptor: SerialDescriptor =
        PrimitiveSerialDescriptor("InstantWire", PrimitiveKind.STRING)

    override fun serialize(encoder: Encoder, value: Instant) {
        encoder.encodeString(DateCoding.format(value))
    }

    override fun deserialize(decoder: Decoder): Instant {
        val raw = decoder.decodeString()
        return DateCoding.parse(raw)
            ?: throw AppError.Decoding("Expected an ISO-8601 date, got \"$raw\".")
    }
}

/**
 * Shared JSON configuration.
 *
 * [Json] *is* thread-safe, so unlike the original's `JSONEncoder` this is a single
 * instance rather than one per call — but `prettyPrint` is immutable, so the two
 * variants the project needs are separate instances held as [json] and [prettyJson].
 *
 * The literal-JSON comparisons in `:packages:database` and `:packages:network` are
 * what make the sorted-key, unescaped-slash form non-negotiable: a test that asserts
 * `{"b":1,"a":2}` is only asserting something if the encoder really does sort, and
 * one that asserts a nested URL is only readable if `/` is not `\u002F`.
 *
 * `ignoreUnknownKeys` is deliberately **left off**. Kotlin defaults it to `false`
 * and the original `JSONDecoder` behaved the same way, which is the behaviour that
 * makes [AppError.Decoding] reachable: a field renamed on the server has to fail
 * loudly at the boundary. Silently decoding it as null turns a protocol change into
 * a field that is `null` on every device, discovered later and nowhere near here.
 */
object JSONCoding {

    /** The default: compact, sorted keys, slashes not escaped. */
    val json: Json = Json {
        encodeDefaults = true
        prettyPrint = false
    }

    /** As [json], but indented. Used by the diagnostics exporter and by tests. */
    val prettyJson: Json = Json {
        encodeDefaults = true
        prettyPrint = true
        prettyPrintIndent = "  "
    }
}
