package id.sch.mts241alikhlash.cbt.data.repository

import id.sch.mts241alikhlash.cbt.data.local.AnswerDao
import id.sch.mts241alikhlash.cbt.data.local.CachedAnswerEntity
import id.sch.mts241alikhlash.cbt.data.local.CachedQuestionEntity
import id.sch.mts241alikhlash.cbt.data.local.QuestionDao
import id.sch.mts241alikhlash.cbt.data.model.StudentAnswerDto
import id.sch.mts241alikhlash.cbt.data.model.StudentQuestionDto
import id.sch.mts241alikhlash.cbt.data.model.SubmitAnswerRequest
import id.sch.mts241alikhlash.cbt.data.remote.ExamApiService
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
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
}

private fun ans(questionId: String, sessionId: String = "s1") = CachedAnswerEntity(
    questionId = questionId,
    sessionId = sessionId,
    answer = "answer-$questionId"
)

private class SyncFakeAnswerDao(
    private val unsynced: List<CachedAnswerEntity> = emptyList()
) : AnswerDao {
    val markedSynced = mutableSetOf<String>()

    override fun observeAnswers(sessionId: String): Flow<List<CachedAnswerEntity>> =
        flowOf(emptyList())

    override suspend fun getAnswer(questionId: String): CachedAnswerEntity? = null

    override suspend fun getUnsyncedAnswers(sessionId: String): List<CachedAnswerEntity> =
        unsynced.filter { it.sessionId == sessionId }

    override suspend fun saveAnswer(answer: CachedAnswerEntity) = Unit

    override suspend fun markSynced(questionId: String) {
        markedSynced.add(questionId)
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
    override suspend fun getQuestions(sessionId: String): List<StudentQuestionDto> = emptyList()

    override suspend fun saveAnswer(
        sessionId: String,
        request: SubmitAnswerRequest
    ): StudentAnswerDto {
        if (request.questionId in failFor) {
            throw IOException("network failure for ${request.questionId}")
        }
        return StudentAnswerDto(
            id = request.questionId,
            sessionId = sessionId,
            studentId = "student",
            questionId = request.questionId,
            answer = request.answer,
            updatedAt = "2026-10-27T00:00:00Z"
        )
    }
}
