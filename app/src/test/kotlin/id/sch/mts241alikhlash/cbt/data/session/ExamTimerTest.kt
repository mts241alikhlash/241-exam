package id.sch.mts241alikhlash.cbt.data.session

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ExamTimerTest {
    private val start = 1_700_000_000_000L

    @Test
    fun `deadline is 90 minutes after start`() {
        assertEquals(start + 5_400_000L, ExamTimer.deadlineFrom(start))
    }

    @Test
    fun `remaining time is derived from the deadline`() {
        val deadline = ExamTimer.deadlineFrom(start)
        assertEquals(5_400L, ExamTimer.remainingSeconds(deadline, start))
        assertEquals(5_100L, ExamTimer.remainingSeconds(deadline, start + 300_000L))
    }

    @Test
    fun `remaining time never goes negative`() {
        val deadline = ExamTimer.deadlineFrom(start)
        assertEquals(0L, ExamTimer.remainingSeconds(deadline, start + 10_000_000L))
    }

    @Test
    fun `sub-second remainders round up so the clock never understates the time`() {
        val deadline = ExamTimer.deadlineFrom(start)
        // 5_398_500 ms left displays as 5_399 s, never 5_398 s.
        assertEquals(5_399L, ExamTimer.remainingSeconds(deadline, start + 1_500L))
    }

    @Test
    fun `a fresh session is not marked resumed`() {
        val deadline = ExamTimer.deadlineFrom(start)
        assertFalse(ExamTimer.isResumed(deadline, start))
    }

    /**
     * R2: reading the deadline back from DataStore and building the ViewModel costs a few
     * milliseconds. A brand-new exam must still read 90:00 and must not announce itself as
     * resumed just because the clock advanced during startup.
     */
    @Test
    fun `startup latency does not mark a fresh session as resumed`() {
        val deadline = ExamTimer.deadlineFrom(start)
        val nowAfterStartup = deadline - ExamTimer.EXAM_DURATION_MS + 50L

        assertEquals(
            "a fresh paper must open at 90:00, not 89:59",
            ExamTimer.EXAM_DURATION_SECONDS,
            ExamTimer.remainingSeconds(deadline, nowAfterStartup)
        )
        assertFalse(
            "50 ms of startup latency is not a resumed exam",
            ExamTimer.isResumed(deadline, nowAfterStartup)
        )
    }

    @Test
    fun `sub-second latency anywhere below the tolerance is not resumed`() {
        val deadline = ExamTimer.deadlineFrom(start)
        val justInsideTolerance = deadline - ExamTimer.EXAM_DURATION_MS + 1_900L

        assertFalse(ExamTimer.isResumed(deadline, justInsideTolerance))
    }

    @Test
    fun `a cold start after the clock ran is marked resumed`() {
        val deadline = ExamTimer.deadlineFrom(start)
        // App killed for 10 minutes, then relaunched.
        assertTrue(ExamTimer.isResumed(deadline, start + 600_000L))
    }

    @Test
    fun `a session resumed by minutes shows the banner`() {
        val deadline = ExamTimer.deadlineFrom(start)
        // 45 minutes of the paper already spent.
        val now = start + 45L * 60L * 1000L

        assertTrue(ExamTimer.isResumed(deadline, now))
        assertEquals(2_700L, ExamTimer.remainingSeconds(deadline, now))
    }

    @Test
    fun `a session past its tolerance is resumed`() {
        val deadline = ExamTimer.deadlineFrom(start)
        val justPastTolerance = deadline - ExamTimer.EXAM_DURATION_MS + 3_000L

        assertTrue(ExamTimer.isResumed(deadline, justPastTolerance))
    }

    @Test
    fun `explicit not-restored flag wins over the 90 minute heuristic`() {
        val start = 1_700_000_000_000L
        val shortDeadline = start + 60L * 60L * 1000L

        assertFalse(ExamTimer.isResumed(shortDeadline, start, restored = false))
    }

    @Test
    fun `explicit restored flag reports resumed`() {
        val start = 1_700_000_000_000L

        assertTrue(ExamTimer.isResumed(ExamTimer.deadlineFrom(start), start, restored = true))
    }
}
