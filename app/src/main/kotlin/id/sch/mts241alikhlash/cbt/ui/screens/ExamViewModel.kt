package id.sch.mts241alikhlash.cbt.ui.screens

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import id.sch.mts241alikhlash.cbt.data.model.PaperQuestionDto
import id.sch.mts241alikhlash.cbt.data.repository.ExamRepository
import id.sch.mts241alikhlash.cbt.data.session.ExamTimer
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class ExamUiState(
    val isLoading: Boolean = true,
    val errorMessage: String? = null,
    val questions: List<PaperQuestionDto> = emptyList(),
    val currentIndex: Int = 0,
    val answers: Map<String, String> = emptyMap(),
    val doubtfuls: Set<String> = emptySet(),
    val remainingSeconds: Long = ExamTimer.EXAM_DURATION_SECONDS,
    val isSubmitted: Boolean = false,
    val isSubmitting: Boolean = false,
    val submitError: String? = null,
    val isResumed: Boolean = false
) {
    /** Answers only make sense once a paper is on screen and not being submitted. */
    val canAnswer: Boolean get() = !isLoading && !isSubmitting && !isSubmitted && questions.isNotEmpty()

    /** Submission is impossible until the paper actually loaded. */
    val canSubmit: Boolean get() = canAnswer
}

class ExamViewModel(
    private val repository: ExamRepository,
    private val sessionId: String,
    deadline: Long? = null,
    restored: Boolean? = null,
    private val now: () -> Long = { System.currentTimeMillis() }
) : ViewModel() {

    private val _uiState = MutableStateFlow(
        ExamUiState(
            remainingSeconds = deadline?.let { ExamTimer.remainingSeconds(it, now()) }
                ?: ExamTimer.EXAM_DURATION_SECONDS,
            isResumed = deadline != null && ExamTimer.isResumed(deadline, now(), restored)
        )
    )
    val uiState: StateFlow<ExamUiState> = _uiState.asStateFlow()

    private val timerDeadline: Long = deadline ?: ExamTimer.deadlineFrom(now())
    private var timerJob: Job? = null

    init {
        loadQuestions()
        observeLocalAnswers()
        startTimer()
    }

    fun loadQuestions(forceRefresh: Boolean = false) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, errorMessage = null)
            repository.getQuestions(sessionId, forceRefresh)
                .onSuccess { list ->
                    // An active session with zero questions is a broken paper, not a
                    // valid empty exam: show the retry screen instead of a blank sheet.
                    if (list.isEmpty()) {
                        _uiState.value = _uiState.value.copy(
                            isLoading = false,
                            questions = emptyList(),
                            errorMessage = EMPTY_PAPER_MESSAGE
                        )
                    } else {
                        _uiState.value = _uiState.value.copy(
                            isLoading = false,
                            questions = list,
                            currentIndex = _uiState.value.currentIndex
                                .coerceIn(0, list.lastIndex),
                            errorMessage = null
                        )
                    }
                }
                .onFailure { err ->
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        questions = emptyList(),
                        errorMessage = err.localizedMessage ?: "Gagal memuat soal"
                    )
                }
        }
    }

    /** Retry from the error screen; bypasses the cache so a stale/empty paper cannot win. */
    fun retry() = loadQuestions(forceRefresh = true)

    private fun observeLocalAnswers() {
        viewModelScope.launch {
            repository.observeAnswers(sessionId).collect { list ->
                val answerMap = list.associate { it.questionId to it.answer }
                val doubtSet = list.filter { it.isDoubtful }.map { it.questionId }.toSet()
                _uiState.value = _uiState.value.copy(
                    answers = answerMap,
                    doubtfuls = doubtSet
                )
            }
        }
    }

    private fun startTimer() {
        timerJob?.cancel()
        timerJob = viewModelScope.launch {
            while (!_uiState.value.isSubmitted) {
                val remaining = ExamTimer.remainingSeconds(timerDeadline, now())
                _uiState.value = _uiState.value.copy(remainingSeconds = remaining)
                if (remaining <= 0L) break
                delay(1000)
            }
        }
    }

    fun selectAnswer(questionId: String, answer: String) {
        if (!_uiState.value.canAnswer) return
        val currentDoubt = _uiState.value.doubtfuls.contains(questionId)
        viewModelScope.launch {
            repository.recordAnswer(questionId, sessionId, answer, currentDoubt)
        }
    }

    fun toggleDoubtful(questionId: String) {
        if (!_uiState.value.canAnswer) return
        val currentAnswer = _uiState.value.answers[questionId] ?: ""
        val newDoubt = !_uiState.value.doubtfuls.contains(questionId)
        viewModelScope.launch {
            repository.recordAnswer(questionId, sessionId, currentAnswer, newDoubt)
        }
    }

    fun goToIndex(index: Int) {
        if (index in _uiState.value.questions.indices) {
            _uiState.value = _uiState.value.copy(currentIndex = index)
        }
    }

    fun submitExam() {
        val state = _uiState.value
        // Guard against double taps and against submitting a paper that never loaded.
        if (state.isSubmitting || state.isSubmitted || !state.canSubmit) return

        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isSubmitting = true, submitError = null)
            try {
                val report = repository.syncPending(sessionId)
                // syncPending re-reads the queue, so `failed` is the truth at this moment.
                if (report.failed == 0) {
                    _uiState.value = _uiState.value.copy(
                        isSubmitting = false,
                        isSubmitted = true
                    )
                } else {
                    _uiState.value = _uiState.value.copy(
                        isSubmitting = false,
                        isSubmitted = false,
                        submitError = "${report.failed} jawaban belum terkirim. Coba lagi."
                    )
                }
            } catch (cancellation: kotlin.coroutines.cancellation.CancellationException) {
                throw cancellation
            } catch (error: Exception) {
                _uiState.value = _uiState.value.copy(
                    isSubmitting = false,
                    isSubmitted = false,
                    submitError = SUBMIT_ERROR_MESSAGE
                )
            }
        }
    }

    companion object {
        const val EMPTY_PAPER_MESSAGE = "Soal ujian tidak tersedia. Periksa koneksi lalu coba lagi."
        const val SUBMIT_ERROR_MESSAGE = "Gagal mengumpulkan jawaban. Coba lagi."
    }
}
