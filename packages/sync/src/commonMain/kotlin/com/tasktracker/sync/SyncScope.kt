package com.tasktracker.sync

/**
 * One logical change stream the server can replay from a cursor.
 *
 * A scope is *not* an entity type. `workItems` is a scope, and changes arriving on it
 * may reference either a work item or the project that work item belongs to — renaming
 * a project has to reach clients that never asked for projects. Keeping the two concepts
 * separate is what lets one pull loop serve both.
 */
enum class SyncScope(val wireValue: String) {
    Projects("projects"),
    WorkItems("workItems"),
    ;

    companion object {
        val all: List<SyncScope> get() = entries
    }
}

/** What set a cycle off. Logged, because "synced" and "synced because the user typed" are
 * different things when a user reports a missing update. */
enum class SyncTrigger(val wireName: String) {
    /** App process started. */
    AppLaunch("appLaunch"),

    /** App returned to the foreground. */
    Foreground("foreground"),

    /** A local mutation queued an outbox row. */
    LocalWrite("localWrite"),

    /** The periodic timer. */
    Periodic("periodic"),

    /** The user asked for it. */
    Manual("manual"),

    /** The network path came back. */
    ConnectivityRestored("connectivityRestored"),
}

/** Coarse progress, reported while a cycle runs so the UI can say something truthful
 * instead of a spinner. */
sealed interface SyncPhase {
    data class Pushing(val count: Int) : SyncPhase

    data class Pulling(val scope: SyncScope) : SyncPhase

    /** Outbox and pulls are done; writing bookkeeping. */
    data object Finalising : SyncPhase
}

/** Where a failure happened, so a report can be acted on rather than just displayed. */
sealed interface SyncStage {
    data object RecoveringStranded : SyncStage
    data object Push : SyncStage

    data class Pull(val scope: SyncScope) : SyncStage
}

/**
 * One thing that went wrong, in a form a view can list.
 *
 * The original gave every failure a `UUID` because `SyncFailure` was `Identifiable`, which
 * SwiftUI needs to diff a list. Nothing here needs an identity for that: a failure has no
 * life outside the report that holds it, and a random id per failure would put a UUID in
 * every `toString` a test prints. Identity is the report's job, not the failure's.
 */
class SyncFailure(
    val stage: SyncStage,
    val message: String,
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is SyncFailure) return false
        return stage == other.stage && message == other.message
    }

    override fun hashCode(): Int = 31 * stage.hashCode() + message.hashCode()

    override fun toString(): String = "SyncFailure(stage=$stage, message=$message)"
}
