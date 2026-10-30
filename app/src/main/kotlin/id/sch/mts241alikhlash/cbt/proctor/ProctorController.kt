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
) {

    fun start(sessionId: String) = startStream(sessionId)

    fun stop() = stopStream()
}
