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
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException

class QuestionCacheTest {
    @Test
    fun `falls back to cache when network fails`() = runTest {
        val api = FakeApi(failure = IOException("offline"))
        val dao = FakeQuestionDao(preloaded = listOf(cached("q1")))
        val repo = ExamRepository(api, FakeAnswerDao(), dao)
        val result = repo.getQuestions("s1")
        assertEquals(listOf("q1"), result.getOrThrow().map { it.id })
    }

    @Test
    fun `network success replaces cache`() = runTest {
        val api = FakeApi(questions = listOf(dto("q9")))
        val dao = FakeQuestionDao()
        ExamRepository(api, FakeAnswerDao(), dao).getQuestions("s1")
        assertEquals(listOf("q9"), dao.saved.map { it.id })
    }

    @Test
    fun `empty cache and network failure returns failure`() = runTest {
        val repo = ExamRepository(FakeApi(failure = IOException()), FakeAnswerDao(), FakeQuestionDao())
        assertTrue(repo.getQuestions("s1").isFailure)
    }
}

private fun cached(id: String) = CachedQuestionEntity(
    sessionId = "s1",
    id = id,
    number = 1,
    type = "multiple_choice",
    bodyHtml = "<p>Soal $id</p>",
    choicesJson = null,
    cachedAt = 0L
)

private fun dto(id: String) = StudentQuestionDto(
    id = id,
    number = 1,
    type = "multiple_choice",
    bodyHtml = "<p>Soal $id</p>",
    choices = null
)

private class FakeQuestionDao(
    private val preloaded: List<CachedQuestionEntity> = emptyList()
) : QuestionDao {
    val saved = mutableListOf<CachedQuestionEntity>()

    override suspend fun replaceForSession(sessionId: String, items: List<CachedQuestionEntity>) {
        saved.clear()
        saved.addAll(items)
    }

    override suspend fun getForSession(sessionId: String): List<CachedQuestionEntity> {
        val source = if (saved.isNotEmpty()) saved else preloaded
        return source.filter { it.sessionId == sessionId }
    }

    override suspend fun insertAll(items: List<CachedQuestionEntity>) {
        saved.addAll(items)
    }

    override suspend fun deleteForSession(sessionId: String) {
        saved.removeAll { it.sessionId == sessionId }
    }
}

private class FakeAnswerDao : AnswerDao {
    override fun observeAnswers(sessionId: String): Flow<List<CachedAnswerEntity>> =
        flowOf(emptyList())

    override suspend fun getAnswer(questionId: String): CachedAnswerEntity? = null

    override suspend fun getUnsyncedAnswers(sessionId: String): List<CachedAnswerEntity> =
        emptyList()

    override suspend fun saveAnswer(answer: CachedAnswerEntity) = Unit

    override suspend fun markSynced(questionId: String) = Unit
}

private class FakeApi(
    private val questions: List<StudentQuestionDto> = emptyList(),
    private val failure: Throwable? = null
) : ExamApiService {
    override suspend fun getQuestions(sessionId: String): List<StudentQuestionDto> {
        failure?.let { throw it }
        return questions
    }

    override suspend fun saveAnswer(
        sessionId: String,
        request: SubmitAnswerRequest
    ): StudentAnswerDto = throw UnsupportedOperationException("not used in QuestionCacheTest")
}
