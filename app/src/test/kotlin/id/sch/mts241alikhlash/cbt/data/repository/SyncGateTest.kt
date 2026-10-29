package id.sch.mts241alikhlash.cbt.data.repository

import id.sch.mts241alikhlash.cbt.ackFor
import id.sch.mts241alikhlash.cbt.data.local.AnswerDao
import id.sch.mts241alikhlash.cbt.data.local.CachedAnswerEntity
import id.sch.mts241alikhlash.cbt.data.local.CachedQuestionEntity
import id.sch.mts241alikhlash.cbt.data.local.QuestionDao
import id.sch.mts241alikhlash.cbt.data.model.SavedAnswerAckDto
import id.sch.mts241alikhlash.cbt.data.model.SavedAnswerDto
import id.sch.mts241alikhlash.cbt.data.model.SubmittedSessionDto
import id.sch.mts241alikhlash.cbt.data.model.PaperQuestionDto
import id.sch.mts241alikhlash.cbt.data.model.SaveAnswerRequest
import id.sch.mts241alikhlash.cbt.data.remote.ExamApiService
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.async
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException

/**
 * C2: the sync gate must reflect answers edited while an upload was in flight.
 */
class SyncGateTest {

    @Test
    fun `answer changed during upload stays pending`() = runTest {
        val dao = VersionedAnswerDao()
        val api = BlockingApi()
        val repo = ExamRepository(api, dao, EmptyQuestionDao(), now = { 1000L })

        dao.saveAnswer(
            CachedAnswerEntity(
                questionId = "q1",
                sessionId = "s1",
                answer = "A",
                isSynced = false,
                updatedAt = 1000L
            )
        )

        val report = async { repo.syncPending("s1") }
        api.awaitUploadStarted()

        // Student edits q1 while "A" is still in flight.
        dao.saveAnswer(
            CachedAnswerEntity(
                questionId = "q1",
                sessionId = "s1",
                answer = "B",
                isSynced = false,
                updatedAt = 2000L
            )
        )
        api.completeUpload()

        val syncReport = report.await()
        // The stale upload must not mark the newer revision as synced.
        assertEquals("edited answer must remain pending", 1, syncReport.failed)
        assertEquals(0, syncReport.synced)
        assertEquals("B", dao.current("q1")?.answer)
        assertEquals(false, dao.current("q1")?.isSynced)
    }

    @Test
    fun `markSynced ignores a stale version of the same answer`() = runTest {
        val dao = VersionedAnswerDao()
        dao.saveAnswer(
            CachedAnswerEntity(
                questionId = "q1",
                sessionId = "s1",
                answer = "A",
                isSynced = false,
                updatedAt = 2000L
            )
        )

        val updated = dao.markSynced("s1", "q1", "A", 1000L)

        assertEquals(0, updated)
        assertEquals(false, dao.current("q1")?.isSynced)
    }

    @Test
    fun `markSynced does not cross sessions`() = runTest {
        val dao = VersionedAnswerDao()
        dao.saveAnswer(
            CachedAnswerEntity(
                questionId = "q1",
                sessionId = "s1",
                answer = "A",
                isSynced = false,
                updatedAt = 1000L
            )
        )

        val updated = dao.markSynced("s2", "q1", "A", 1000L)

        assertEquals(0, updated)
        assertEquals(false, dao.current("q1")?.isSynced)
    }

    @Test
    fun `sync gate opens once every pending answer uploads`() = runTest {
        val dao = VersionedAnswerDao()
        dao.saveAnswer(
            CachedAnswerEntity(
                questionId = "q1",
                sessionId = "s1",
                answer = "A",
                isSynced = false,
                updatedAt = 1000L
            )
        )
        val repo = ExamRepository(UploadingApi(), dao, EmptyQuestionDao(), now = { 1000L })

        val report = repo.syncPending("s1")

        assertEquals(1, report.synced)
        assertEquals(0, report.failed)
    }
}

private class BlockingApi : ExamApiService {
    override suspend fun getAnswers(sessionId: String): List<SavedAnswerDto> = emptyList()

    override suspend fun submit(sessionId: String): SubmittedSessionDto = SubmittedSessionDto("SUBMITTED")

    private val started = CompletableDeferred<Unit>()
    private val release = CompletableDeferred<Unit>()

    suspend fun awaitUploadStarted() = started.await()
    fun completeUpload() = release.complete(Unit)

    override suspend fun getQuestions(sessionId: String): List<PaperQuestionDto> = emptyList()

    override suspend fun saveAnswer(
        sessionId: String,
        request: SaveAnswerRequest
    ): SavedAnswerAckDto {
        started.complete(Unit)
        release.await()
        return answerDto(sessionId, request)
    }
}

private class UploadingApi : ExamApiService {
    override suspend fun getAnswers(sessionId: String): List<SavedAnswerDto> = emptyList()

    override suspend fun submit(sessionId: String): SubmittedSessionDto = SubmittedSessionDto("SUBMITTED")

    override suspend fun getQuestions(sessionId: String): List<PaperQuestionDto> = emptyList()

    override suspend fun saveAnswer(
        sessionId: String,
        request: SaveAnswerRequest
    ): SavedAnswerAckDto = answerDto(sessionId, request)
}

private fun answerDto(sessionId: String, request: SaveAnswerRequest) = ackFor(request)

/** In-memory DAO that honours the markSynced predicate exactly like the SQL does. */
private class VersionedAnswerDao : AnswerDao {
    private val rows = linkedMapOf<String, CachedAnswerEntity>()

    fun current(questionId: String): CachedAnswerEntity? = rows[questionId]

    override fun observeAnswers(sessionId: String): Flow<List<CachedAnswerEntity>> =
        flowOf(rows.values.filter { it.sessionId == sessionId })

    override suspend fun getAnswer(sessionId: String, questionId: String): CachedAnswerEntity? =
        rows[questionId]?.takeIf { it.sessionId == sessionId }

    override suspend fun insertIfAbsent(answer: CachedAnswerEntity): Long = 1L

    override suspend fun getUnsyncedAnswers(sessionId: String): List<CachedAnswerEntity> =
        rows.values.filter { it.sessionId == sessionId && !it.isSynced }

    override suspend fun countUnsyncedAnswers(sessionId: String): Int =
        rows.values.count { it.sessionId == sessionId && !it.isSynced }

    override suspend fun saveAnswer(answer: CachedAnswerEntity) {
        rows[answer.questionId] = answer
    }

    override suspend fun markSynced(
        sessionId: String,
        questionId: String,
        answer: String,
        updatedAt: Long
    ): Int {
        val row = rows[questionId] ?: return 0
        val matches = row.sessionId == sessionId &&
            row.answer == answer &&
            row.updatedAt == updatedAt &&
            !row.isSynced
        if (!matches) return 0
        rows[questionId] = row.copy(isSynced = true)
        return 1
    }
}

private class EmptyQuestionDao : QuestionDao {
    override suspend fun getForSession(sessionId: String): List<CachedQuestionEntity> =
        emptyList()

    override suspend fun insertAll(items: List<CachedQuestionEntity>) = Unit

    override suspend fun deleteForSession(sessionId: String) = Unit
}
