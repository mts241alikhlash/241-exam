package id.sch.mts241alikhlash.cbt.data.session

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ExamSessionTeardownTest {
    @Test
    fun `session is cleared even when cache purge fails`() = runTest {
        val sessionRepository = SessionRepository(RecordingSessionStore().apply { save("s1") })

        endExamSession(sessionRepository, "s1") {
            throw IllegalStateException("database closed")
        }

        assertNull(sessionRepository.current())
    }

    @Test
    fun `cache purge receives the finished session id`() = runTest {
        val sessionRepository = SessionRepository(RecordingSessionStore().apply { save("s1") })
        val purged = mutableListOf<String>()

        endExamSession(sessionRepository, "s1") { purged.add(it) }

        assertEquals(listOf("s1"), purged)
        assertNull(sessionRepository.current())
    }

    @Test
    fun `missing session id skips the purge`() = runTest {
        val sessionRepository = SessionRepository(RecordingSessionStore())
        var purgeCalled = false

        endExamSession(sessionRepository, null) { purgeCalled = true }

        assertEquals(false, purgeCalled)
        assertNull(sessionRepository.current())
    }
}

private class RecordingSessionStore : SessionStore {
    private var value: String? = null

    override suspend fun load(): String? = value

    override suspend fun save(id: String) {
        value = id
    }

    override suspend fun clear() {
        value = null
    }
}
