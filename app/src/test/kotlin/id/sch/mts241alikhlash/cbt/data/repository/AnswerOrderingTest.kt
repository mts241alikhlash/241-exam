package id.sch.mts241alikhlash.cbt.data.repository

import id.sch.mts241alikhlash.cbt.ackFor
import id.sch.mts241alikhlash.cbt.data.local.AnswerDao
import id.sch.mts241alikhlash.cbt.data.local.CachedAnswerEntity
import id.sch.mts241alikhlash.cbt.data.local.CachedQuestionEntity
import id.sch.mts241alikhlash.cbt.data.local.QuestionDao
import id.sch.mts241alikhlash.cbt.data.model.AnswerPayload
import id.sch.mts241alikhlash.cbt.data.model.PaperQuestionDto
import id.sch.mts241alikhlash.cbt.data.model.SaveAnswerRequest
import id.sch.mts241alikhlash.cbt.data.model.SavedAnswerAckDto
import id.sch.mts241alikhlash.cbt.data.model.SavedAnswerDto
import id.sch.mts241alikhlash.cbt.data.model.SubmitRequest
import id.sch.mts241alikhlash.cbt.data.model.SubmittedSessionDto
import id.sch.mts241alikhlash.cbt.data.remote.ExamApiService
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonObject
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import retrofit2.HttpException
import retrofit2.Response
import java.io.IOException

@OptIn(ExperimentalCoroutinesApi::class)
class AnswerOrderingTest {

    private fun http(code: Int, message: String) = HttpException(
        Response.error<Any>(
            code,
            """{"statusCode":$code,"message":"$message","data":null}""".toResponseBody("application/json".toMediaType())
        )
    )

    @Test
    fun `a retry of an old revision never overtakes a newer one`() = runTest {
        val dao = OrderDao()
        val api = OrderApi()
        var clock = 1_000L
        val repo = ExamRepository(api, dao, OrderQuestionDao(), now = { clock })
        val gate = CompletableDeferred<Unit>()
        api.gate = gate
        api.failFirst = true

        val first = launch { repo.recordAnswer("q1", "s1", AnswerPayload.Choice("A").encode()) }
        runCurrent()
        clock = 2_000L
        api.gate = null
        val second = launch { repo.recordAnswer("q1", "s1", AnswerPayload.Choice("B").encode()) }
        runCurrent()
        gate.complete(Unit)
        first.join()
        second.join()

        assertEquals(listOf("A", "B"), api.seen.map { (it.answer["key"] as kotlinx.serialization.json.JsonPrimitive).content })
        assertTrue(dao.rows.getValue("q1").isSynced)
        assertEquals(1, api.seen.map { it.clientId }.distinct().size - 1)
    }

    @Test
    fun `a superseded revision is not sent at all`() = runTest {
        val dao = OrderDao()
        val api = OrderApi()
        var clock = 1_000L
        val repo = ExamRepository(api, dao, OrderQuestionDao(), now = { clock })
        val gate = CompletableDeferred<Unit>()
        api.gate = gate

        val first = launch { repo.recordAnswer("q2", "s1", AnswerPayload.Choice("A").encode()) }
        runCurrent()
        clock = 2_000L
        api.gate = null
        val second = launch { repo.recordAnswer("q2", "s1", AnswerPayload.Choice("C").encode()) }
        runCurrent()
        gate.complete(Unit)
        first.join()
        second.join()

        assertEquals(listOf("A", "C"), api.seen.map { (it.answer["key"] as kotlinx.serialization.json.JsonPrimitive).content })
    }

    @Test
    fun `409 closed stops the queue and keeps the local answers`() = runTest {
        val dao = OrderDao(ans("q1"), ans("q2"))
        val api = OrderApi(errors = mapOf("q1" to http(409, "Waktu pengiriman jawaban sudah habis")))

        val report = ExamRepository(api, dao, OrderQuestionDao()).syncPending("s1")

        assertEquals("Waktu pengiriman jawaban sudah habis", report.closedMessage)
        assertEquals(1, api.seen.size)
        assertEquals(2, report.failed)
        assertFalse(dao.rows.getValue("q1").isSynced)
        assertFalse(dao.rows.getValue("q2").isSynced)
    }

    @Test
    fun `400 is sent once per pass and does not stop the queue`() = runTest {
        val dao = OrderDao(ans("q1"), ans("q2"))
        val api = OrderApi(errors = mapOf("q1" to http(400, "Bentuk jawaban tidak sesuai dengan jenis soal")))

        val report = ExamRepository(api, dao, OrderQuestionDao()).syncPending("s1")

        assertEquals(listOf("q1", "q2"), api.seen.map { it.questionId })
        assertEquals(1, report.synced)
        assertEquals(1, report.rejected)
        assertEquals(1, report.failed)
        assertEquals(null, report.closedMessage)
    }

    @Test
    fun `a network failure stops the pass at the first row`() = runTest {
        val dao = OrderDao(ans("q1"), ans("q2"), ans("q3"))
        val offline = IOException("offline")
        val api = OrderApi(errors = mapOf("q1" to offline, "q2" to offline, "q3" to offline))

        val report = ExamRepository(api, dao, OrderQuestionDao()).syncPending("s1")

        assertEquals(1, api.seen.size)
        assertEquals(3, report.failed)
    }

    @Test
    fun `a server error on one row does not stop the pass`() = runTest {
        val dao = OrderDao(ans("q1"), ans("q2"))
        val api = OrderApi(errors = mapOf("q1" to http(500, "Terjadi kesalahan")))

        val report = ExamRepository(api, dao, OrderQuestionDao()).syncPending("s1")

        assertEquals(listOf("q1", "q2"), api.seen.map { it.questionId })
        assertEquals(1, report.synced)
        assertEquals(1, report.failed)
    }

    @Test
    fun `a network failure leaves the row for the next pass`() = runTest {
        val dao = OrderDao(ans("q1"))
        val api = OrderApi(errors = mapOf("q1" to IOException("offline")))

        val report = ExamRepository(api, dao, OrderQuestionDao()).syncPending("s1")

        assertEquals(1, report.failed)
        assertEquals(0, report.rejected)
        assertEquals(null, report.closedMessage)
    }

    @Test
    fun `pulls server answers without overwriting unsent local ones`() = runTest {
        val dao = OrderDao(ans("q1", answer = AnswerPayload.Choice("A").encode()))
        val api = OrderApi(
            remote = listOf(
                SavedAnswerDto("q1", AnswerPayload.Choice("B").toJson()),
                SavedAnswerDto("q2", AnswerPayload.Multiple(listOf("C", "A")).toJson()),
                SavedAnswerDto("q3", JsonObject(emptyMap()))
            )
        )

        val imported = ExamRepository(api, dao, OrderQuestionDao(), now = { 9_000L }).pullServerAnswers("s1")

        assertEquals(1, imported)
        assertEquals(AnswerPayload.Choice("A").encode(), dao.rows.getValue("q1").answer)
        assertFalse(dao.rows.getValue("q1").isSynced)
        val pulled = dao.rows.getValue("q2")
        assertEquals(AnswerPayload.Multiple(listOf("A", "C")).encode(), pulled.answer)
        assertTrue(pulled.isSynced)
        assertEquals(null, dao.rows["q3"])
    }

    @Test
    fun `the same client id is never produced for another question`() {
        val a = answerClientId("s1", "q1", """{"key":"A"}""", 1_000L)
        val b = answerClientId("s1", "q2", """{"key":"A"}""", 1_000L)
        assertNotEquals(a, b)
    }

    private fun ans(questionId: String, answer: String = AnswerPayload.Choice("A").encode()) = CachedAnswerEntity(
        questionId = questionId,
        sessionId = "s1",
        answer = answer,
        isSynced = false,
        updatedAt = 1_000L
    )
}

private class OrderDao(vararg initial: CachedAnswerEntity) : AnswerDao {
    val rows = initial.associateBy { it.questionId }.toMutableMap()

    override fun observeAnswers(sessionId: String): Flow<List<CachedAnswerEntity>> = flowOf(emptyList())

    override suspend fun getAnswer(sessionId: String, questionId: String): CachedAnswerEntity? =
        rows[questionId]?.takeIf { it.sessionId == sessionId }

    override suspend fun getUnsyncedAnswers(sessionId: String): List<CachedAnswerEntity> =
        rows.values.filter { it.sessionId == sessionId && !it.isSynced }

    override suspend fun countUnsyncedAnswers(sessionId: String): Int =
        rows.values.count { it.sessionId == sessionId && !it.isSynced }

    override suspend fun saveAnswer(answer: CachedAnswerEntity) {
        rows[answer.questionId] = answer
    }

    override suspend fun insertIfAbsent(answer: CachedAnswerEntity): Long {
        if (rows.containsKey(answer.questionId)) return -1L
        rows[answer.questionId] = answer
        return 1L
    }

    override suspend fun markSynced(sessionId: String, questionId: String, answer: String, updatedAt: Long): Int {
        val row = rows[questionId] ?: return 0
        if (row.sessionId != sessionId || row.answer != answer || row.updatedAt != updatedAt || row.isSynced) return 0
        rows[questionId] = row.copy(isSynced = true)
        return 1
    }
}

private class OrderQuestionDao : QuestionDao {
    override suspend fun getForSession(sessionId: String): List<CachedQuestionEntity> = emptyList()
    override suspend fun insertAll(items: List<CachedQuestionEntity>) = Unit
    override suspend fun deleteForSession(sessionId: String) = Unit
}

private class OrderApi(
    private val errors: Map<String, Exception> = emptyMap(),
    private val remote: List<SavedAnswerDto> = emptyList()
) : ExamApiService {
    val seen = mutableListOf<SaveAnswerRequest>()
    var gate: CompletableDeferred<Unit>? = null
    var failFirst = false

    override suspend fun getQuestions(sessionId: String): List<PaperQuestionDto> = emptyList()

    override suspend fun getAnswers(sessionId: String): List<SavedAnswerDto> = remote

    override suspend fun saveAnswer(sessionId: String, request: SaveAnswerRequest): SavedAnswerAckDto {
        seen += request
        val wait = gate
        val firstCall = seen.size == 1
        wait?.await()
        if (failFirst && firstCall) throw IOException("offline")
        errors[request.questionId]?.let { throw it }
        return ackFor(request)
    }

    override suspend fun submit(sessionId: String, request: SubmitRequest): SubmittedSessionDto = SubmittedSessionDto("SUBMITTED")
}
