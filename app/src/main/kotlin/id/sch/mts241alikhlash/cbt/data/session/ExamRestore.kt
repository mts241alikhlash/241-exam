package id.sch.mts241alikhlash.cbt.data.session

import id.sch.mts241alikhlash.cbt.data.repository.SyncReport
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
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
}

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
