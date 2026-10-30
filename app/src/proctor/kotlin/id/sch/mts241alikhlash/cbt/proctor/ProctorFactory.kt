package id.sch.mts241alikhlash.cbt.proctor

import android.content.Context

/** `proctor` flavor: the controller starts and stops the real camera service. */
object ProctorFactory {
    fun controller(context: Context): ProctorController {
        val app = context.applicationContext
        return ProctorController(
            startStream = { sessionId -> ProctorService.start(app, sessionId) },
            stopStream = { ProctorService.stop(app) },
            reportBlocked = { sessionId, reason ->
                ProctorReporter.report(app, sessionId, reason)
            },
        )
    }
}
