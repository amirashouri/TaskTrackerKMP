package com.tasktracker.database

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * In `jvmTest` rather than `commonTest` for the only reason that reflection is a JVM
 * capability: Kotlin/Native has no `::class.java`, so a structural check over an entity's
 * fields cannot be written in common code. Every behavioural test of the session cache is
 * in `commonTest` and runs on all three targets; this one guards the shape, and it guards
 * it where shape is observable.
 */
class SessionRecordStructureTest {
    @Test
    fun noTokenIsEverWrittenToTheStore() {
        // A structural guard, not a behavioural one: if someone adds a credential field to
        // `SessionRecord`, this fails and points at the keychain instead. A behavioural test
        // could not catch it — a stored token is only visible in the file on disk, long
        // after the save that wrote it.
        val fields = SessionRecord::class.java.declaredFields.map { it.name.lowercase() }
        assertTrue(fields.isNotEmpty())
        for (field in fields) {
            assertFalse(field.contains("token"), "SessionRecord holds a $field; tokens belong in the keychain")
            assertFalse(field.contains("password"), "SessionRecord holds a $field; credentials belong in the keychain")
            assertFalse(field.contains("secret"), "SessionRecord holds a $field; secrets belong in the keychain")
        }
    }
}
