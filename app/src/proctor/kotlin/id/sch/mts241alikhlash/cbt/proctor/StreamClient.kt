package id.sch.mts241alikhlash.cbt.proctor

import android.content.Context
import android.util.Log
import com.pedro.common.ConnectChecker
import com.pedro.library.rtmp.RtmpStream
import com.pedro.library.util.streamclient.RtmpStreamClient

/**
 * The camera and the RTMPS connection, wrapped so the service never touches
 * RootEncoder types directly.
 *
 * RTMPS rather than WHIP: WHIP only exists in RootEncoder 2.8.x, which needs AGP
 * 9.3 and Kotlin 2.4.10 while this project builds on AGP 8.9 and Kotlin 2.1.10.
 * RTMPS still runs over TLS on 443, so a home router has nothing to block.
 * Upgrade path: when the project moves to AGP 9, swap this class for the WHIP
 * client; nothing outside it needs to change.
 */
class StreamClient(
    context: Context,
    private val onEnded: (reason: String) -> Unit,
) {
    private val appContext = context.applicationContext

    @Volatile
    private var stream: RtmpStream? = null

    fun publish(url: String) {
        if (stream?.isStreaming == true) return

        val checker = object : ConnectChecker {
            override fun onConnectionStarted(url: String) = Unit
            override fun onConnectionSuccess() = Unit
            override fun onConnectionFailed(reason: String) = onEnded(reason)
            override fun onNewBitrate(bitrate: Long) = Unit
            override fun onDisconnect() = onEnded("disconnected")
            override fun onAuthError() = onEnded("auth")
            override fun onAuthSuccess() = Unit
        }

        val created = RtmpStream(appContext, checker)
        stream = created

        val prepared = runCatching {
            created.prepareVideo(WIDTH, HEIGHT, FRAME_RATE, BITRATE, 0, 2)
        }.getOrElse {
            Log.w(TAG, "prepareVideo failed", it)
            false
        }
        if (!prepared) {
            created.release()
            stream = null
            onEnded("prepare")
            return
        }

        created.startStream(url)
    }

    fun stop() {
        stream?.let { current ->
            runCatching { if (current.isStreaming) current.stopStream() }
            runCatching { current.release() }
        }
        stream = null
    }

    fun client(): RtmpStreamClient? = stream?.getStreamClient()

    private companion object {
        const val TAG = "StreamClient"
        const val WIDTH = 640
        const val HEIGHT = 480
        const val FRAME_RATE = 12
        const val BITRATE = 400 * 1024
    }
}
