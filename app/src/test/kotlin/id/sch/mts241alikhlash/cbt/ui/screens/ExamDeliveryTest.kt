package id.sch.mts241alikhlash.cbt.ui.screens

import id.sch.mts241alikhlash.cbt.EmptyQuestionDao
import id.sch.mts241alikhlash.cbt.InMemoryAnswerDao
import id.sch.mts241alikhlash.cbt.MainDispatcherRule
import id.sch.mts241alikhlash.cbt.ackFor
import id.sch.mts241alikhlash.cbt.choices
import id.sch.mts241alikhlash.cbt.data.local.CachedAnswerEntity
import id.sch.mts241alikhlash.cbt.data.model.AnswerPayload
import id.sch.mts241alikhlash.cbt.data.model.PaperQuestionDto
import id.sch.mts241alikhlash.cbt.data.model.SaveAnswerRequest
import id.sch.mts241alikhlash.cbt.data.model.SavedAnswerAckDto
import id.sch.mts241alikhlash.cbt.data.model.SavedAnswerDto
import id.sch.mts241alikhlash.cbt.data.model.SubmittedSessionDto
import id.sch.mts241alikhlash.cbt.data.remote.ExamApiService
import id.sch.mts241alikhlash.cbt.data.repository.ExamRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import retrofit2.HttpException
import retrofit2.Response

@OptIn(ExperimentalCoroutinesApi::class)
class ExamDeliveryTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private fun http(code: Int, message: String) = HttpException(
        Response.error<Any>(
            code,
            """{"statusCode":$code,"message":"$message","data":null}""".toResponseBody("application/json".toMediaType())
        )
    )

    private class Api(
        var answerError: Exception? = null,
        var submitError: Exception? = null
    ) : ExamApiService {
        var submitCalls = 0
        override suspend fun getQuestions(sessionId: String) =
            listOf(PaperQuestionDto("q1", 1, "MULTIPLE_CHOICE", "x", 1.0, choices("a", "b")))

        override suspend fun getAnswers(sessionId: String): List<SavedAnswerDto> = emptyList()

        override suspend fun saveAnswer(sessionId: String, request: SaveAnswerRequest): SavedAnswerAckDto {
            answerError?.let { throw it }
            return ackFor(request)
        }

        override suspend fun submit(sessionId: String): SubmittedSessionDto {
            submitCalls++
            submitError?.let { throw it }
            return SubmittedSessionDto("EXPIRED")
        }
    }

    private fun TestScope.newViewModel(api: Api, dao: InMemoryAnswerDao = InMemoryAnswerDao(), deadline: Long = 60 * 60 * 1000L) =
        ExamViewModel(
            repository = ExamRepository(api, dao, EmptyQuestionDao(), now = { testScheduler.currentTime }),
            sessionId = "s1",
            deadline = deadline,
            now = { testScheduler.currentTime }
        )

    private val window = "Waktu pengiriman jawaban sudah habis"
    private val released = "Sesi dilepas. Mulai ujian lagi"

    @Test
    fun `a closed sending window on an answer shows a warning and keeps the exam`() = runTest {
        val vm = newViewModel(Api(answerError = http(409, window)))
        runCurrent()

        vm.selectAnswer("q1", AnswerPayload.Choice("A"))
        runCurrent()

        assertEquals(window, vm.uiState.value.deliveryWarning)
        assertNull(vm.uiState.value.lostMessage)
    }

    @Test
    fun `a released session on an answer sends the student out of the exam`() = runTest {
        val vm = newViewModel(Api(answerError = http(409, released)))
        runCurrent()

        vm.selectAnswer("q1", AnswerPayload.Choice("A"))
        runCurrent()

        assertEquals(released, vm.uiState.value.lostMessage)
    }

    @Test
    fun `a rejected answer shows the server message`() = runTest {
        val vm = newViewModel(Api(answerError = http(400, "Bentuk jawaban tidak sesuai dengan jenis soal")))
        runCurrent()

        vm.selectAnswer("q1", AnswerPayload.Choice("A"))
        runCurrent()

        assertEquals("Bentuk jawaban tidak sesuai dengan jenis soal", vm.uiState.value.deliveryWarning)
    }

    @Test
    fun `submit reports how many answers never arrived`() = runTest {
        val dao = InMemoryAnswerDao(
            CachedAnswerEntity("q1", "s1", AnswerPayload.Choice("A").encode(), isSynced = false, updatedAt = 1_000L)
        )
        val vm = newViewModel(Api(answerError = http(409, window)), dao)
        runCurrent()

        vm.submitExam()
        runCurrent()

        assertTrue(vm.uiState.value.isSubmitted)
        assertEquals(1, vm.uiState.value.unsentAnswers)
    }

    @Test
    fun `submit of a released session ends the exam instead of retrying`() = runTest {
        val api = Api(submitError = http(409, released))
        val vm = newViewModel(api)
        runCurrent()

        vm.submitExam()
        runCurrent()

        assertEquals(released, vm.uiState.value.lostMessage)
        assertFalse(vm.uiState.value.isSubmitted)
        assertEquals(1, api.submitCalls)
    }

    @Test
    fun `time over stops the automatic submit once the session is lost`() = runTest {
        val api = Api(submitError = http(409, released))
        val vm = newViewModel(api, deadline = 1_000L)
        runCurrent()

        advanceUntilIdle()

        assertEquals(released, vm.uiState.value.lostMessage)
        assertEquals(1, api.submitCalls)
    }
}
