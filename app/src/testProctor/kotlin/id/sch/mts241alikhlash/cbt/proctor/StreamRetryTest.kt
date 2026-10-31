package id.sch.mts241alikhlash.cbt.proctor

import org.junit.Assert.assertEquals
import org.junit.Test

class StreamRetryTest {

    @Test
    fun `backoff starts at one second`() {
        assertEquals(1_000L, StreamRetry.delayFor(1))
    }

    @Test
    fun `backoff doubles per attempt`() {
        assertEquals(2_000L, StreamRetry.delayFor(2))
        assertEquals(8_000L, StreamRetry.delayFor(4))
    }

    @Test
    fun `backoff stops growing at thirty seconds`() {
        assertEquals(16_000L, StreamRetry.delayFor(5))
        assertEquals(30_000L, StreamRetry.delayFor(6))
        assertEquals(30_000L, StreamRetry.delayFor(60))
    }

    @Test
    fun `an attempt below one is treated as the first`() {
        assertEquals(1_000L, StreamRetry.delayFor(0))
        assertEquals(1_000L, StreamRetry.delayFor(-4))
    }

    @Test
    fun `the ICE wait is shorter than the report threshold`() {
        // A stalled ICE handshake must give up before the seat is reported blocked,
        // otherwise the first retry never happens and the grid keeps spinning.
        assert(StreamRetry.ICE_TIMEOUT_MS in 1..StreamRetry.REPORT_AFTER_MS) {
            "ICE_TIMEOUT_MS=${StreamRetry.ICE_TIMEOUT_MS} must sit within REPORT_AFTER_MS=${StreamRetry.REPORT_AFTER_MS}"
        }
    }
}
