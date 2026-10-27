package id.sch.mts241alikhlash.cbt.ui.screens

import id.sch.mts241alikhlash.cbt.MainDispatcherRule
import id.sch.mts241alikhlash.cbt.data.local.AnswerDao
import id.sch.mts241alikhlash.cbt.data.local.CachedAnswerEntity
import id.sch.mts241alikhlash.cbt.data.local.CachedQuestionEntity
import id.sch.mts241alikhlash.cbt.data.local.QuestionDao
import id.sch.mts241alikhlash.cbt.data.model.StudentAnswerDto
import id.sch.mts241alikhlash.cbt.data.model.StudentQuestionDto
import id.sch.mts241alikhlash.cbt.data.model.SubmitAnswerRequest
import id.sch.mts241alikhlash.cbt.data.remote.ExamApiService
import id.sch.mts241alikhlash.cbt.data.repository.ExamRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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
        val vm = ExamViewModel(repoWithPending(1), "s1")

        vm.submitExam()
        advanceUntilIdle()

        assertFalse(vm.uiState.value.isSubmitted)
        assertEquals("1 jawaban belum terkirim. Coba lagi.", vm.uiState.value.submitError)
    }

    @Test
    fun `submit succeeds when all synced`() = runTest {
        val vm = ExamViewModel(repoWithPending(0), "s1")

        vm.submitExam()
        advanceUntilIdle()

        assertTrue(vm.uiState.value.isSubmitted)
        assertNull(vm.uiState.value.submitError)
    }
}

/**
 * Builds an [ExamRepository] whose sync attempt fails for [pending] unsynced answers.
 * With zero pending answers the sync succeeds trivially.
 */
private fun repoWithPending(pending: Int): ExamRepository {
    val answers = (1..pending).map { index ->
        CachedAnswerEntity(
            questionId = "q$index",
            sessionId = "s1",
            answer = "answer-$index",
            isSynced = false
        )
    }
    return ExamRepository(
        api = SubmitFakeApi(),
        dao = SubmitFakeAnswerDao(answers),
        questionDao = SubmitFakeQuestionDao()
    )
}

private class SubmitFakeAnswerDao(
    private val unsynced: List<CachedAnswerEntity>
) : AnswerDao {
    override fun observeAnswers(sessionId: String): Flow<List<CachedAnswerEntity>> =
        flowOf(emptyList())

    override suspend fun getAnswer(questionId: String): CachedAnswerEntity? = null

    override suspend fun getUnsyncedAnswers(sessionId: String): List<CachedAnswerEntity> =
        unsynced.filter { it.sessionId == sessionId }

    override suspend fun saveAnswer(answer: CachedAnswerEntity) = Unit

    override suspend fun markSynced(questionId: String) = Unit
}

private class SubmitFakeQuestionDao : QuestionDao {
    override suspend fun getForSession(sessionId: String): List<CachedQuestionEntity> =
        emptyList()

    override suspend fun insertAll(items: List<CachedQuestionEntity>) = Unit

    override suspend fun deleteForSession(sessionId: String) = Unit
}

/** Every answer upload fails, so any pending answer is reported as failed. */
private class SubmitFakeApi : ExamApiService {
    override suspend fun getQuestions(sessionId: String): List<StudentQuestionDto> = emptyList()

    override suspend fun saveAnswer(
        sessionId: String,
        request: SubmitAnswerRequest
    ): StudentAnswerDto = throw IOException("offline")
}
