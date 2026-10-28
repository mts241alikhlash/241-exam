package id.sch.mts241alikhlash.cbt.ui.screens

import id.sch.mts241alikhlash.cbt.MainDispatcherRule
import id.sch.mts241alikhlash.cbt.data.auth.httpError
import id.sch.mts241alikhlash.cbt.data.model.ExamSummaryDto
import id.sch.mts241alikhlash.cbt.data.model.StartSessionRequest
import id.sch.mts241alikhlash.cbt.data.model.StartedSessionDto
import id.sch.mts241alikhlash.cbt.data.remote.ExamCatalogApiService
import id.sch.mts241alikhlash.cbt.data.repository.ExamCatalogRepository
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import java.io.IOException

class ExamListViewModelTest {

    @get:Rule
    val mainRule = MainDispatcherRule()

    private fun exam(id: String, status: String) = ExamSummaryDto(
        id, "Ujian $id", "Matematika", "2026-11-01T01:00:00Z", "2026-11-01T03:00:00Z", 90, status
    )

    private class Api(var result: () -> List<ExamSummaryDto>) : ExamCatalogApiService {
        override suspend fun listExams(): List<ExamSummaryDto> = result()
        override suspend fun startSession(examId: String, request: StartSessionRequest): StartedSessionDto =
            throw UnsupportedOperationException()
    }

    private fun viewModel(api: Api) = ExamListViewModel(ExamCatalogRepository(api, { "device-1" }))

    @Test
    fun list_empty_state_when_no_exams() {
        assertEquals(ExamListUiState.Empty, viewModel(Api { emptyList() }).state.value)
    }

    @Test
    fun list_content_state_when_exams_exist() {
        val exams = listOf(exam("e1", "OPEN"))
        assertEquals(ExamListUiState.Content(exams), viewModel(Api { exams }).state.value)
    }

    @Test
    fun list_error_state_on_network_failure_with_retry() {
        val api = Api { throw IOException("offline") }
        val vm = viewModel(api)

        assertEquals(
            ExamListUiState.Error("Tidak dapat terhubung ke server.", unauthorized = false),
            vm.state.value
        )

        api.result = { listOf(exam("e1", "OPEN")) }
        vm.refresh()

        assertTrue(vm.state.value is ExamListUiState.Content)
    }

    @Test
    fun list_error_marks_unauthorized_on_401() {
        val vm = viewModel(Api { throw httpError(401) })

        assertEquals(true, (vm.state.value as ExamListUiState.Error).unauthorized)
    }

    @Test
    fun only_open_or_in_progress_exams_selectable() {
        assertTrue(exam("a", "OPEN").isSelectable())
        assertTrue(exam("b", "IN_PROGRESS").isSelectable())
        assertFalse(exam("c", "UPCOMING").isSelectable())
        assertFalse(exam("d", "FINISHED").isSelectable())
    }
}

class ExamStatusLabelTest {
    private val zone = java.time.ZoneId.of("Asia/Jakarta")

    @org.junit.Test
    fun upcoming_shows_the_local_opening_time() {
        val label = examStatusLabel("UPCOMING", "2026-11-02T01:00:00.000Z", zone)
        org.junit.Assert.assertTrue(label, label.startsWith("Dibuka "))
        org.junit.Assert.assertTrue(label, label.contains("08.00"))
        org.junit.Assert.assertTrue(label, label.contains("2026"))
    }

    @org.junit.Test
    fun in_progress_invites_the_student_to_continue() {
        org.junit.Assert.assertEquals("Sedang berlangsung · Lanjutkan", examStatusLabel("IN_PROGRESS", "x", zone))
    }

    @org.junit.Test
    fun open_and_finished_keep_their_labels() {
        org.junit.Assert.assertEquals("Dibuka · Mulai", examStatusLabel("OPEN", "x", zone))
        org.junit.Assert.assertEquals("Selesai", examStatusLabel("FINISHED", "x", zone))
    }

    @org.junit.Test
    fun an_unreadable_start_time_falls_back_to_the_plain_label() {
        org.junit.Assert.assertEquals("Belum dibuka", examStatusLabel("UPCOMING", "bukan tanggal", zone))
    }
}
