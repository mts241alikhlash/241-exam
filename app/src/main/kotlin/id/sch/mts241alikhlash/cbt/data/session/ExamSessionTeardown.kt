package id.sch.mts241alikhlash.cbt.data.session

/**
 * Ends an exam session without letting cache cleanup block session teardown.
 *
 * Ordering matters: the session is cleared *before* the cache purge, and the purge
 * must never run for a session that is no longer the persisted one. The clear is a
 * compare-and-clear ([SessionRepository.finishIfCurrent]) so a teardown that was
 * already in flight when a new exam started cannot erase the new session.
 *
 * Returns true when this call actually ended the session.
 */
suspend fun endExamSession(
    sessionRepository: SessionRepository,
    sessionId: String?,
    clearQuestions: suspend (String) -> Unit
): Boolean {
    if (sessionId == null) {
        // Nothing to end; still clear a blank leftover so the next launch starts clean.
        sessionRepository.finish()
        return false
    }

    val ended = sessionRepository.finishIfCurrent(sessionId)
    if (!ended) return false

    runCatching { clearQuestions(sessionId) }
    return true
}
