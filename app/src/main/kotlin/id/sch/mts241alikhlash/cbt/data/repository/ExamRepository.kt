package id.sch.mts241alikhlash.cbt.data.repository

import id.sch.mts241alikhlash.cbt.data.local.AnswerDao
import id.sch.mts241alikhlash.cbt.data.local.CachedAnswerEntity
import id.sch.mts241alikhlash.cbt.data.model.StudentQuestionDto
import id.sch.mts241alikhlash.cbt.data.model.SubmitAnswerRequest
import id.sch.mts241alikhlash.cbt.data.remote.ExamApiService
import kotlinx.coroutines.flow.Flow

class ExamRepository(
    private val api: ExamApiService,
    private val dao: AnswerDao
) {
    fun observeAnswers(sessionId: String): Flow<List<CachedAnswerEntity>> {
        return dao.observeAnswers(sessionId)
    }

    suspend fun getQuestions(sessionId: String): Result<List<StudentQuestionDto>> {
        return runCatching {
            api.getQuestions(sessionId)
        }
    }

    suspend fun recordAnswer(
        questionId: String,
        sessionId: String,
        answer: String,
        isDoubtful: Boolean = false
    ) {
        val entity = CachedAnswerEntity(
            questionId = questionId,
            sessionId = sessionId,
            answer = answer,
            isDoubtful = isDoubtful,
            isSynced = false
        )
        dao.saveAnswer(entity)

        runCatching {
            api.saveAnswer(sessionId, SubmitAnswerRequest(questionId, answer))
        }.onSuccess {
            dao.markSynced(questionId)
        }
    }

    suspend fun syncPending(sessionId: String) {
        val pending = dao.getUnsyncedAnswers(sessionId)
        for (item in pending) {
            runCatching {
                api.saveAnswer(sessionId, SubmitAnswerRequest(item.questionId, item.answer))
            }.onSuccess {
                dao.markSynced(item.questionId)
            }
        }
    }
}
