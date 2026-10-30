package id.sch.mts241alikhlash.cbt.proctor

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import android.util.Log
import id.sch.mts241alikhlash.cbt.MainActivity
import id.sch.mts241alikhlash.cbt.R
import id.sch.mts241alikhlash.cbt.data.model.CameraBlockedBody
import id.sch.mts241alikhlash.cbt.data.model.StreamTokenDto
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Keeps the front camera publishing while the exam session is open.
 *
 * Android 11 forbids *starting* a camera foreground service from the background;
 * it does not stop one that was started while the app was visible, so pressing
 * Home or turning the screen off keeps the stream running. Starting the service
 * is therefore always done from the exam screen (see ProctorController).
 */
class ProctorService : Service() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var wakelock: PowerManager.WakeLock? = null
    private var stream: StreamClient? = null
    private var loop: Job? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val sessionId = intent?.getStringExtra(EXTRA_SESSION_ID)
        if (intent?.action == ACTION_STOP || sessionId == null) {
            stopSelf()
            return START_NOT_STICKY
        }

        startInForeground()
        acquireWakelock()
        publish(sessionId)
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        loop?.cancel()
        stream?.stop()
        stream = null
        releaseWakelock()
        scope.cancel()
        super.onDestroy()
    }

    private fun startInForeground() {
        val manager = getSystemService(NotificationManager::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            manager.createNotificationChannel(
                NotificationChannel(
                    CHANNEL_ID,
                    getString(R.string.proctor_channel_name),
                    NotificationManager.IMPORTANCE_LOW,
                ),
            )
        }

        val open = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val notification: Notification = Notification.Builder(this, CHANNEL_ID)
            .setContentTitle(getString(R.string.proctor_notification_title))
            .setContentText(getString(R.string.proctor_notification_text))
            .setSmallIcon(android.R.drawable.presence_video_online)
            .setContentIntent(open)
            .setOngoing(true)
            .build()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(
                NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_CAMERA,
            )
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    private fun publish(sessionId: String) {
        loop?.cancel()
        loop = scope.launch {
            var attempt = 0
            var failingSince = 0L

            while (isActive) {
                val token = runCatching { fetchToken(sessionId) }.getOrNull()
                if (token == null) {
                    attempt++
                    if (failingSince == 0L) failingSince = System.currentTimeMillis()
                    reportIfLongFailing(sessionId, failingSince, "network")
                    delay(StreamRetry.delayFor(attempt))
                    continue
                }

                val ended = kotlinx.coroutines.CompletableDeferred<String>()
                val client = StreamClient(this@ProctorService) { reason ->
                    ended.complete(reason)
                }
                stream = client
                client.publish(token.publishUrl)

                val reason = ended.await()
                Log.i(TAG, "stream ended: $reason")
                client.stop()
                stream = null

                if (reason == "auth" || reason == "prepare") {
                    // Nothing about waiting fixes these; ask for a fresh token.
                    failingSince = System.currentTimeMillis()
                }
                attempt++
                if (failingSince == 0L) failingSince = System.currentTimeMillis()
                reportIfLongFailing(sessionId, failingSince, reason)
                delay(StreamRetry.delayFor(attempt))
            }
        }
    }

    private suspend fun reportIfLongFailing(
        sessionId: String,
        since: Long,
        reason: String,
    ) {
        if (since == 0L) return
        if (System.currentTimeMillis() - since < StreamRetry.REPORT_AFTER_MS) return
        runCatching {
            ProctorApi.service(this).reportCameraBlocked(
                sessionId,
                CameraBlockedBody(reasonFor(reason)),
            )
        }
    }

    private fun reasonFor(reason: String): String = when (reason) {
        "auth", "network" -> "network"
        "prepare" -> "permission"
        "disconnected" -> "stopped"
        else -> "network"
    }

    private suspend fun fetchToken(sessionId: String): StreamTokenDto =
        ProctorApi.service(this).streamToken(sessionId)

    private fun acquireWakelock() {
        if (wakelock != null) return
        val power = getSystemService(PowerManager::class.java)
        wakelock = power
            .newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "$packageName:proctor")
            .apply {
                setReferenceCounted(false)
                acquire()
            }
    }

    private fun releaseWakelock() {
        runCatching { wakelock?.takeIf { it.isHeld }?.release() }
        wakelock = null
    }

    companion object {
        private const val TAG = "ProctorService"
        private const val CHANNEL_ID = "exam_proctor"
        private const val NOTIFICATION_ID = 4101
        private const val EXTRA_SESSION_ID = "sessionId"
        const val ACTION_STOP = "id.sch.mts241alikhlash.cbt.proctor.STOP"

        fun start(context: Context, sessionId: String) {
            val intent = Intent(context, ProctorService::class.java)
                .putExtra(EXTRA_SESSION_ID, sessionId)
            context.startForegroundService(intent)
        }

        fun stop(context: Context) {
            context.startService(
                Intent(context, ProctorService::class.java).setAction(ACTION_STOP),
            )
        }
    }
}
