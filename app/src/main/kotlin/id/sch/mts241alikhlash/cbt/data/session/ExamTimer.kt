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

    /**
     * Startup latency that must not be mistaken for lost exam time.
     *
     * Reading the deadline back from DataStore and building the ViewModel takes a few
     * milliseconds, so a brand-new paper is always observed slightly after its start.
     * Without this slack every fresh exam would announce itself as "resumed".
     */
    const val RESUME_TOLERANCE_SECONDS: Long = 2L

    fun deadlineFrom(startedAt: Long): Long = startedAt + EXAM_DURATION_MS

    /**
     * Whole seconds left, rounded **up**.
     *
     * Rounding up is what makes a fresh 90-minute paper read `90:00` instead of `89:59`
     * the instant it starts, and it keeps the clock from showing `00:00` while time is
     * still on it (any remainder above zero still counts as one second).
     */
    fun remainingSeconds(deadline: Long, now: Long): Long {
        val millisLeft = (deadline - now).coerceAtLeast(0L)
        return (millisLeft + 999L) / 1000L
    }

    /**
     * True when the exam was genuinely resumed with less time than a full paper,
     * i.e. the student should be told the clock kept running while the app was closed.
     *
     * [RESUME_TOLERANCE_SECONDS] of slack absorbs process-start latency; only a real gap
     * (the app was away for more than that) counts as resumed.
     */
    fun isResumed(deadline: Long, now: Long, restored: Boolean? = null): Boolean =
        restored ?: (remainingSeconds(deadline, now) < EXAM_DURATION_SECONDS - RESUME_TOLERANCE_SECONDS)
}
