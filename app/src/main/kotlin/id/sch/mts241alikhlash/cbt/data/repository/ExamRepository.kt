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
import kotlin.coroutines.cancellation.CancellationException

class ExamRepository(
    private val api: ExamApiService,
    private val dao: AnswerDao,
    private val questionDao: QuestionDao,
    private val now: () -> Long = { System.currentTimeMillis() }
) {
    fun observeAnswers(sessionId: String): Flow<List<CachedAnswerEntity>> {
        return dao.observeAnswers(sessionId)
    }

    suspend fun getQuestions(
        sessionId: String,
        forceRefresh: Boolean = false
    ): Result<List<StudentQuestionDto>> {
        val cached = loadCache(sessionId)
        if (!forceRefresh && cached.isNotEmpty() && !isStale(cached)) {
            return Result.success(cached.map { it.toDto() })
        }

        return try {
            val remote = api.getQuestions(sessionId)
            if (remote.isEmpty() && cached.isNotEmpty()) {
                // A blank payload must not destroy a usable offline cache.
                Result.success(cached.map { it.toDto() })
            } else {
                questionDao.replaceForSession(
                    sessionId,
                    remote.map { it.toEntity(sessionId, now()) }
                )
                Result.success(remote)
            }
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (error: Exception) {
            if (cached.isNotEmpty()) {
                Result.success(cached.map { it.toDto() })
            } else {
                Result.failure(IllegalStateException(EMPTY_CACHE_MESSAGE, error))
            }
        }
    }

    suspend fun clearQuestions(sessionId: String) {
        questionDao.deleteForSession(sessionId)
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

    private suspend fun loadCache(sessionId: String): List<CachedQuestionEntity> =
        try {
            questionDao.getForSession(sessionId)
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (_: Exception) {
            emptyList()
        }

    private fun isStale(cached: List<CachedQuestionEntity>): Boolean {
        val newest = cached.maxOf { it.cachedAt }
        return now() - newest > CACHE_TTL_MS
    }

    companion object {
        const val EMPTY_CACHE_MESSAGE = "Tidak dapat memuat soal. Periksa koneksi."
        const val CACHE_TTL_MS = 24L * 60L * 60L * 1000L
        internal val json = Json { ignoreUnknownKeys = true }
    }
}

private fun StudentQuestionDto.toEntity(sessionId: String, cachedAt: Long) = CachedQuestionEntity(
    sessionId = sessionId,
    id = id,
    number = number,
    type = type,
    bodyHtml = bodyHtml,
    choicesJson = choices?.let {
        ExamRepository.json.encodeToString(ListSerializer(String.serializer()), it)
    },
    cachedAt = cachedAt
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
