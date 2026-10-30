package id.sch.mts241alikhlash.cbt.proctor

/**
 * Backoff between reconnect attempts. A stream that drops must not hammer the
 * server: the delay doubles from one second and stops growing at thirty.
 */
object StreamRetry {
    fun delayFor(attempt: Int): Long =
        minOf(30_000L, 1_000L shl (attempt.coerceAtLeast(1) - 1))

    /** How long a failing stream may keep retrying before it reports itself blocked. */
    const val REPORT_AFTER_MS = 60_000L
}
