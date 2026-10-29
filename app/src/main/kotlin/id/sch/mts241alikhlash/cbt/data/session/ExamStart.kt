package id.sch.mts241alikhlash.cbt.data.session

import id.sch.mts241alikhlash.cbt.data.repository.ExamCatalogRepository
import id.sch.mts241alikhlash.cbt.data.repository.StartedSession

suspend fun startExamSession(
    catalog: ExamCatalogRepository,
    sessions: SessionRepository,
    examId: String,
    accessCode: String
): StartedSession {
    val started = catalog.startSession(examId, accessCode)
    sessions.start(started.sessionId, started.deadlineLocalMillis)
    return started
}
