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
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException
import kotlin.coroutines.cancellation.CancellationException

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

    @Test
    fun `cache hit does not call network`() = runTest {
        val api = FakeApi(questions = listOf(dto("q9")))
        val dao = FakeQuestionDao(preloaded = listOf(cached("q1")))
        val result = ExamRepository(api, FakeAnswerDao(), dao).getQuestions("s1")
        assertEquals(listOf("q1"), result.getOrThrow().map { it.id })
        assertEquals(0, api.calls)
    }

    @Test
    fun `sessions do not mix`() = runTest {
        val dao = FakeQuestionDao(
            preloaded = listOf(cached("q1", "s1"), cached("q2", "s2"))
        )
        val repo = ExamRepository(FakeApi(failure = IOException("offline")), FakeAnswerDao(), dao)
        assertEquals(listOf("q1"), repo.getQuestions("s1").getOrThrow().map { it.id })
        assertEquals(listOf("q2"), repo.getQuestions("s2").getOrThrow().map { it.id })
    }

    @Test
    fun `network success replaces only the requested session`() = runTest {
        val dao = FakeQuestionDao(
            preloaded = listOf(cached("old", "s1"), cached("other", "s2"))
        )
        val api = FakeApi(questions = listOf(dto("q9")))
        ExamRepository(api, FakeAnswerDao(), dao)
            .getQuestions("s1", forceRefresh = true)
            .getOrThrow()

        assertEquals(listOf("q9"), dao.saved.filter { it.sessionId == "s1" }.map { it.id })
        assertEquals(listOf("other"), dao.saved.filter { it.sessionId == "s2" }.map { it.id })
    }

    @Test
    fun `choices survive cache round trip`() = runTest {
        val api = FakeApi(questions = listOf(dto("q1").copy(choices = listOf("A", "B"))))
        val dao = FakeQuestionDao()
        ExamRepository(api, FakeAnswerDao(), dao).getQuestions("s1")

        val stored = dao.saved.single()
        assertNotNull(stored.choicesJson)

        val offline = ExamRepository(FakeApi(failure = IOException("offline")), FakeAnswerDao(), dao)
        val cachedQuestions = offline.getQuestions("s1").getOrThrow()
        assertEquals(listOf("A", "B"), cachedQuestions.single().choices)
    }

    @Test
    fun `empty remote response keeps existing cache`() = runTest {
        val dao = FakeQuestionDao(preloaded = listOf(cached("q1")))
        val api = FakeApi(questions = emptyList())
        val result = ExamRepository(api, FakeAnswerDao(), dao)
            .getQuestions("s1", forceRefresh = true)

        assertEquals(listOf("q1"), result.getOrThrow().map { it.id })
        assertEquals(listOf("q1"), dao.saved.map { it.id })
        assertEquals(0, dao.replaceCalls)
    }

    @Test
    fun `stale cache is refreshed from network`() = runTest {
        val now = 1_000_000_000L
        val dao = FakeQuestionDao(
            preloaded = listOf(
                cached("q1", cachedAt = now - ExamRepository.CACHE_TTL_MS - 1)
            )
        )
        val api = FakeApi(questions = listOf(dto("q9")))
        val result = ExamRepository(api, FakeAnswerDao(), dao, now = { now }).getQuestions("s1")

        assertEquals(listOf("q9"), result.getOrThrow().map { it.id })
        assertEquals(1, api.calls)
    }

    @Test
    fun `clearing a session removes only its cached questions`() = runTest {
        val dao = FakeQuestionDao(
            preloaded = listOf(cached("q1", "s1"), cached("q2", "s2"))
        )
        val repo = ExamRepository(FakeApi(), FakeAnswerDao(), dao)

        repo.clearQuestions("s1")

        assertEquals(emptyList<String>(), dao.saved.filter { it.sessionId == "s1" }.map { it.id })
        assertEquals(listOf("q2"), dao.saved.filter { it.sessionId == "s2" }.map { it.id })
    }

    @Test
    fun `cancellation is not swallowed`() = runTest {
        val api = FakeApi(failure = CancellationException("cancelled"))
        val repo = ExamRepository(api, FakeAnswerDao(), FakeQuestionDao())

        var caught: Throwable? = null
        try {
            repo.getQuestions("s1")
        } catch (e: CancellationException) {
            caught = e
        }

        assertNotNull(caught)
    }
}

private fun cached(
    id: String,
    sessionId: String = "s1",
    cachedAt: Long = System.currentTimeMillis()
) = CachedQuestionEntity(
    sessionId = sessionId,
    id = id,
    number = 1,
    type = "multiple_choice",
    bodyHtml = "<p>Soal $id</p>",
    choicesJson = null,
    cachedAt = cachedAt
)

private fun dto(id: String) = StudentQuestionDto(
    id = id,
    number = 1,
    type = "multiple_choice",
    bodyHtml = "<p>Soal $id</p>",
    choices = null
)

private class FakeQuestionDao(
    preloaded: List<CachedQuestionEntity> = emptyList()
) : QuestionDao {
    private val store = preloaded.toMutableList()
    var replaceCalls = 0
        private set

    val saved: List<CachedQuestionEntity> get() = store.toList()

    override suspend fun replaceForSession(sessionId: String, items: List<CachedQuestionEntity>) {
        replaceCalls++
        store.removeAll { it.sessionId == sessionId }
        store.addAll(items)
    }

    override suspend fun getForSession(sessionId: String): List<CachedQuestionEntity> =
        store.filter { it.sessionId == sessionId }.sortedBy { it.number }

    override suspend fun insertAll(items: List<CachedQuestionEntity>) {
        store.addAll(items)
    }

    override suspend fun deleteForSession(sessionId: String) {
        store.removeAll { it.sessionId == sessionId }
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
    var calls = 0
        private set

    override suspend fun getQuestions(sessionId: String): List<StudentQuestionDto> {
        calls++
        failure?.let { throw it }
        return questions
    }

    override suspend fun saveAnswer(
        sessionId: String,
        request: SubmitAnswerRequest
    ): StudentAnswerDto = throw UnsupportedOperationException("not used in QuestionCacheTest")
}
