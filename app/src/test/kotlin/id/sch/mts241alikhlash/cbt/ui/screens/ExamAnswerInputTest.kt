package id.sch.mts241alikhlash.cbt.ui.screens

import id.sch.mts241alikhlash.cbt.EmptyQuestionDao
import id.sch.mts241alikhlash.cbt.InMemoryAnswerDao
import id.sch.mts241alikhlash.cbt.MainDispatcherRule
import id.sch.mts241alikhlash.cbt.ackFor
import id.sch.mts241alikhlash.cbt.choices
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
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ExamAnswerInputTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private class RecordingApi : ExamApiService {
        val sent = mutableListOf<SaveAnswerRequest>()
        val questions = listOf(
            PaperQuestionDto("q1", 1, "MULTIPLE_CHOICE", "x", 1.0, choices("a", "b", "c")),
            PaperQuestionDto("q2", 2, "ESSAY", "y", 5.0, null)
        )

        override suspend fun getQuestions(sessionId: String) = questions
        override suspend fun getAnswers(sessionId: String): List<SavedAnswerDto> = emptyList()
        override suspend fun saveAnswer(sessionId: String, request: SaveAnswerRequest): SavedAnswerAckDto {
            sent += request
            return ackFor(request)
        }
        override suspend fun submit(sessionId: String) = SubmittedSessionDto("GRADED")
    }

    private fun TestScope.newViewModel(api: RecordingApi) = ExamViewModel(
        repository = ExamRepository(api, InMemoryAnswerDao(), EmptyQuestionDao(), now = { testScheduler.currentTime }),
        sessionId = "s1",
        deadline = 60 * 60 * 1000L,
        now = { testScheduler.currentTime }
    )

    private fun RecordingApi.last() = AnswerPayload.fromJson(sent.last().answer)

    @Test
    fun `single choice sends its key`() = runTest {
        val api = RecordingApi()
        val vm = newViewModel(api)
        runCurrent()

        vm.selectAnswer("q1", AnswerPayload.Choice("B"))
        runCurrent()

        assertEquals(AnswerPayload.Choice("B"), api.last())
        assertEquals(AnswerPayload.Choice("B"), vm.uiState.value.answers["q1"])
    }

    @Test
    fun `true false sends a bool`() = runTest {
        val api = RecordingApi()
        val vm = newViewModel(api)
        runCurrent()

        vm.selectAnswer("q1", AnswerPayload.Bool(false))
        runCurrent()

        assertEquals(AnswerPayload.Bool(false), api.last())
    }

    @Test
    fun `multiple response toggles keys and keeps them sorted`() = runTest {
        val api = RecordingApi()
        val vm = newViewModel(api)
        runCurrent()

        vm.toggleKey("q1", "C")
        runCurrent()
        vm.toggleKey("q1", "A")
        runCurrent()
        assertEquals(AnswerPayload.Multiple(listOf("A", "C")), api.last())

        vm.toggleKey("q1", "A")
        runCurrent()
        assertEquals(AnswerPayload.Multiple(listOf("C")), api.last())
    }

    @Test
    fun `text is saved after the debounce`() = runTest {
        val api = RecordingApi()
        val vm = newViewModel(api)
        runCurrent()

        vm.updateText("q2", "jawab")
        advanceTimeBy(ExamViewModel.TEXT_DEBOUNCE_MS - 1)
        runCurrent()
        assertTrue(api.sent.isEmpty())
        assertEquals("jawab", vm.uiState.value.drafts["q2"])

        advanceTimeBy(2)
        runCurrent()
        assertEquals(AnswerPayload.Text("jawab"), api.last())
    }

    @Test
    fun `only the last typed text is sent`() = runTest {
        val api = RecordingApi()
        val vm = newViewModel(api)
        runCurrent()

        vm.updateText("q2", "a")
        advanceTimeBy(100)
        vm.updateText("q2", "ab")
        advanceTimeBy(ExamViewModel.TEXT_DEBOUNCE_MS + 10)
        runCurrent()

        assertEquals(listOf(AnswerPayload.Text("ab")), api.sent.map { AnswerPayload.fromJson(it.answer) })
    }

    @Test
    fun `leaving the question saves the pending text at once`() = runTest {
        val api = RecordingApi()
        val vm = newViewModel(api)
        runCurrent()
        vm.goToIndex(1)

        vm.updateText("q2", "belum selesai")
        vm.goToIndex(0)
        runCurrent()

        assertEquals(AnswerPayload.Text("belum selesai"), api.last())
    }

    @Test
    fun `submit saves the pending text first`() = runTest {
        val api = RecordingApi()
        val vm = newViewModel(api)
        runCurrent()

        vm.updateText("q2", "akhir")
        vm.submitExam()
        runCurrent()

        assertEquals(AnswerPayload.Text("akhir"), api.last())
        assertTrue(vm.uiState.value.isSubmitted)
    }

    @Test
    fun `text longer than the server limit is cut`() = runTest {
        val api = RecordingApi()
        val vm = newViewModel(api)
        runCurrent()

        vm.updateText("q2", "x".repeat(25_000))

        assertEquals(ExamViewModel.MAX_TEXT_LENGTH, vm.uiState.value.drafts["q2"]?.length)
    }

    @Test
    fun `marking a question doubtful without an answer sends nothing`() = runTest {
        val api = RecordingApi()
        val vm = newViewModel(api)
        runCurrent()

        vm.toggleDoubtful("q1")
        runCurrent()

        assertTrue(api.sent.isEmpty())
        assertTrue("q1" in vm.uiState.value.doubtfuls)
    }

    @Test
    fun `a doubt mark followed at once by a choice keeps the mark`() = runTest {
        val dao = StaleDao()
        val vm = ExamViewModel(
            repository = ExamRepository(RecordingApi(), dao, EmptyQuestionDao(), now = { testScheduler.currentTime }),
            sessionId = "s1",
            deadline = 60 * 60 * 1000L,
            now = { testScheduler.currentTime }
        )
        runCurrent()

        vm.toggleDoubtful("q1")
        vm.selectAnswer("q1", AnswerPayload.Choice("A"))
        runCurrent()

        assertTrue(dao.rows.getValue("q1").isDoubtful)
    }

    @Test
    fun `two quick doubt toggles end with the mark off`() = runTest {
        val dao = StaleDao()
        val vm = ExamViewModel(
            repository = ExamRepository(RecordingApi(), dao, EmptyQuestionDao(), now = { testScheduler.currentTime }),
            sessionId = "s1",
            deadline = 60 * 60 * 1000L,
            now = { testScheduler.currentTime }
        )
        runCurrent()

        vm.toggleDoubtful("q1")
        vm.toggleDoubtful("q1")
        runCurrent()

        assertTrue(!dao.rows.getValue("q1").isDoubtful)
    }
}

private class StaleDao : InMemoryAnswerDao() {
    override fun observeAnswers(sessionId: String): kotlinx.coroutines.flow.Flow<List<id.sch.mts241alikhlash.cbt.data.local.CachedAnswerEntity>> =
        kotlinx.coroutines.flow.flowOf(emptyList())
}
