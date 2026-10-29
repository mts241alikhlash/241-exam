package id.sch.mts241alikhlash.cbt.data.repository

import id.sch.mts241alikhlash.cbt.data.local.AnswerDao
import id.sch.mts241alikhlash.cbt.data.local.CachedAnswerEntity
import id.sch.mts241alikhlash.cbt.data.local.CachedQuestionEntity
import id.sch.mts241alikhlash.cbt.data.local.QuestionDao
import id.sch.mts241alikhlash.cbt.data.model.AnswerPayload
import id.sch.mts241alikhlash.cbt.data.model.PaperChoiceDto
import id.sch.mts241alikhlash.cbt.data.model.PaperQuestionDto
import id.sch.mts241alikhlash.cbt.data.model.SaveAnswerRequest
import id.sch.mts241alikhlash.cbt.data.model.SubmitRequest
import id.sch.mts241alikhlash.cbt.data.model.SubmittedSessionDto
import id.sch.mts241alikhlash.cbt.data.remote.ApiException
import id.sch.mts241alikhlash.cbt.data.remote.ExamApiService
import id.sch.mts241alikhlash.cbt.data.remote.guarded
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import kotlin.coroutines.cancellation.CancellationException

data class SyncReport(
    val synced: Int,
    val failed: Int,
    val rejected: Int = 0,
    val closedMessage: String? = null
)

sealed interface SendOutcome {
    data object Sent : SendOutcome
    data object Retry : SendOutcome
    data object Offline : SendOutcome
    data object Superseded : SendOutcome
    data class Closed(val message: String) : SendOutcome
    data class Rejected(val message: String) : SendOutcome
}

private fun answerRequest(questionId: String, answer: String, clientId: String) = SaveAnswerRequest(
    questionId = questionId,
    clientId = clientId,
    answer = (AnswerPayload.decode(answer) ?: AnswerPayload.Choice(answer)).toJson()
)

fun answerClientId(sessionId: String, questionId: String, answer: String, updatedAt: Long): String =
    java.util.UUID.nameUUIDFromBytes("$sessionId|$questionId|$updatedAt|$answer".toByteArray()).toString()

class ExamRepository(
    private val api: ExamApiService,
    private val dao: AnswerDao,
    private val questionDao: QuestionDao,
    private val now: () -> Long = { System.currentTimeMillis() },
    private val log: (String, Throwable?) -> Unit = { message, error ->
        // java.util.logging works on both Android and the JVM unit-test classpath,
        // unlike android.util.Log which is a throwing stub in local tests.
        LOGGER.log(java.util.logging.Level.WARNING, message, error)
    }
) {
    private val sendLock = Mutex()

    fun observeAnswers(sessionId: String): Flow<List<CachedAnswerEntity>> {
        return dao.observeAnswers(sessionId)
    }

    suspend fun getQuestions(
        sessionId: String,
        forceRefresh: Boolean = false
    ): Result<List<PaperQuestionDto>> {
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
                cacheQuestions(sessionId, remote)
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

    /**
     * Writes the freshly downloaded paper to the cache without letting a cache failure
     * turn a successful download into a blank screen: the network result still wins.
     */
    private suspend fun cacheQuestions(sessionId: String, remote: List<PaperQuestionDto>) {
        try {
            questionDao.replaceForSession(sessionId, remote.map { it.toEntity(sessionId, now()) })
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (error: Exception) {
            log("Failed to cache questions for $sessionId", error)
        }
    }

    suspend fun submit(sessionId: String, violations: Int = 0): SubmittedSessionDto =
        guarded { api.submit(sessionId, SubmitRequest(violations)) }

    suspend fun clearQuestions(sessionId: String) {
        questionDao.deleteForSession(sessionId)
    }

    suspend fun recordAnswer(
        questionId: String,
        sessionId: String,
        answer: String,
        isDoubtful: Boolean = false
    ): SendOutcome {
        val entity = CachedAnswerEntity(
            questionId = questionId,
            sessionId = sessionId,
            answer = answer,
            isDoubtful = isDoubtful,
            isSynced = false,
            updatedAt = now()
        )
        if (answer.isBlank()) {
            dao.saveAnswer(entity.copy(isSynced = true))
            return SendOutcome.Sent
        }
        dao.saveAnswer(entity)

        return sendLock.withLock {
            val latest = dao.getAnswer(sessionId, questionId)
            if (latest == null || latest.isSynced || latest.updatedAt != entity.updatedAt || latest.answer != entity.answer) {
                return@withLock SendOutcome.Superseded
            }
            send(latest).also { outcome ->
                // Only this exact revision may be marked: a newer edit made while the
                // request was in flight must stay pending.
                if (outcome == SendOutcome.Sent) dao.markSynced(sessionId, questionId, answer, entity.updatedAt)
            }
        }
    }

    /**
     * Uploads every pending answer and then re-reads the queue.
     *
     * [SyncReport.failed] is the *authoritative* count of rows still unsynced after the
     * pass, not the number of errors observed. That closes the gap where a student edits
     * an answer mid-sync and the snapshot taken before the loop goes stale.
     */
    suspend fun syncPending(sessionId: String): SyncReport {
        var synced = 0
        var rejected = 0
        var closedMessage: String? = null
        sendLock.withLock {
            val pending = dao.getUnsyncedAnswers(sessionId)
            loop@ for (item in pending) {
                when (val outcome = send(item)) {
                    SendOutcome.Sent -> {
                        val marked = dao.markSynced(
                            sessionId = item.sessionId,
                            questionId = item.questionId,
                            answer = item.answer,
                            updatedAt = item.updatedAt
                        )
                        if (marked > 0) synced++
                    }
                    is SendOutcome.Closed -> {
                        closedMessage = outcome.message
                        break@loop
                    }
                    is SendOutcome.Rejected -> rejected++
                    SendOutcome.Offline -> break@loop
                    SendOutcome.Retry, SendOutcome.Superseded -> Unit
                }
            }
        }
        val remaining = dao.countUnsyncedAnswers(sessionId)
        return SyncReport(synced = synced, failed = remaining, rejected = rejected, closedMessage = closedMessage)
    }

    suspend fun pullServerAnswers(sessionId: String): Int {
        val remote = guarded { api.getAnswers(sessionId) }
        var imported = 0
        for (item in remote) {
            val payload = AnswerPayload.fromJson(item.answer) ?: continue
            val inserted = dao.insertIfAbsent(
                CachedAnswerEntity(
                    questionId = item.questionId,
                    sessionId = sessionId,
                    answer = payload.encode(),
                    isDoubtful = false,
                    isSynced = true,
                    updatedAt = now()
                )
            )
            if (inserted != -1L) imported++
        }
        return imported
    }

    private suspend fun send(item: CachedAnswerEntity): SendOutcome =
        try {
            guarded {
                api.saveAnswer(
                    item.sessionId,
                    answerRequest(
                        item.questionId,
                        item.answer,
                        answerClientId(item.sessionId, item.questionId, item.answer, item.updatedAt)
                    )
                )
            }
            SendOutcome.Sent
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (error: ApiException) {
            when (error.statusCode) {
                409 -> SendOutcome.Closed(error.message)
                400 -> SendOutcome.Rejected(error.message)
                0 -> {
                    log("Failed to sync answer ${item.questionId}", error)
                    SendOutcome.Offline
                }
                else -> {
                    log("Failed to sync answer ${item.questionId}", error)
                    SendOutcome.Retry
                }
            }
        } catch (error: Exception) {
            log("Failed to sync answer ${item.questionId}", error)
            SendOutcome.Retry
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
        private val LOGGER: java.util.logging.Logger =
            java.util.logging.Logger.getLogger("ExamRepository")
    }
}

private fun PaperQuestionDto.toEntity(sessionId: String, cachedAt: Long) = CachedQuestionEntity(
    sessionId = sessionId,
    id = id,
    number = number,
    type = type,
    bodyHtml = bodyHtml,
    points = points,
    choicesJson = choices?.let {
        ExamRepository.json.encodeToString(ListSerializer(PaperChoiceDto.serializer()), it)
    },
    cachedAt = cachedAt
)

private fun CachedQuestionEntity.toDto() = PaperQuestionDto(
    id = id,
    number = number,
    type = type,
    bodyHtml = bodyHtml,
    points = points,
    choices = choicesJson?.let { encoded ->
        runCatching {
            ExamRepository.json.decodeFromString(ListSerializer(PaperChoiceDto.serializer()), encoded)
        }.getOrNull()
    }
)
