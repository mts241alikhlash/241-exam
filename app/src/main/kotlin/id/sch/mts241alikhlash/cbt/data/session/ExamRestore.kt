package id.sch.mts241alikhlash.cbt.data.session

import id.sch.mts241alikhlash.cbt.data.remote.ApiException
import id.sch.mts241alikhlash.cbt.data.repository.SyncReport
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.coroutines.cancellation.CancellationException

/**
 * The persisted exam session: which paper is open and when it must end.
 *
 * The deadline is stored rather than a countdown so that the remaining time can be
 * recomputed from the wall clock after a process death.
 */
data class RestoredSession(val sessionId: String, val deadline: Long?)

sealed interface RestoreOutcome {
    data object NoSession : RestoreOutcome
    data class Restored(val session: RestoredSession) : RestoreOutcome
    data class Failed(val message: String) : RestoreOutcome
    data class SessionLost(val message: String) : RestoreOutcome
}

const val RESTORE_CHECK_TIMEOUT_MS = 3_000L
const val RESTORE_ERROR_MESSAGE = "Gagal memulihkan sesi ujian. Coba lagi."

/**
 * Reads the persisted session without letting a storage failure hang the splash screen.
 *
 * A restored session also kicks off a best-effort answer sync on [scope]; the sync is
 * deliberately fire-and-forget so a slow network cannot block the exam UI.
 */
suspend fun restoreExamSession(
    sessionRepository: SessionRepository,
    scope: CoroutineScope,
    syncPending: suspend (String) -> SyncReport,
    validate: suspend (String) -> Unit = {},
    onSyncResult: (Result<SyncReport>) -> Unit = {}
): RestoreOutcome {
    val session = try {
        val id = sessionRepository.current() ?: return RestoreOutcome.NoSession
        RestoredSession(id, sessionRepository.deadline())
    } catch (cancellation: CancellationException) {
        throw cancellation
    } catch (_: Exception) {
        return RestoreOutcome.Failed(RESTORE_ERROR_MESSAGE)
    }

    val lost = sessionLostMessage(session.sessionId, validate)
    if (lost != null) {
        sessionRepository.finishIfCurrent(session.sessionId)
        return RestoreOutcome.SessionLost(lost)
    }

    syncPendingInBackground(scope, session.sessionId, syncPending, onSyncResult)
    return RestoreOutcome.Restored(session)
}

/**
 * Runs [syncPending] on [scope] and reports the outcome instead of throwing.
 *
 * Returns immediately; the caller (splash/restore path) must not wait for the network.
 */
fun syncPendingInBackground(
    scope: CoroutineScope,
    sessionId: String,
    syncPending: suspend (String) -> SyncReport,
    onResult: (Result<SyncReport>) -> Unit = {}
): Job = scope.launch {
    val result = try {
        Result.success(syncPending(sessionId))
    } catch (cancellation: CancellationException) {
        throw cancellation
    } catch (error: Exception) {
        Result.failure(error)
    }
    onResult(result)
}

/**
 * Asks the server whether this device still owns the session.
 *
 * Only a 409 means the session is gone from this device (released by a supervisor or
 * taken over elsewhere). Offline or slow checks must never block an exam in progress.
 */
private suspend fun sessionLostMessage(sessionId: String, validate: suspend (String) -> Unit): String? =
    try {
        withTimeoutOrNull(RESTORE_CHECK_TIMEOUT_MS) { validate(sessionId) }
        null
    } catch (cancellation: CancellationException) {
        throw cancellation
    } catch (error: ApiException) {
        if (error.statusCode == 409) error.message else null
    } catch (_: Exception) {
        null
    }
