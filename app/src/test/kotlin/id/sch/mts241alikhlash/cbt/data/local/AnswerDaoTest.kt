package id.sch.mts241alikhlash.cbt.data.local

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * Exercises the real `markSynced` SQL.
 *
 * The repository tests use an in-memory DAO that mirrors the predicate; only a Room-backed
 * test can prove the shipped UPDATE statement actually guards session, answer and version.
 */
@RunWith(RobolectricTestRunner::class)
class AnswerDaoTest {

    private lateinit var db: ExamDatabase
    private lateinit var dao: AnswerDao

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, ExamDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        dao = db.answerDao()
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun `markSynced marks the matching revision`() = runTest {
        dao.saveAnswer(answer("q1", "A", updatedAt = 100L))

        val updated = dao.markSynced("s1", "q1", "A", 100L)

        assertEquals(1, updated)
        assertTrue(dao.getAnswer("s1", "q1")!!.isSynced)
        assertEquals(0, dao.countUnsyncedAnswers("s1"))
    }

    @Test
    fun `markSynced refuses a stale version of the same question`() = runTest {
        // The student changed the answer while the previous upload was in flight.
        dao.saveAnswer(answer("q1", "B", updatedAt = 200L))

        val updated = dao.markSynced("s1", "q1", "A", 100L)

        assertEquals("stale revision must not be marked", 0, updated)
        assertFalse(dao.getAnswer("s1", "q1")!!.isSynced)
        assertEquals(1, dao.countUnsyncedAnswers("s1"))
    }

    @Test
    fun `markSynced refuses a different answer with the same timestamp`() = runTest {
        dao.saveAnswer(answer("q1", "B", updatedAt = 100L))

        val updated = dao.markSynced("s1", "q1", "A", 100L)

        assertEquals(0, updated)
        assertFalse(dao.getAnswer("s1", "q1")!!.isSynced)
    }

    @Test
    fun `markSynced refuses to touch another session`() = runTest {
        dao.saveAnswer(answer("q1", "A", updatedAt = 100L, sessionId = "s2"))

        val updated = dao.markSynced("s1", "q1", "A", 100L)

        assertEquals(0, updated)
        assertFalse(dao.getAnswer("s2", "q1")!!.isSynced)
    }

    @Test
    fun `unsynced queries are scoped to the session`() = runTest {
        dao.saveAnswer(answer("q1", "A", updatedAt = 100L, sessionId = "s1"))
        dao.saveAnswer(answer("q2", "A", updatedAt = 100L, sessionId = "s2"))

        assertEquals(1, dao.countUnsyncedAnswers("s1"))
        assertEquals(listOf("q1"), dao.getUnsyncedAnswers("s1").map { it.questionId })
    }

    private fun answer(
        questionId: String,
        answer: String,
        updatedAt: Long,
        sessionId: String = "s1"
    ) = CachedAnswerEntity(
        questionId = questionId,
        sessionId = sessionId,
        answer = answer,
        isDoubtful = false,
        isSynced = false,
        updatedAt = updatedAt
    )
}
