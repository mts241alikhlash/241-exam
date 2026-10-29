package id.sch.mts241alikhlash.cbt.ui.screens

import id.sch.mts241alikhlash.cbt.choices
import id.sch.mts241alikhlash.cbt.ackFor
import id.sch.mts241alikhlash.cbt.MainDispatcherRule
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
import id.sch.mts241alikhlash.cbt.data.repository.ExamRepository
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import java.io.IOException

@OptIn(ExperimentalCoroutinesApi::class)
class ExamViewModelSubmitTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    @Test
    fun `submit blocked when answers still pending`() = runTest {
        val vm = viewModel(repoWithPending(1))
        advanceUntilIdle()

        vm.submitExam()
        advanceUntilIdle()

        assertFalse(vm.uiState.value.isSubmitted)
        assertEquals("1 jawaban belum terkirim. Coba lagi.", vm.uiState.value.submitError)
    }

    @Test
    fun `submit succeeds when all synced`() = runTest {
        val vm = viewModel(repoWithPending(0))
        advanceUntilIdle()

        vm.submitExam()
        advanceUntilIdle()

        assertTrue(vm.uiState.value.isSubmitted)
        assertNull(vm.uiState.value.submitError)
    }

    /** Cheap-test-quality fix: uploads that all succeed must submit even with a queue. */
    @Test
    fun `submit succeeds when every pending answer uploads`() = runTest {
        val vm = viewModel(repoWithPending(pending = 2, failUploads = false))
        advanceUntilIdle()

        vm.submitExam()
        advanceUntilIdle()

        assertTrue("all uploads succeeded, submit must go through", vm.uiState.value.isSubmitted)
        assertNull(vm.uiState.value.submitError)
    }

    /** C2: gate must re-query the queue, not trust the pre-sync snapshot. */
    @Test
    fun `submit blocked when an answer is still pending after sync`() = runTest {
        // The API accepts the upload but the DAO refuses to mark it (a newer revision
        // arrived), so the post-sync count is non-zero even though nothing threw.
        // Counting exceptions instead of re-reading the queue would wrongly submit here.
        val vm = viewModel(repoWithPending(1, failUploads = false, markSucceeds = false))
        advanceUntilIdle()

        vm.submitExam()
        advanceUntilIdle()

        assertFalse("unsynced row must block submit", vm.uiState.value.isSubmitted)
        assertEquals("1 jawaban belum terkirim. Coba lagi.", vm.uiState.value.submitError)
    }

    /** I6: while the sync runs, the UI must be locked and no second submit allowed. */
    @Test
    fun `submitting flag is set while the sync is in flight`() = runTest {
        val api = BlockingSubmitApi()
        val vm = viewModel(repoWithPending(1, api = api))
        advanceUntilIdle()

        vm.submitExam()
        api.awaitUploadStarted()

        assertTrue("isSubmitting must be true during submit", vm.uiState.value.isSubmitting)
        assertFalse(vm.uiState.value.isSubmitted)
        assertFalse("inputs must be disabled while submitting", vm.uiState.value.canAnswer)
        assertFalse("submit must be disabled while submitting", vm.uiState.value.canSubmit)

        api.completeUpload()
        advanceUntilIdle()
        assertFalse(vm.uiState.value.isSubmitting)
    }

    @Test
    fun `concurrent submit taps only run one sync`() = runTest {
        val api = BlockingSubmitApi()
        val vm = viewModel(repoWithPending(1, api = api))
        advanceUntilIdle()

        vm.submitExam()
        api.awaitUploadStarted()
        vm.submitExam() // second tap while the first is still running
        vm.submitExam()

        api.completeUpload()
        advanceUntilIdle()

        assertEquals("only one upload may be in flight", 1, api.uploadCount)
    }

    @Test
    fun `submit failure surfaces a message instead of crashing`() = runTest {
        val vm = viewModel(repoWithPending(1, syncThrows = true))
        advanceUntilIdle()

        vm.submitExam()
        advanceUntilIdle()

        assertFalse(vm.uiState.value.isSubmitted)
        assertFalse(vm.uiState.value.isSubmitting)
        assertEquals(ExamViewModel.SUBMIT_ERROR_MESSAGE, vm.uiState.value.submitError)
    }

    /** C1: submitting must be impossible before the paper has loaded. */
    @Test
    fun `submit is rejected while the paper is still loading`() = runTest {
        val api = BlockingQuestionsApi()
        val vm = viewModel(repoWithQuestions(api = api))

        assertTrue(vm.uiState.value.isLoading)
        assertFalse("submit must be disabled before load", vm.uiState.value.canSubmit)

        vm.submitExam()
        runCurrent()

        assertFalse(vm.uiState.value.isSubmitted)
        assertEquals(0, api.uploadCount)
    }

    @Test
    fun `submit is rejected when the paper failed to load`() = runTest {
        val vm = viewModel(repoWithQuestions(api = FailingQuestionsApi(failFirst = true)))
        runCurrent()

        assertNotNull(vm.uiState.value.errorMessage)
        assertFalse(vm.uiState.value.canSubmit)

        vm.submitExam()
        runCurrent()

        assertFalse(vm.uiState.value.isSubmitted)
    }

    /** C1: empty payload for an active session is an error, not a blank paper. */
    @Test
    fun `empty paper becomes an error state with retry available`() = runTest {
        val vm = viewModel(repoWithQuestions())
        advanceUntilIdle()

        val state = vm.uiState.value
        assertFalse(state.isLoading)
        assertEquals(ExamViewModel.EMPTY_PAPER_MESSAGE, state.errorMessage)
        assertFalse(state.canSubmit)
        assertTrue(state.questions.isEmpty())
    }

    /** C1: retry forces a network fetch and clears the error when it succeeds. */
    @Test
    fun `retry reloads with forceRefresh and recovers`() = runTest {
        val api = FailingQuestionsApi(failFirst = true)
        val vm = viewModel(repoWithQuestions(api = api))
        runCurrent()

        assertNotNull(vm.uiState.value.errorMessage)
        assertEquals(1, api.questionCalls)

        vm.retry()
        runCurrent()

        val state = vm.uiState.value
        assertNull("error must clear after a successful retry", state.errorMessage)
        assertEquals(listOf("q1"), state.questions.map { it.id })
        assertTrue(state.canSubmit)
        assertEquals("retry must bypass the cache and hit the network", 2, api.questionCalls)
    }

    /** C3: a restored session resumes from its stored deadline, not from 90:00. */
    @Test
    fun `resumed session starts from the persisted deadline`() = runTest {
        val vm = viewModel(repoWithPending(0), deadline = 30L * 60L * 1000L)
        runCurrent()

        assertEquals(1800L, vm.uiState.value.remainingSeconds)
        assertTrue("a shortened clock must show the resumed notice", vm.uiState.value.isResumed)
    }

    @Test
    fun `fresh session is not flagged as resumed`() = runTest {
        val vm = viewModel(repoWithPending(0), deadline = 90L * 60L * 1000L)
        runCurrent()

        assertEquals(5400L, vm.uiState.value.remainingSeconds)
        assertFalse(vm.uiState.value.isResumed)
    }

    /**
     * R2: the deadline is written at start, read back from DataStore, then handed to the
     * ViewModel — so the ViewModel always observes a few ms of latency. A brand-new exam
     * must still read 90:00 and must not show the "Ujian dilanjutkan" banner.
     */
    @Test
    fun `startup latency does not turn a fresh exam into a resumed one`() = runTest {
        val deadline = 90L * 60L * 1000L
        testScheduler.advanceTimeBy(50L) // process start-up cost
        val vm = viewModel(repoWithPending(0), deadline = deadline)
        runCurrent()

        assertEquals("must open at 90:00, not 89:59", 5400L, vm.uiState.value.remainingSeconds)
        assertFalse("fresh exam must not show the resumed banner", vm.uiState.value.isResumed)
    }

    @Test
    fun `a genuinely resumed exam still shows the banner`() = runTest {
        val deadline = 90L * 60L * 1000L
        testScheduler.advanceTimeBy(10L * 60L * 1000L) // app away for ten minutes
        val vm = viewModel(repoWithPending(0), deadline = deadline)
        runCurrent()

        assertEquals(4800L, vm.uiState.value.remainingSeconds)
        assertTrue(vm.uiState.value.isResumed)
    }

    @Test
    fun `expired deadline clamps to zero`() = runTest {
        val vm = viewModel(repoWithPending(0), deadline = -60_000L)
        runCurrent()

        assertEquals(0L, vm.uiState.value.remainingSeconds)
    }

    @Test
    fun `timer counts down from the deadline`() = runTest {
        val vm = viewModel(repoWithPending(0), deadline = 90L * 60L * 1000L)
        runCurrent()
        assertEquals(5400L, vm.uiState.value.remainingSeconds)

        advanceTimeBySeconds(30)

        assertEquals(5370L, vm.uiState.value.remainingSeconds)
    }

    /**
     * Builds the ViewModel against the test scheduler's clock so the deadline-based
     * countdown terminates under virtual time instead of spinning on a frozen clock.
     */
    private fun TestScope.viewModel(
        repository: ExamRepository,
        sessionId: String = "s1",
        deadline: Long? = null
    ) = ExamViewModel(
        repository = repository,
        sessionId = sessionId,
        deadline = deadline,
        now = { testScheduler.currentTime }
    )

    private fun TestScope.advanceTimeBySeconds(seconds: Long) {
        testScheduler.advanceTimeBy(seconds * 1000L)
        testScheduler.runCurrent()
    }
}

private fun repoWithPending(
    pending: Int,
    failUploads: Boolean = true,
    markSucceeds: Boolean = true,
    api: ExamApiService? = null,
    syncThrows: Boolean = false
): ExamRepository {
    val answers = (1..pending).map { index ->
        CachedAnswerEntity(
            questionId = "q$index",
            sessionId = "s1",
            answer = "answer-$index",
            isSynced = false,
            updatedAt = 1000L + index
        )
    }
    val effectiveApi = api ?: if (failUploads) SubmitFakeApi() else RecordingSubmitApi()
    return ExamRepository(
        api = effectiveApi,
        dao = SubmitFakeAnswerDao(answers, markSucceeds = markSucceeds, syncThrows = syncThrows),
        // A loaded paper: submit is only reachable once questions are on screen.
        questionDao = SubmitFakeQuestionDao(listOf(dto("q1")))
    )
}

private fun repoWithQuestions(
    questions: List<PaperQuestionDto> = emptyList(),
    api: ExamApiService = RecordingQuestionsApi(questions)
): ExamRepository = ExamRepository(
    api = api,
    dao = SubmitFakeAnswerDao(emptyList()),
    questionDao = SubmitFakeQuestionDao(emptyList())
)

private fun dto(id: String) = PaperQuestionDto(
    id = id,
    number = 1,
    type = "multiple_choice",
    bodyHtml = "<p>Soal $id</p>",
    points = 1.0,
    choices = choices("A", "B")
)

private class SubmitFakeAnswerDao(
    unsynced: List<CachedAnswerEntity>,
    private val markSucceeds: Boolean = true,
    private val syncThrows: Boolean = false
) : AnswerDao {
    private val rows = unsynced.associateBy { it.questionId }.toMutableMap()

    override fun observeAnswers(sessionId: String): Flow<List<CachedAnswerEntity>> =
        flowOf(rows.values.filter { it.sessionId == sessionId })

    override suspend fun getAnswer(sessionId: String, questionId: String): CachedAnswerEntity? =
        rows[questionId]?.takeIf { it.sessionId == sessionId }

    override suspend fun insertIfAbsent(answer: CachedAnswerEntity): Long = 1L

    override suspend fun getUnsyncedAnswers(sessionId: String): List<CachedAnswerEntity> {
        if (syncThrows) throw IllegalStateException("database closed")
        return rows.values.filter { it.sessionId == sessionId && !it.isSynced }
    }

    override suspend fun countUnsyncedAnswers(sessionId: String): Int {
        if (syncThrows) throw IllegalStateException("database closed")
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
        if (!markSucceeds) return 0
        val row = rows[questionId] ?: return 0
        if (row.sessionId != sessionId || row.answer != answer ||
            row.updatedAt != updatedAt || row.isSynced
        ) {
            return 0
        }
        rows[questionId] = row.copy(isSynced = true)
        return 1
    }
}

private class SubmitFakeQuestionDao(
    preloaded: List<PaperQuestionDto> = emptyList()
) : QuestionDao {
    private val store = preloaded.toMutableList()

    override suspend fun getForSession(sessionId: String): List<CachedQuestionEntity> =
        store.map {
            CachedQuestionEntity(
                sessionId = sessionId,
                id = it.id,
                number = it.number,
                type = it.type,
                bodyHtml = it.bodyHtml,
                points = it.points,
                choicesJson = null,
                cachedAt = 0L
            )
        }

    override suspend fun insertAll(items: List<CachedQuestionEntity>) = Unit

    override suspend fun deleteForSession(sessionId: String) = Unit
}

/** Every answer upload fails, so any pending answer is reported as failed. */
private class SubmitFakeApi : ExamApiService {
    override suspend fun getAnswers(sessionId: String): List<SavedAnswerDto> = emptyList()

    override suspend fun submit(sessionId: String): SubmittedSessionDto = SubmittedSessionDto("SUBMITTED")

    override suspend fun getQuestions(sessionId: String): List<PaperQuestionDto> = emptyList()

    override suspend fun saveAnswer(
        sessionId: String,
        request: SaveAnswerRequest
    ): SavedAnswerAckDto = throw IOException("offline")
}

/** Every answer upload succeeds. */
private class RecordingSubmitApi : ExamApiService {
    override suspend fun getAnswers(sessionId: String): List<SavedAnswerDto> = emptyList()

    override suspend fun submit(sessionId: String): SubmittedSessionDto = SubmittedSessionDto("SUBMITTED")

    override suspend fun getQuestions(sessionId: String): List<PaperQuestionDto> = emptyList()

    override suspend fun saveAnswer(
        sessionId: String,
        request: SaveAnswerRequest
    ): SavedAnswerAckDto = answerDto(sessionId, request)
}

private class BlockingSubmitApi : ExamApiService {
    override suspend fun getAnswers(sessionId: String): List<SavedAnswerDto> = emptyList()

    override suspend fun submit(sessionId: String): SubmittedSessionDto = SubmittedSessionDto("SUBMITTED")

    private val started = CompletableDeferred<Unit>()
    private val release = CompletableDeferred<Unit>()
    var uploadCount = 0
        private set

    suspend fun awaitUploadStarted() = started.await()
    fun completeUpload() = release.complete(Unit)

    override suspend fun getQuestions(sessionId: String): List<PaperQuestionDto> = emptyList()

    override suspend fun saveAnswer(
        sessionId: String,
        request: SaveAnswerRequest
    ): SavedAnswerAckDto {
        uploadCount++
        started.complete(Unit)
        release.await()
        return answerDto(sessionId, request)
    }
}

private class RecordingQuestionsApi(
    private val questions: List<PaperQuestionDto> = emptyList()
) : ExamApiService {
    override suspend fun getAnswers(sessionId: String): List<SavedAnswerDto> = emptyList()

    override suspend fun submit(sessionId: String): SubmittedSessionDto = SubmittedSessionDto("SUBMITTED")

    var uploadCount = 0
        private set

    override suspend fun getQuestions(sessionId: String): List<PaperQuestionDto> = questions

    override suspend fun saveAnswer(
        sessionId: String,
        request: SaveAnswerRequest
    ): SavedAnswerAckDto {
        uploadCount++
        return answerDto(sessionId, request)
    }
}

private class BlockingQuestionsApi : ExamApiService {
    override suspend fun getAnswers(sessionId: String): List<SavedAnswerDto> = emptyList()

    override suspend fun submit(sessionId: String): SubmittedSessionDto = SubmittedSessionDto("SUBMITTED")

    private val never = CompletableDeferred<Unit>()
    var uploadCount = 0
        private set

    override suspend fun getQuestions(sessionId: String): List<PaperQuestionDto> {
        never.await()
        return listOf(dto("q1"))
    }

    override suspend fun saveAnswer(
        sessionId: String,
        request: SaveAnswerRequest
    ): SavedAnswerAckDto {
        uploadCount++
        return answerDto(sessionId, request)
    }
}

private class FailingQuestionsApi(private val failFirst: Boolean = false) : ExamApiService {
    override suspend fun getAnswers(sessionId: String): List<SavedAnswerDto> = emptyList()

    override suspend fun submit(sessionId: String): SubmittedSessionDto = SubmittedSessionDto("SUBMITTED")

    var questionCalls = 0
        private set

    override suspend fun getQuestions(sessionId: String): List<PaperQuestionDto> {
        questionCalls++
        if (failFirst && questionCalls == 1) throw IOException("offline")
        return listOf(dto("q1"))
    }

    override suspend fun saveAnswer(
        sessionId: String,
        request: SaveAnswerRequest
    ): SavedAnswerAckDto = throw IOException("offline")
}

private fun answerDto(sessionId: String, request: SaveAnswerRequest) = ackFor(request)
