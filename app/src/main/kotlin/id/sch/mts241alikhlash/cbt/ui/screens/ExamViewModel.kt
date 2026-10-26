package id.sch.mts241alikhlash.cbt.ui.screens

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import id.sch.mts241alikhlash.cbt.data.model.StudentQuestionDto
import id.sch.mts241alikhlash.cbt.data.repository.ExamRepository
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class ExamUiState(
    val isLoading: Boolean = true,
    val errorMessage: String? = null,
    val questions: List<StudentQuestionDto> = emptyList(),
    val currentIndex: Int = 0,
    val answers: Map<String, String> = emptyMap(),
    val doubtfuls: Set<String> = emptySet(),
    val remainingSeconds: Long = 5400,
    val isSubmitted: Boolean = false
)

class ExamViewModel(
    private val repository: ExamRepository,
    private val sessionId: String
) : ViewModel() {

    private val _uiState = MutableStateFlow(ExamUiState())
    val uiState: StateFlow<ExamUiState> = _uiState.asStateFlow()

    init {
        loadQuestions()
        observeLocalAnswers()
        startTimer()
    }

    private fun loadQuestions() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true)
            repository.getQuestions(sessionId)
                .onSuccess { list ->
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        questions = list
                    )
                }
                .onFailure { err ->
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        errorMessage = err.localizedMessage ?: "Gagal memuat soal"
                    )
                }
        }
    }

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
        viewModelScope.launch {
            while (_uiState.value.remainingSeconds > 0 && !_uiState.value.isSubmitted) {
                delay(1000)
                _uiState.value = _uiState.value.copy(
                    remainingSeconds = _uiState.value.remainingSeconds - 1
                )
            }
        }
    }

    fun selectAnswer(questionId: String, answer: String) {
        val currentDoubt = _uiState.value.doubtfuls.contains(questionId)
        viewModelScope.launch {
            repository.recordAnswer(questionId, sessionId, answer, currentDoubt)
        }
    }

    fun toggleDoubtful(questionId: String) {
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
        viewModelScope.launch {
            repository.syncPending(sessionId)
            _uiState.value = _uiState.value.copy(isSubmitted = true)
        }
    }
}
