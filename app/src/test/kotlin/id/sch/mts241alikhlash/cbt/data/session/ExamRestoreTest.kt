package id.sch.mts241alikhlash.cbt.data.session

import id.sch.mts241alikhlash.cbt.data.repository.SyncReport
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException

class ExamRestoreTest {

    @Test
    fun `no persisted session yields NoSession`() = runTest {
        val outcome = restoreExamSession(
            sessionRepository = SessionRepository(FailingStore(failLoad = false)),
            scope = this,
            syncPending = { SyncReport(0, 0) }
        )
        assertEquals(RestoreOutcome.NoSession, outcome)
    }

    @Test
    fun `restored session carries the persisted deadline`() = runTest {
        val deadline = 1_700_005_400_000L
        val repo = SessionRepository(StubStore("s1", deadline))

        val outcome = restoreExamSession(
            sessionRepository = repo,
            scope = this,
            syncPending = { SyncReport(0, 0) }
        )

        assertEquals(
            RestoreOutcome.Restored(RestoredSession("s1", deadline)),
            outcome
        )
    }

    @Test
    fun `storage failure becomes an error instead of hanging`() = runTest {
        val outcome = restoreExamSession(
            sessionRepository = SessionRepository(FailingStore(failLoad = true)),
            scope = this,
            syncPending = { SyncReport(0, 0) }
        )

        assertTrue("expected a failed restore, got $outcome", outcome is RestoreOutcome.Failed)
        assertEquals(
            RESTORE_ERROR_MESSAGE,
            (outcome as RestoreOutcome.Failed).message
        )
    }

    @Test
    fun `restore triggers a background sync for the restored session`() = runTest {
        val repo = SessionRepository(StubStore("s1", null))
        val synced = mutableListOf<String>()

        restoreExamSession(
            sessionRepository = repo,
            scope = this,
            syncPending = { id ->
                synced.add(id)
                SyncReport(1, 0)
            }
        )
        advanceUntilIdle()

        assertEquals(listOf("s1"), synced)
    }

    @Test
    fun `restore does not wait for a slow sync`() = runTest {
        val repo = SessionRepository(StubStore("s1", null))
        val results = mutableListOf<Result<SyncReport>>()

        val outcome = restoreExamSession(
            sessionRepository = repo,
            scope = this,
            syncPending = { throw IOException("offline") },
            onSyncResult = { results.add(it) }
        )

        // Restore already returned; the sync failure has not been reported yet.
        assertTrue(outcome is RestoreOutcome.Restored)
        assertTrue("sync must not block restore", results.isEmpty())

        advanceUntilIdle()
        assertEquals(1, results.size)
        assertTrue("sync failure is reported, not thrown", results.single().isFailure)
    }

    @Test
    fun `sync failure does not fail the restore`() = runTest {
        val repo = SessionRepository(StubStore("s1", null))

        val outcome = restoreExamSession(
            sessionRepository = repo,
            scope = this,
            syncPending = { throw IOException("offline") }
        )
        advanceUntilIdle()

        assertTrue(outcome is RestoreOutcome.Restored)
    }
}

private class StubStore(
    private val id: String?,
    private val deadline: Long?
) : SessionStore {
    override suspend fun load(): String? = id
    override suspend fun loadDeadline(): Long? = deadline
    override suspend fun save(id: String, deadline: Long) = Unit
    override suspend fun clear() = Unit
}

private class FailingStore(private val failLoad: Boolean) : SessionStore {
    override suspend fun load(): String? {
        if (failLoad) throw IllegalStateException("datastore corrupted")
        return null
    }

    override suspend fun loadDeadline(): Long? = null
    override suspend fun save(id: String, deadline: Long) = Unit
    override suspend fun clear() = Unit
}
