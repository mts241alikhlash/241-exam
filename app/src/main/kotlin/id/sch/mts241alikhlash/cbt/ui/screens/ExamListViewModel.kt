package id.sch.mts241alikhlash.cbt.ui.screens

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import id.sch.mts241alikhlash.cbt.data.model.ExamSummaryDto
import id.sch.mts241alikhlash.cbt.data.remote.ApiException
import id.sch.mts241alikhlash.cbt.data.remote.GENERIC_ERROR_MESSAGE
import id.sch.mts241alikhlash.cbt.data.repository.ExamCatalogRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

sealed interface ExamListUiState {
    data object Loading : ExamListUiState
    data object Empty : ExamListUiState
    data class Content(val exams: List<ExamSummaryDto>) : ExamListUiState
    data class Error(val message: String, val unauthorized: Boolean) : ExamListUiState
}

fun ExamSummaryDto.isSelectable(): Boolean = status == "OPEN" || status == "IN_PROGRESS"

class ExamListViewModel(private val catalog: ExamCatalogRepository) : ViewModel() {

    private val _state = MutableStateFlow<ExamListUiState>(ExamListUiState.Loading)
    val state: StateFlow<ExamListUiState> = _state.asStateFlow()

    init {
        refresh()
    }

    fun refresh() {
        _state.value = ExamListUiState.Loading
        viewModelScope.launch {
            _state.value = try {
                val exams = catalog.listExams()
                if (exams.isEmpty()) ExamListUiState.Empty else ExamListUiState.Content(exams)
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (error: ApiException) {
                ExamListUiState.Error(error.message, unauthorized = error.statusCode == 401)
            } catch (error: Exception) {
                ExamListUiState.Error(GENERIC_ERROR_MESSAGE, unauthorized = false)
            }
        }
    }
}

fun examStatusLabel(status: String, startsAt: String, zone: java.time.ZoneId = java.time.ZoneId.systemDefault()): String =
    when (status) {
        "OPEN" -> "Dibuka · Mulai"
        "IN_PROGRESS" -> "Sedang berlangsung · Lanjutkan"
        "UPCOMING" -> openingTime(startsAt, zone)?.let { "Dibuka $it" } ?: "Belum dibuka"
        "FINISHED" -> "Selesai"
        else -> status
    }

private fun openingTime(startsAt: String, zone: java.time.ZoneId): String? =
    runCatching {
        java.time.OffsetDateTime.parse(startsAt)
            .atZoneSameInstant(zone)
            .format(java.time.format.DateTimeFormatter.ofPattern("EEE, d MMM yyyy HH.mm", java.util.Locale.forLanguageTag("id-ID")))
    }.getOrNull()
