package id.sch.mts241alikhlash.cbt.data.session

import id.sch.mts241alikhlash.cbt.data.repository.ExamCatalogRepository
import id.sch.mts241alikhlash.cbt.data.repository.StartedSession
import kotlin.coroutines.cancellation.CancellationException

suspend fun startExamSession(
    catalog: ExamCatalogRepository,
    sessions: SessionRepository,
    examId: String,
    accessCode: String,
    pullAnswers: suspend (String) -> Unit = {}
): StartedSession {
    val started = catalog.startSession(examId, accessCode)
    sessions.start(started.sessionId, started.deadlineLocalMillis)
    try {
        pullAnswers(started.sessionId)
    } catch (cancellation: CancellationException) {
        throw cancellation
    } catch (_: Exception) {
    }
    return started
}
