package id.sch.mts241alikhlash.cbt.data.repository

import id.sch.mts241alikhlash.cbt.data.local.AnswerDao
import id.sch.mts241alikhlash.cbt.data.local.CachedAnswerEntity
import id.sch.mts241alikhlash.cbt.data.local.CachedQuestionEntity
import id.sch.mts241alikhlash.cbt.data.local.QuestionDao
import id.sch.mts241alikhlash.cbt.data.model.StudentQuestionDto
import id.sch.mts241alikhlash.cbt.data.model.SubmitAnswerRequest
import id.sch.mts241alikhlash.cbt.data.remote.ExamApiService
import kotlinx.coroutines.flow.Flow
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json

class ExamRepository(
    private val api: ExamApiService,
    private val dao: AnswerDao,
    private val questionDao: QuestionDao
) {
    fun observeAnswers(sessionId: String): Flow<List<CachedAnswerEntity>> {
        return dao.observeAnswers(sessionId)
    }

    suspend fun getQuestions(
        sessionId: String,
        forceRefresh: Boolean = false
    ): Result<List<StudentQuestionDto>> {
        if (!forceRefresh) {
            val cached = runCatching { questionDao.getForSession(sessionId) }.getOrDefault(emptyList())
            if (cached.isNotEmpty()) {
                return Result.success(cached.map { it.toDto() })
            }
        }

        return runCatching {
            val remote = api.getQuestions(sessionId)
            questionDao.replaceForSession(
                sessionId,
                remote.map { it.toEntity(sessionId) }
            )
            remote
        }.recoverCatching { error ->
            val cached = runCatching { questionDao.getForSession(sessionId) }
                .getOrDefault(emptyList())
            if (cached.isNotEmpty()) {
                cached.map { it.toDto() }
            } else {
                throw IllegalStateException(EMPTY_CACHE_MESSAGE, error)
            }
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

    companion object {
        const val EMPTY_CACHE_MESSAGE = "Tidak dapat memuat soal. Periksa koneksi."
        internal val json = Json { ignoreUnknownKeys = true }
    }
}

private fun StudentQuestionDto.toEntity(sessionId: String) = CachedQuestionEntity(
    sessionId = sessionId,
    id = id,
    number = number,
    type = type,
    bodyHtml = bodyHtml,
    choicesJson = choices?.let {
        ExamRepository.json.encodeToString(ListSerializer(String.serializer()), it)
    },
    cachedAt = System.currentTimeMillis()
)

private fun CachedQuestionEntity.toDto() = StudentQuestionDto(
    id = id,
    number = number,
    type = type,
    bodyHtml = bodyHtml,
    choices = choicesJson?.let { encoded ->
        runCatching {
            ExamRepository.json.decodeFromString(ListSerializer(String.serializer()), encoded)
        }.getOrNull()
    }
)
