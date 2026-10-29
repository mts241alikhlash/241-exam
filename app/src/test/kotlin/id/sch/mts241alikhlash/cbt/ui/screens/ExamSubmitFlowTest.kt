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
import id.sch.mts241alikhlash.cbt.data.model.SubmitRequest
import id.sch.mts241alikhlash.cbt.data.model.SubmittedSessionDto
import id.sch.mts241alikhlash.cbt.data.remote.ExamApiService
import id.sch.mts241alikhlash.cbt.data.remote.NETWORK_ERROR_MESSAGE
import id.sch.mts241alikhlash.cbt.data.repository.ExamRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
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
import java.io.IOException

@OptIn(ExperimentalCoroutinesApi::class)
class ExamSubmitFlowTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private fun pending(vararg ids: String) = ids.map {
        CachedAnswerEntity(it, "s1", AnswerPayload.Choice("A").encode(), isSynced = false, updatedAt = 1_000L)
    }.toTypedArray()

    private class FlowApi(
        var submitFailures: Int = 0,
        var answerError: Exception? = null
    ) : ExamApiService {
        val events = mutableListOf<String>()
        var submitCalls = 0

        override suspend fun getQuestions(sessionId: String): List<PaperQuestionDto> =
            listOf(PaperQuestionDto("q1", 1, "MULTIPLE_CHOICE", "x", 1.0, choices("a", "b")))

        override suspend fun getAnswers(sessionId: String): List<SavedAnswerDto> = emptyList()

        override suspend fun saveAnswer(sessionId: String, request: SaveAnswerRequest): SavedAnswerAckDto {
            events += "answer:${request.questionId}"
            answerError?.let { throw it }
            return ackFor(request)
        }

        override suspend fun submit(sessionId: String, request: SubmitRequest): SubmittedSessionDto {
            events += "submit"
            submitCalls++
            if (submitFailures > 0) {
                submitFailures--
                throw IOException("offline")
            }
            return SubmittedSessionDto("GRADED")
        }
    }

    private fun TestScope.newViewModel(api: FlowApi, dao: InMemoryAnswerDao, deadline: Long? = null) =
        ExamViewModel(
            repository = ExamRepository(api, dao, EmptyQuestionDao()),
            sessionId = "s1",
            deadline = deadline,
            now = { testScheduler.currentTime }
        )

    @Test
    fun `submit syncs the queue and then posts submit`() = runTest {
        val api = FlowApi()
        val vm = newViewModel(api, InMemoryAnswerDao(*pending("q1")))
        advanceUntilIdle()

        vm.submitExam()
        advanceUntilIdle()

        assertEquals(listOf("answer:q1", "submit"), api.events)
        assertTrue(vm.uiState.value.isSubmitted)
        assertNull(vm.uiState.value.submitError)
    }

    @Test
    fun `submitting twice leaves the screen submitted and posts once`() = runTest {
        val api = FlowApi()
        val vm = newViewModel(api, InMemoryAnswerDao())
        advanceUntilIdle()

        vm.submitExam()
        advanceUntilIdle()
        vm.submitExam()
        advanceUntilIdle()

        assertEquals(1, api.submitCalls)
        assertTrue(vm.uiState.value.isSubmitted)
    }

    @Test
    fun `a failed submit keeps the exam open and can be retried`() = runTest {
        val api = FlowApi(submitFailures = 1)
        val vm = newViewModel(api, InMemoryAnswerDao())
        runCurrent()

        vm.submitExam()
        runCurrent()
        assertFalse(vm.uiState.value.isSubmitted)
        assertEquals(NETWORK_ERROR_MESSAGE, vm.uiState.value.submitError)

        vm.submitExam()
        runCurrent()
        assertTrue(vm.uiState.value.isSubmitted)
    }

    @Test
    fun `a closed session still hands in so the server can finish it`() = runTest {
        val closed = HttpException(
            Response.error<Any>(
                409,
                """{"statusCode":409,"message":"Waktu pengiriman jawaban sudah habis","data":null}""".toResponseBody("application/json".toMediaType())
            )
        )
        val api = FlowApi(answerError = closed)
        val vm = newViewModel(api, InMemoryAnswerDao(*pending("q1")))
        advanceUntilIdle()

        vm.submitExam()
        advanceUntilIdle()

        assertEquals(1, api.submitCalls)
        assertTrue(vm.uiState.value.isSubmitted)
    }

    @Test
    fun `time over submits once automatically`() = runTest {
        val api = FlowApi()
        val vm = newViewModel(api, InMemoryAnswerDao(), deadline = 2_000L)
        runCurrent()

        advanceTimeBy(3_000L)
        runCurrent()
        advanceTimeBy(20_000L)
        runCurrent()

        assertTrue(vm.uiState.value.isTimeOver)
        assertTrue(vm.uiState.value.isSubmitted)
        assertEquals(1, api.submitCalls)
    }

    @Test
    fun `time over keeps retrying until the submit goes through`() = runTest {
        val api = FlowApi(submitFailures = 2)
        val vm = newViewModel(api, InMemoryAnswerDao(), deadline = 1_000L)
        runCurrent()

        advanceTimeBy(30_000L)
        runCurrent()

        assertTrue(vm.uiState.value.isSubmitted)
        assertEquals(3, api.submitCalls)
    }

    @Test
    fun `answers are frozen once time is over`() = runTest {
        val api = FlowApi(submitFailures = 100)
        val vm = newViewModel(api, InMemoryAnswerDao(), deadline = 1_000L)
        runCurrent()

        advanceTimeBy(2_000L)
        runCurrent()

        assertFalse(vm.uiState.value.canAnswer)
    }

    @Test
    fun `automatic submit gives up after the grace window and offers a manual retry`() = runTest {
        val api = FlowApi(submitFailures = 1000)
        val vm = newViewModel(api, InMemoryAnswerDao(), deadline = 1_000L)
        runCurrent()

        advanceUntilIdle()

        assertFalse(vm.uiState.value.isSubmitted)
        assertEquals(ExamViewModel.AUTO_SUBMIT_MAX_ATTEMPTS, api.submitCalls)
        assertTrue(vm.uiState.value.canSubmit)
    }
}
