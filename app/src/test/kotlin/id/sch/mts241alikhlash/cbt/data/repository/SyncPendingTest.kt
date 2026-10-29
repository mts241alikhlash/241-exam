package id.sch.mts241alikhlash.cbt.data.repository

import id.sch.mts241alikhlash.cbt.ackFor
import id.sch.mts241alikhlash.cbt.data.local.AnswerDao
import id.sch.mts241alikhlash.cbt.data.local.CachedAnswerEntity
import id.sch.mts241alikhlash.cbt.data.local.CachedQuestionEntity
import id.sch.mts241alikhlash.cbt.data.local.QuestionDao
import id.sch.mts241alikhlash.cbt.data.model.SavedAnswerAckDto
import id.sch.mts241alikhlash.cbt.data.model.SavedAnswerDto
import id.sch.mts241alikhlash.cbt.data.model.SubmitRequest
import id.sch.mts241alikhlash.cbt.data.model.SubmittedSessionDto
import id.sch.mts241alikhlash.cbt.data.model.PaperQuestionDto
import id.sch.mts241alikhlash.cbt.data.model.SaveAnswerRequest
import id.sch.mts241alikhlash.cbt.data.remote.ExamApiService
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test
import java.io.IOException

class SyncPendingTest {
    @Test
    fun `counts synced and failed answers`() = runTest {
        val dao = SyncFakeAnswerDao(unsynced = listOf(ans("q1"), ans("q2")))
        val api = SyncFakeApi(failFor = setOf("q2"))
        val report = ExamRepository(api, dao, SyncFakeQuestionDao()).syncPending("s1")
        assertEquals(1, report.synced)
        assertEquals(1, report.failed)
    }

    @Test
    fun `marks only successful answers synced`() = runTest {
        val dao = SyncFakeAnswerDao(unsynced = listOf(ans("q1"), ans("q2")))
        val api = SyncFakeApi(failFor = setOf("q2"))
        ExamRepository(api, dao, SyncFakeQuestionDao()).syncPending("s1")
        assertEquals(setOf("q1"), dao.markedSynced)
    }

    @Test
    fun `failed count comes from the queue, not from the attempt snapshot`() = runTest {
        // A row that appears mid-sync (student answered while the queue drained) must be
        // counted, otherwise the submit gate would open with an unsynced answer.
        val dao = SyncFakeAnswerDao(
            unsynced = listOf(ans("q1")),
            arriveDuringSync = ans("q2")
        )
        val api = SyncFakeApi()

        val report = ExamRepository(api, dao, SyncFakeQuestionDao()).syncPending("s1")

        assertEquals(1, report.synced)
        assertEquals("newly arrived pending answer must block submit", 1, report.failed)
    }

    @Test
    fun `retry of the same revision sends the same client id`() = runTest {
        val dao = SyncFakeAnswerDao(unsynced = listOf(ans("q1")))
        val api = SyncFakeApi(failFor = setOf("q1"))
        val repo = ExamRepository(api, dao, SyncFakeQuestionDao())

        repo.syncPending("s1")
        repo.syncPending("s1")

        assertEquals(2, api.seen.size)
        assertEquals(api.seen[0].clientId, api.seen[1].clientId)
        assertEquals(answerClientId("s1", "q1", "answer-q1", 1_000L), api.seen[0].clientId)
    }

    @Test
    fun `a newer revision of the same answer gets a different client id`() {
        assertNotEquals(
            answerClientId("s1", "q1", "a", 1_000L),
            answerClientId("s1", "q1", "a", 2_000L)
        )
    }

    @Test
    fun `a different answer at the same timestamp gets a different client id`() {
        assertNotEquals(
            answerClientId("s1", "q1", "A", 1_000L),
            answerClientId("s1", "q1", "C", 1_000L)
        )
    }

    @Test
    fun `zero pending answers reports a clean gate`() = runTest {
        val dao = SyncFakeAnswerDao(unsynced = emptyList())
        val report = ExamRepository(SyncFakeApi(), dao, SyncFakeQuestionDao()).syncPending("s1")

        assertEquals(0, report.synced)
        assertEquals(0, report.failed)
    }
}

private fun ans(
    questionId: String,
    sessionId: String = "s1",
    answer: String = "answer-$questionId",
    updatedAt: Long = 1_000L
) = CachedAnswerEntity(
    questionId = questionId,
    sessionId = sessionId,
    answer = answer,
    isSynced = false,
    updatedAt = updatedAt
)

private class SyncFakeAnswerDao(
    unsynced: List<CachedAnswerEntity> = emptyList(),
    private val arriveDuringSync: CachedAnswerEntity? = null
) : AnswerDao {
    val markedSynced = mutableSetOf<String>()
    private val rows = unsynced.associateBy { it.questionId }.toMutableMap()
    private var arrived = false

    override fun observeAnswers(sessionId: String): Flow<List<CachedAnswerEntity>> =
        flowOf(emptyList())

    override suspend fun getAnswer(sessionId: String, questionId: String): CachedAnswerEntity? =
        rows[questionId]?.takeIf { it.sessionId == sessionId }

    override suspend fun insertIfAbsent(answer: CachedAnswerEntity): Long = 1L

    override suspend fun getUnsyncedAnswers(sessionId: String): List<CachedAnswerEntity> =
        rows.values.filter { it.sessionId == sessionId && !it.isSynced }

    override suspend fun countUnsyncedAnswers(sessionId: String): Int {
        // The student answers another question while the queue drains: the row lands
        // after the attempt snapshot, so only a fresh count can see it.
        if (!arrived && arriveDuringSync != null) {
            arrived = true
            rows[arriveDuringSync.questionId] = arriveDuringSync
        }
        return rows.values.count { it.sessionId == sessionId && !it.isSynced }
    }

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
        if (row.sessionId != sessionId || row.answer != answer ||
            row.updatedAt != updatedAt || row.isSynced
        ) {
            return 0
        }
        rows[questionId] = row.copy(isSynced = true)
        markedSynced.add(questionId)
        return 1
    }
}

private class SyncFakeQuestionDao : QuestionDao {
    override suspend fun getForSession(sessionId: String): List<CachedQuestionEntity> =
        emptyList()

    override suspend fun insertAll(items: List<CachedQuestionEntity>) = Unit

    override suspend fun deleteForSession(sessionId: String) = Unit
}

private class SyncFakeApi(
    private val failFor: Set<String> = emptySet()
) : ExamApiService {
    override suspend fun getAnswers(sessionId: String): List<SavedAnswerDto> = emptyList()

    override suspend fun submit(sessionId: String, request: SubmitRequest): SubmittedSessionDto = SubmittedSessionDto("SUBMITTED")

    val seen = mutableListOf<SaveAnswerRequest>()

    override suspend fun getQuestions(sessionId: String): List<PaperQuestionDto> = emptyList()

    override suspend fun saveAnswer(
        sessionId: String,
        request: SaveAnswerRequest
    ): SavedAnswerAckDto {
        seen += request
        if (request.questionId in failFor) {
            throw IOException("network failure for ${request.questionId}")
        }
        return ackFor(request)
    }
}
