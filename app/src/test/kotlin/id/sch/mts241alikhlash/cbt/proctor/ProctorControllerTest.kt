package id.sch.mts241alikhlash.cbt.proctor

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ProctorControllerTest {

    @Test
    fun `does nothing when built without bindings`() {
        val calls = mutableListOf<String>()
        val controller = ProctorController()
        controller.start("session-1")
        controller.stop()
        assertTrue(calls.isEmpty())
    }

    @Test
    fun `forwards the session id to the start binding`() {
        val calls = mutableListOf<String>()
        val controller = ProctorController(
            startStream = { calls += "start:$it" },
            stopStream = { calls += "stop" },
            reportBlocked = { id, reason -> calls += "blocked:$id:$reason" },
        )

        controller.start("session-1")
        controller.blocked("session-1", "permission")
        controller.stop()

        assertEquals(
            listOf("start:session-1", "blocked:session-1:permission", "stop"),
            calls,
        )
    }
}
