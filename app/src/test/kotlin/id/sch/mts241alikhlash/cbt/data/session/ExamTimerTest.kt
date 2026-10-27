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
    fun `sub-second remainders round down`() {
        val deadline = ExamTimer.deadlineFrom(start)
        // 5_398_500 ms left floors to 5_398 whole seconds: the clock never rounds up
        // in the student's favour.
        assertEquals(5398L, ExamTimer.remainingSeconds(deadline, start + 1_500L))
    }

    @Test
    fun `a fresh session is not marked resumed`() {
        val deadline = ExamTimer.deadlineFrom(start)
        assertFalse(ExamTimer.isResumed(deadline, start))
    }

    @Test
    fun `a cold start after the clock ran is marked resumed`() {
        val deadline = ExamTimer.deadlineFrom(start)
        // App killed for 10 minutes, then relaunched.
        assertTrue(ExamTimer.isResumed(deadline, start + 600_000L))
    }
}
