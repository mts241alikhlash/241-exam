package id.sch.mts241alikhlash.cbt.data.session

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SessionRepositoryTest {
    @Test
    fun `start then current returns id`() = runTest {
        val repo = SessionRepository(InMemorySessionStore())
        repo.start("sess-1")
        assertEquals("sess-1", repo.current())
    }

    @Test
    fun `finish clears session`() = runTest {
        val repo = SessionRepository(InMemorySessionStore())
        repo.start("sess-1")
        repo.finish()
        assertNull(repo.current())
    }

    @Test
    fun `blank id is rejected`() = runTest {
        val repo = SessionRepository(InMemorySessionStore())
        val error = runCatching { repo.start("  ") }.exceptionOrNull()
        assertTrue(error is IllegalArgumentException)
    }
}

private class InMemorySessionStore : SessionStore {
    private var value: String? = null

    override suspend fun load(): String? = value

    override suspend fun save(id: String) {
        value = id
    }

    override suspend fun clear() {
        value = null
    }
}
