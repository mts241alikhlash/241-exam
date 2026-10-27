package id.sch.mts241alikhlash.cbt.data.session

/**
 * Exam countdown maths.
 *
 * The remaining time is always derived from an absolute deadline stored with the
 * session, so killing the app (or rebooting the device) cannot restart the clock.
 */
object ExamTimer {
    const val EXAM_DURATION_SECONDS: Long = 90L * 60L
    const val EXAM_DURATION_MS: Long = EXAM_DURATION_SECONDS * 1000L

    fun deadlineFrom(startedAt: Long): Long = startedAt + EXAM_DURATION_MS

    fun remainingSeconds(deadline: Long, now: Long): Long =
        ((deadline - now).coerceAtLeast(0L)) / 1000L

    /**
     * True when the exam was resumed with less than a full paper's worth of time,
     * i.e. the student should be told the clock kept running while the app was closed.
     */
    fun isResumed(deadline: Long, now: Long): Boolean =
        remainingSeconds(deadline, now) < EXAM_DURATION_SECONDS
}
