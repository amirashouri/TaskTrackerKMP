package com.tasktracker.core

/**
 * Whether an interpolated value may appear in the log output.
 *
 * This mirrors the original's `OSLogPrivacy` but is a real enum, which
 * `OSLogPrivacy` is not — and it is the type the builder accepts, so redaction is
 * visible in the type signature rather than in a comment nobody reads.
 */
enum class LogPrivacy {
    /** Always substituted with `<private>`. */
    Private,

    /** Always rendered. */
    Public,

    /**
     * Rendered. Use for ordinary values; pass [Private] for anything sensitive.
     *
     * Treated as visible. Failing closed here would make correct usage awkward —
     * a counter would have to opt *out* of redaction — and the failure mode is
     * asymmetric: a wrongly-visible counter is noise, a wrongly-hidden project
     * name is a bug nobody can debug. Anything genuinely sensitive must pass
     * [Private] explicitly.
     */
    Auto,
}

/**
 * A log message assembled with explicit redaction.
 *
 * The original relies on `ExpressibleByStringInterpolation` to get `\(value,
 * privacy:)` at the call site. Kotlin has no equivalent that survives a
 * cross-platform compile, so this uses a builder instead:
 *
 * ```
 * logger.info { "refreshed \(it)" }        // no, not this — see below
 * logger.info { append("refreshed "); append(userId, LogPrivacy.Private) }
 * ```
 *
 * Which is more verbose at the call site and that is the accepted trade: the
 * alternative is a string template that cannot be redacted at all, and
 * "remember to not log the token" is exactly the kind of rule that holds until
 * the one time it does not.
 *
 * The built text is a plain [String], so the sink sees a finished value. Redaction
 * therefore happens *before* the message reaches any sink, and cannot be bypassed
 * by handing a raw template to a platform logger.
 */
class LogMessage private constructor(val text: String) {

    class Builder {
        private val text = StringBuilder()

        fun append(literal: String): Builder = apply { text.append(literal) }

        fun append(value: Any?, privacy: LogPrivacy = LogPrivacy.Auto): Builder = apply {
            text.append(if (privacy == LogPrivacy.Private) "<private>" else value.toString())
        }

        fun build(): LogMessage = LogMessage(text.toString())
    }

    override fun toString(): String = text
}

/** Builds a [LogMessage]. See [LogMessage] for why this is not a string template. */
fun logMessage(block: LogMessage.Builder.() -> Unit): LogMessage =
    LogMessage.Builder().apply(block).build()

/** Severity, ordered so a test can assert a threshold. */
enum class LogLevel { Debug, Info, Warning, Error }

fun interface LogSink {
    fun write(level: LogLevel, category: String, text: String)
}

/**
 * Thin wrapper over the platform log so the subsystem and category list lives in
 * one place.
 *
 * Messages are built lazily — the `() -> LogMessage` is only invoked if the level
 * is enabled. That matters for the sync engine, which assembles messages inside
 * loops and would otherwise pay string-building cost for output nobody sees.
 *
 * There is no redaction here: it has already happened in [logMessage], and this
 * class has no way to add any. The `sink` property is `internal` so no caller can
 * hand a raw, unredacted template to a platform logger and bypass the builder.
 */
class TTLogger internal constructor(private val category: String) {

    internal fun emit(level: LogLevel, message: () -> LogMessage) {
        if (!isEnabled(level)) return
        sink.write(level, category, message().text)
    }

    internal fun isEnabled(level: LogLevel): Boolean = level.ordinal >= minimumLevel.ordinal

    fun debug(block: () -> LogMessage) = emit(LogLevel.Debug, block)
    fun info(block: () -> LogMessage) = emit(LogLevel.Info, block)
    fun warning(block: () -> LogMessage) = emit(LogLevel.Warning, block)
    fun error(block: () -> LogMessage) = emit(LogLevel.Error, block)

    /**
     * Replaces the process-wide sink.
     *
     * Exists for two callers, and the second is the reason it is public rather than `internal`:
     *
     *  - a platform, to route to `os_log` or `android.util.Log` instead of [println]. The
     *    default sink is stdout on *every* target including release builds, so without this
     *    there is no way for a device build to log anywhere a person can read.
     *  - a test, to assert on what a subsystem reported. A log line is an observable effect,
     *    and "the reason a session ended was recorded" is a claim about an effect — the same
     *    kind of claim as "the session cache was cleared", which *is* asserted against a real
     *    collaborator. Without a seam the only alternative is a public mutable field for the
     *    assertion, which is strictly worse: it is state a caller can corrupt, and it cannot
     *    be narrowed to a single install.
     *
     * [revert] returns the previous sink so a caller can put it back. A test that installs
     * one and does not restore it would silently change the logging of every test that runs
     * after it, which is the kind of leak that makes a suite order-dependent.
     */
    fun install(sink: LogSink): Revert {
        val previous = Companion.sink
        Companion.sink = sink
        return Revert { Companion.sink = previous }
    }

    /** Puts back the sink [install] replaced. See [install]. */
    class Revert internal constructor(private val undo: () -> Unit) {
        operator fun invoke() = undo()
    }

    companion object {
        /** The one place the sink and the threshold are configured. */
        internal var sink: LogSink = LogSink { level, category, text ->
            println("[${level.name.lowercase()}] $category: $text")
        }

        /** Below this, [debug] and [info] are dropped without building the message. */
        internal var minimumLevel: LogLevel = LogLevel.Debug

        val auth = TTLogger("auth")
        val network = TTLogger("network")
        val database = TTLogger("database")
        val sync = TTLogger("sync")
        val routing = TTLogger("routing")
        val di = TTLogger("di")
        val board = TTLogger("board")
        val projects = TTLogger("projects")
        val settings = TTLogger("settings")
        val notifications = TTLogger("notifications")
    }
}
