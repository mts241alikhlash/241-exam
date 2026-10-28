package id.sch.mts241alikhlash.cbt.data.local

import androidx.room.testing.MigrationTestHelper
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class ExamDatabaseMigrationTest {

    @get:Rule
    val helper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        ExamDatabase::class.java
    )

    /**
     * The exported schemas are test fixtures wired into the debug/androidTest assets only,
     * so the release APK never ships them. `testReleaseUnitTest` therefore has no schema
     * JSON to validate against and must skip rather than fail.
     */
    @Before
    fun requireSchemaAssets() {
        val assets = InstrumentationRegistry.getInstrumentation()
            .context
            .assets
        val present = runCatching {
            assets.open("$SCHEMA_DIR/1.json").close()
        }.isSuccess
        assumeTrue(
            "Room schema assets are only packaged for debug/androidTest variants",
            present
        )
    }

    @Test
    fun `migration 1 to 2 creates cached_questions with composite primary key`() {
        helper.createDatabase(TEST_DB, 1).apply {
            execSQL(
                "INSERT INTO cached_answers " +
                    "(questionId, sessionId, answer, isDoubtful, isSynced, updatedAt) " +
                    "VALUES ('q1', 's1', 'A', 0, 0, 0)"
            )
            close()
        }

        val db = helper.runMigrationsAndValidate(TEST_DB, 2, true, ExamDatabase.MIGRATION_1_2)

        db.query(
            "SELECT name FROM sqlite_master WHERE type = 'table' AND name = 'cached_questions'"
        ).use { cursor ->
            assertTrue("cached_questions table should exist", cursor.moveToFirst())
        }

        val primaryKeyColumns = mutableListOf<Pair<String, Int>>()
        db.query("PRAGMA table_info(`cached_questions`)").use { cursor ->
            val nameIndex = cursor.getColumnIndexOrThrow("name")
            val pkIndex = cursor.getColumnIndexOrThrow("pk")
            while (cursor.moveToNext()) {
                val pk = cursor.getInt(pkIndex)
                if (pk > 0) primaryKeyColumns.add(cursor.getString(nameIndex) to pk)
            }
        }
        assertEquals(
            listOf("sessionId" to 1, "id" to 2),
            primaryKeyColumns.sortedBy { it.second }
        )

        // v1 data must survive the migration.
        db.query("SELECT COUNT(*) FROM cached_answers").use { cursor ->
            cursor.moveToFirst()
            assertEquals(1, cursor.getInt(0))
        }
        db.close()
    }

    @Test
    fun `migrated cached_questions accepts composite key rows`() {
        helper.createDatabase(TEST_DB, 1).close()
        val db = helper.runMigrationsAndValidate(TEST_DB, 2, true, ExamDatabase.MIGRATION_1_2)

        db.execSQL(
            "INSERT INTO cached_questions " +
                "(sessionId, id, number, type, bodyHtml, choicesJson, cachedAt) " +
                "VALUES ('s1', 'q1', 1, 'multiple_choice', '<p>x</p>', NULL, 0)"
        )
        // Same question id in a different session is a distinct row.
        db.execSQL(
            "INSERT INTO cached_questions " +
                "(sessionId, id, number, type, bodyHtml, choicesJson, cachedAt) " +
                "VALUES ('s2', 'q1', 1, 'multiple_choice', '<p>x</p>', NULL, 0)"
        )
        db.query("SELECT COUNT(*) FROM cached_questions").use { cursor ->
            cursor.moveToFirst()
            assertEquals(2, cursor.getInt(0))
        }
        db.close()
    }

    @Test
    fun `migration 2 to 3 clears the old caches and adds the points column`() {
        helper.createDatabase(TEST_DB, 2).apply {
            execSQL(
                "INSERT INTO cached_answers " +
                    "(questionId, sessionId, answer, isDoubtful, isSynced, updatedAt) " +
                    "VALUES ('q1', 's1', 'A', 0, 0, 0)"
            )
            execSQL(
                "INSERT INTO cached_questions " +
                    "(sessionId, id, number, type, bodyHtml, choicesJson, cachedAt) " +
                    "VALUES ('s1', 'q1', 1, 'multiple_choice', '<p>x</p>', '[\"A\"]', 0)"
            )
            close()
        }

        val db = helper.runMigrationsAndValidate(TEST_DB, 3, true, ExamDatabase.MIGRATION_2_3)

        db.query("SELECT COUNT(*) FROM cached_answers").use { cursor ->
            cursor.moveToFirst()
            assertEquals(0, cursor.getInt(0))
        }
        db.query("SELECT COUNT(*) FROM cached_questions").use { cursor ->
            cursor.moveToFirst()
            assertEquals(0, cursor.getInt(0))
        }
        db.execSQL(
            "INSERT INTO cached_questions " +
                "(sessionId, id, number, type, bodyHtml, points, choicesJson, cachedAt) " +
                "VALUES ('s1', 'q1', 1, 'MULTIPLE_CHOICE', '<p>x</p>', 2.5, NULL, 0)"
        )
        db.query("SELECT points FROM cached_questions").use { cursor ->
            cursor.moveToFirst()
            assertEquals(2.5, cursor.getDouble(0), 0.0)
        }
        db.close()
    }

    private companion object {
        const val TEST_DB = "migration-test"
        const val SCHEMA_DIR = "id.sch.mts241alikhlash.cbt.data.local.ExamDatabase"
    }
}
