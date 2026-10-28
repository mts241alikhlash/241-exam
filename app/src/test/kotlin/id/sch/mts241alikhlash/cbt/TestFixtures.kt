package id.sch.mts241alikhlash.cbt

import id.sch.mts241alikhlash.cbt.data.model.PaperChoiceDto
import id.sch.mts241alikhlash.cbt.data.model.SaveAnswerRequest
import id.sch.mts241alikhlash.cbt.data.model.SavedAnswerAckDto

fun choices(vararg html: String): List<PaperChoiceDto> =
    html.mapIndexed { index, text -> PaperChoiceDto(('A' + index).toString(), text) }

fun ackFor(request: SaveAnswerRequest) = SavedAnswerAckDto(
    questionId = request.questionId,
    clientId = request.clientId,
    receivedAfterDeadline = false,
    updatedAt = "2026-10-27T00:00:00Z"
)

class InMemoryAnswerDao(vararg initial: id.sch.mts241alikhlash.cbt.data.local.CachedAnswerEntity) :
    id.sch.mts241alikhlash.cbt.data.local.AnswerDao {
    val rows = initial.associateBy { it.questionId }.toMutableMap()

    override fun observeAnswers(sessionId: String): kotlinx.coroutines.flow.Flow<List<id.sch.mts241alikhlash.cbt.data.local.CachedAnswerEntity>> =
        kotlinx.coroutines.flow.flowOf(rows.values.filter { it.sessionId == sessionId })

    override suspend fun getAnswer(sessionId: String, questionId: String) =
        rows[questionId]?.takeIf { it.sessionId == sessionId }

    override suspend fun getUnsyncedAnswers(sessionId: String) =
        rows.values.filter { it.sessionId == sessionId && !it.isSynced }

    override suspend fun countUnsyncedAnswers(sessionId: String) =
        rows.values.count { it.sessionId == sessionId && !it.isSynced }

    override suspend fun saveAnswer(answer: id.sch.mts241alikhlash.cbt.data.local.CachedAnswerEntity) {
        rows[answer.questionId] = answer
    }

    override suspend fun insertIfAbsent(answer: id.sch.mts241alikhlash.cbt.data.local.CachedAnswerEntity): Long {
        if (rows.containsKey(answer.questionId)) return -1L
        rows[answer.questionId] = answer
        return 1L
    }

    override suspend fun markSynced(sessionId: String, questionId: String, answer: String, updatedAt: Long): Int {
        val row = rows[questionId] ?: return 0
        if (row.sessionId != sessionId || row.answer != answer || row.updatedAt != updatedAt || row.isSynced) return 0
        rows[questionId] = row.copy(isSynced = true)
        return 1
    }
}

class EmptyQuestionDao : id.sch.mts241alikhlash.cbt.data.local.QuestionDao {
    private val rows = mutableListOf<id.sch.mts241alikhlash.cbt.data.local.CachedQuestionEntity>()

    override suspend fun getForSession(sessionId: String) = rows.filter { it.sessionId == sessionId }

    override suspend fun insertAll(items: List<id.sch.mts241alikhlash.cbt.data.local.CachedQuestionEntity>) {
        rows += items
    }

    override suspend fun deleteForSession(sessionId: String) {
        rows.removeAll { it.sessionId == sessionId }
    }
}
