package id.sch.mts241alikhlash.cbt.proctor

/**
 * Starts and stops the exam camera from shared code, without the shared code
 * knowing what a camera is.
 *
 * The `standard` flavor passes the no-op defaults, so `ExamScreen` can call this
 * unconditionally and the class stays free of any proctoring dependency.
 */
class ProctorController(
    private val startStream: (sessionId: String) -> Unit = {},
    private val stopStream: () -> Unit = {},
    private val reportBlocked: (sessionId: String, reason: String) -> Unit = { _, _ -> },
) {

    fun start(sessionId: String) = startStream(sessionId)

    fun stop() = stopStream()

    /** Records why the camera is not streaming. Never counts as a violation. */
    fun blocked(sessionId: String, reason: String) = reportBlocked(sessionId, reason)
}
