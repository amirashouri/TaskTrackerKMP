package com.tasktracker.database

import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import com.tasktracker.core.JSONCoding
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.test.fail
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.io.File

/**
 * The hand-written version-3 schema in [VersionThreeSchema], checked against the committed
 * `3.json`.
 *
 * ## Why this exists, and why it is the more important of the two migration tests
 *
 * `SprintMigrationTest` opens a store built from a **hand-written** copy of version 3. That
 * is unavoidable — Room's `MigrationTestHelper` is Android only, and this module's tests
 * cannot run on the Android host — but it means the whole migration suite rests on a
 * literal somebody typed. So this asks the only question that makes the literal trustworthy:
 *
 * > does it describe the version 3 that was actually shipped?
 *
 * The drift is not hypothetical and it is not subtle. Add a column in a later version,
 * update this file's SQL for the migration test so the open succeeds, and the literal goes
 * on claiming a version 3 that never existed — while `SprintMigrationTest` stays green,
 * because it is testing a store that never shipped.
 *
 * ## It compares what SQLite *made*, not the text of the literal
 *
 * The statements are executed against a real database and the result is read back through
 * `PRAGMA table_info` and `PRAGMA index_list`. Parsing the DDL string would compare the
 * literal with itself and find nothing at all.
 *
 * ## `jvmTest`, for the one reason that is a JVM capability
 *
 * Reading `3.json` off disk. `SprintMigrationTest` stays in `commonTest` and runs on both
 * hosts that can run Room at all; this one needs a filesystem and the JVM is where it runs.
 */
class SprintSchemaDriftTest {

    private val schemaFile = File(
        "schemas/com.tasktracker.database.TaskTrackerDatabase/${VersionThreeSchema.VERSION}.json",
    )

    @Test
    fun theCommittedSchemaIsThereToCheckAgainst() {
        // Asserted first, and separately, because "the guard could not find its input" and
        // "the guard found nothing wrong" would otherwise both be a silent pass on the
        // tests below — and a drift guard that quietly checks nothing is worse than no
        // guard, because it reads like evidence.
        assertTrue(
            schemaFile.isFile,
            "expected the committed schema at ${schemaFile.absolutePath}. Without it the " +
                "tests below compare the hand-written DDL with nothing and pass.",
        )
    }

    @Test
    fun everyTableInTheHandWrittenSchemaMatchesTheCommittedOneColumnForColumn() {
        val committed = committedTables()
        val (made, _) = buildSchema()

        assertEquals(
            committed.keys.sorted(),
            made.keys.sorted(),
            "the tables the migration test creates are not the tables version 3 had. Room " +
                "validates the whole schema on open, so this may still pass today - but the " +
                "literal has stopped describing a store that shipped.",
        )

        for (table in committed.keys) {
            val expected = committed.getValue(table)
            val actual = made[table] ?: emptyList()
            assertEquals(
                expected.map { it.toString() },
                actual.map { it.toString() },
                "columns of `$table` differ between the committed " +
                    "${VersionThreeSchema.VERSION}.json and the DDL in VersionThreeSchema.\n" +
                    "committed: ${expected.joinToString()}\n" +
                    "hand-written: ${actual.joinToString()}",
            )
        }
    }

    @Test
    fun everyIndexInTheHandWrittenSchemaMatchesTheCommittedOne() {
        val committed = committedIndexes()
        val (_, made) = buildSchema()

        for ((table, expected) in committed) {
            // A missing index still leaves a store that *works*, so nothing else in this
            // suite would notice it — which is exactly why it is compared rather than
            // assumed. Room's own validation is what turns the difference into a failure at
            // open time rather than into a slow query later.
            val actual = made[table] ?: emptyList()
            assertEquals(
                expected.sorted(),
                actual.sorted(),
                "indices on `$table` differ between the committed " +
                    "${VersionThreeSchema.VERSION}.json and VersionThreeSchema.\n" +
                    "committed: ${expected.sorted()}\n" +
                    "hand-written: ${actual.sorted()}",
            )
        }
    }

    // --- what SQLite actually made ---------------------------------------------------------------

    private fun buildSchema(): Pair<
        Map<String, List<VersionThreeSchema.Column>>,
        Map<String, List<String>>,
        > {
        val path = System.getProperty("java.io.tmpdir") + "/TaskTracker-drift-${System.nanoTime()}.store"
        deleteStoreFile(path)
        val connection = BundledSQLiteDriver().open(path)
        try {
            VersionThreeSchema.create(connection)
            val columns = VersionThreeSchema.tables.associateWith {
                VersionThreeSchema.columnsOf(connection, it)
            }
            val indexes = VersionThreeSchema.tables.associateWith {
                VersionThreeSchema.indexesOf(connection, it)
            }
            return columns to indexes
        } finally {
            connection.close()
            deleteStoreFile(path)
        }
    }

    // --- what the committed JSON says -------------------------------------------------------------

    private fun committedTables(): Map<String, List<VersionThreeSchema.Column>> {
        val json = readCommittedSchema()
        return json.jsonObject["database"]!!.jsonObject["entities"]!!.jsonArray
            .associate { entity ->
                val table = entity.jsonObject
                val fields = table["fields"]!!.jsonArray
                val primaryKey = table["primaryKey"]!!.jsonObject["columnNames"]!!.jsonArray
                    .map { it.jsonPrimitive.content }
                table["tableName"]!!.jsonPrimitive.content to fields.map { field ->
                    val f = field.jsonObject
                    val name = f["columnName"]!!.jsonPrimitive.content
                    VersionThreeSchema.Column(
                        name = name,
                        // Room writes the SQLite affinity, under `affinity`. A field's
                        // `type` key does not exist, and `!!` on it is an NPE rather than
                        // a readable failure - which is how the first version of this
                        // reported "java.lang.NullPointerException" and nothing else.
                        type = f["affinity"]!!.jsonPrimitive.content,
                        notNull = f["notNull"]?.jsonPrimitive?.content?.toBoolean() ?: false,
                        // Both sides carry the SQL literal with its quotes
                        // intact: Room writes what SQLite reports, so `DEFAULT ''` is
                        // `''` in the JSON and `''` from `PRAGMA table_info`. Stripping
                        // the quotes on one side only makes every defaulted column a
                        // permanent difference — which is how a guard that reports
                        // something on every run gets ignored.
                        defaultValue = f["defaultValue"]?.jsonPrimitive?.content,
                        primaryKeyPosition =
                            if (name in primaryKey) primaryKey.indexOf(name) + 1 else 0,
                    )
                }
            }
    }

    private fun committedIndexes(): Map<String, List<String>> =
        readCommittedSchema().jsonObject["database"]!!.jsonObject["entities"]!!.jsonArray
            .associate { entity ->
                val table = entity.jsonObject
                val name = table["tableName"]!!.jsonPrimitive.content
                // `autoGenerate` indices are SQLite's own for a UNIQUE or INTEGER PRIMARY
                // KEY and have no entry here; comparing them would compare SQLite's
                // behaviour rather than this project's schema.
                val indices = table["indices"]?.jsonArray.orEmpty()
                name to indices.map { index ->
                    val i = index.jsonObject
                    val unique = i["unique"]?.jsonPrimitive?.content?.toBoolean() ?: false
                    "${i["name"]!!.jsonPrimitive.content}${if (unique) " UNIQUE" else ""}" +
                        "(${i["columnNames"]!!.jsonArray.joinToString(", ") { it.jsonPrimitive.content }})"
                }
            }

    private fun readCommittedSchema(): JsonObject {
        if (!schemaFile.isFile) {
            fail("no committed schema at ${schemaFile.absolutePath}; the drift guard has nothing to compare")
        }
        return JSONCoding.json.parseToJsonElement(schemaFile.readText()) as JsonObject
    }
}