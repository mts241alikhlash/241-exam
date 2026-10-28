package id.sch.mts241alikhlash.cbt.data.session

import id.sch.mts241alikhlash.cbt.data.model.ExamSummaryDto
import id.sch.mts241alikhlash.cbt.data.model.StartSessionRequest
import id.sch.mts241alikhlash.cbt.data.model.StartedSessionDto
import id.sch.mts241alikhlash.cbt.data.remote.ApiException
import id.sch.mts241alikhlash.cbt.data.remote.ExamCatalogApiService
import id.sch.mts241alikhlash.cbt.data.repository.ExamCatalogRepository
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.io.IOException

class ExamStartTest {

    private class Api(val result: () -> StartedSessionDto) : ExamCatalogApiService {
        override suspend fun listExams(): List<ExamSummaryDto> = emptyList()
        override suspend fun startSession(examId: String, request: StartSessionRequest): StartedSessionDto = result()
    }

    @Test
    fun startExamSession_persists_session_with_server_deadline() = runTest {
        val sessions = SessionRepository(MemorySessionStore(), now = { 1_000L })
        val catalog = ExamCatalogRepository(
            Api { StartedSessionDto("s1", deadline = 65_000L, serverTime = 5_000L) },
            { "device-1" },
            now = { 1_000L }
        )

        val started = startExamSession(catalog, sessions, "e1", "ABC")

        assertEquals("s1", started.sessionId)
        assertEquals("s1", sessions.current())
        assertEquals(61_000L, sessions.deadline())
    }

    @Test
    fun startExamSession_persists_nothing_when_server_refuses() = runTest {
        val sessions = SessionRepository(MemorySessionStore())
        val catalog = ExamCatalogRepository(Api { throw IOException("offline") }, { "device-1" })

        val error = runCatching { startExamSession(catalog, sessions, "e1", "ABC") }.exceptionOrNull()

        assertEquals(0, (error as ApiException).statusCode)
        assertNull(sessions.current())
    }
}

private class MemorySessionStore : SessionStore {
    private var id: String? = null
    private var deadline: Long? = null
    override suspend fun load(): String? = id
    override suspend fun loadDeadline(): Long? = deadline
    override suspend fun save(id: String, deadline: Long) {
        this.id = id
        this.deadline = deadline
    }

    override suspend fun clear() {
        id = null
        deadline = null
    }
}
