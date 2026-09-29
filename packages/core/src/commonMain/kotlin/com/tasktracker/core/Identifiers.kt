@file:OptIn(ExperimentalUuidApi::class)

package com.tasktracker.core

import kotlinx.serialization.Serializable
import kotlin.jvm.JvmInline
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

/**
 * Identifiers are distinct types so a `ProjectID` can never be passed where a
 * `WorkItemID` is expected — the compiler enforces it, not a reviewer.
 *
 * Each is a `@JvmInline value class` over [Uuid], which is the closest Kotlin
 * equivalent of the original's `RawRepresentable` struct: a distinct nominal
 * type with no allocation at runtime, so the type safety costs nothing. They are
 * `@Serializable`, and because kotlinx.serialization treats a value class
 * transparently the wire form is a bare JSON string — `"0195..."` — not
 * `{"rawValue":"0195..."}`. That matters: the server sees the same identifier
 * whether it came from iOS or Android, and a change here is a protocol change.
 */
@Serializable
@JvmInline
value class ProjectID(val rawValue: Uuid) {
    constructor(raw: String) : this(Uuid.parse(raw))
    val stringValue: String get() = rawValue.toString()
    override fun toString(): String = stringValue
}

/** A unit of work on a board. Distinct from [ProjectID] on purpose. */
@Serializable
@JvmInline
value class WorkItemID(val rawValue: Uuid) {
    constructor(raw: String) : this(Uuid.parse(raw))
    val stringValue: String get() = rawValue.toString()
    override fun toString(): String = stringValue
}

/** The account a session belongs to. */
@Serializable
@JvmInline
value class UserID(val rawValue: Uuid) {
    constructor(raw: String) : this(Uuid.parse(raw))
    val stringValue: String get() = rawValue.toString()
    override fun toString(): String = stringValue
}

/** One row in a work item's status history. */
@Serializable
@JvmInline
value class StatusChangeID(val rawValue: Uuid) {
    constructor(raw: String) : this(Uuid.parse(raw))
    val stringValue: String get() = rawValue.toString()
    override fun toString(): String = stringValue
}

/**
 * A stable, human-readable key in the Jira style (`TT-142`), derived server-side
 * and cached locally so a work item rendered offline still shows a sensible key.
 *
 * Unlike the four UUID-backed types this is not a value class, because a `TT-142`
 * is a short human-authored string, not a generated one — there is nothing to
 * randomise and no parse step to fail.
 */
@Serializable
@JvmInline
value class WorkItemKey(val rawValue: String) {
    override fun toString(): String = rawValue
}

/** Generates a fresh identifier. The original's `init()`; named so the intent is visible. */
fun randomProjectID(): ProjectID = ProjectID(Uuid.random())

/** Generates a fresh identifier. */
fun randomWorkItemID(): WorkItemID = WorkItemID(Uuid.random())

/** Generates a fresh identifier. */
fun randomUserID(): UserID = UserID(Uuid.random())

/** Generates a fresh identifier. */
fun randomStatusChangeID(): StatusChangeID = StatusChangeID(Uuid.random())

/**
 * Parses an identifier from its wire string, or returns `null` if malformed.
 *
 * Deliberately not a throwing constructor: IDs arrive from deep links, clipboard
 * pastes and server payloads, all of which are untrusted. A caller that reached
 * for `runCatching` around an identifier parse would get the wrong shape of
 * failure — a `Result` around a `Throwable` that is not an [AppError] would leak
 * past the data layer's contract. `null` keeps the rejection local.
 */
fun parseProjectID(raw: String): ProjectID? = runCatching { ProjectID(Uuid.parse(raw)) }.getOrNull()

/** Parses an identifier from its wire string, or returns `null` if malformed. */
fun parseWorkItemID(raw: String): WorkItemID? = runCatching { WorkItemID(Uuid.parse(raw)) }.getOrNull()

/** Parses an identifier from its wire string, or returns `null` if malformed. */
fun parseUserID(raw: String): UserID? = runCatching { UserID(Uuid.parse(raw)) }.getOrNull()

/** Parses an identifier from its wire string, or returns `null` if malformed. */
fun parseStatusChangeID(raw: String): StatusChangeID? =
    runCatching { StatusChangeID(Uuid.parse(raw)) }.getOrNull()
