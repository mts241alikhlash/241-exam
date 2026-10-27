package id.sch.mts241alikhlash.cbt.data.session

/**
 * Ends an exam session without letting cache cleanup block session teardown.
 *
 * The session is cleared first and unconditionally: if the purge throws (corrupt DB,
 * disk full, closed database) the persisted session must still be gone, otherwise the
 * next launch restores an exam the student already submitted.
 */
suspend fun endExamSession(
    sessionRepository: SessionRepository,
    sessionId: String?,
    clearQuestions: suspend (String) -> Unit
) {
    sessionRepository.finish()
    if (sessionId != null) {
        runCatching { clearQuestions(sessionId) }
    }
}
