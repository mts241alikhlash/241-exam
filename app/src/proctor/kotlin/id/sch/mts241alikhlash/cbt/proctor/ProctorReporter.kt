package id.sch.mts241alikhlash.cbt.proctor

import android.content.Context
import id.sch.mts241alikhlash.cbt.data.model.CameraBlockedBody
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/** Reports a camera failure when no service is running to report it itself. */
internal object ProctorReporter {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    fun report(context: Context, sessionId: String, reason: String) {
        val app = context.applicationContext
        scope.launch {
            runCatching {
                ProctorApi.service(app).reportCameraBlocked(
                    sessionId,
                    CameraBlockedBody(reason),
                )
            }
        }
    }
}
