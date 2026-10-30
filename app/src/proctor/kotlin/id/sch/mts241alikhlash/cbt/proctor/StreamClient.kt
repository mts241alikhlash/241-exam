package id.sch.mts241alikhlash.cbt.proctor

import android.content.Context
import android.util.Log
import com.pedro.common.ConnectChecker
import com.pedro.encoder.input.sources.audio.NoAudioSource
import com.pedro.encoder.input.sources.video.Camera2Source
import com.pedro.encoder.input.video.CameraHelper
import com.pedro.library.whip.WhipStream

/**
 * The camera and the WHIP connection, wrapped so the service never touches
 * encoder types directly.
 *
 * WHIP: both RTMP and SRT opened their socket and reported success while sending
 * no media, so the server saw a connection and then EOF. WHIP publishes over
 * HTTP, which is the transport this design picked in the first place.
 *
 * A camera held by another app (a video call, the system camera) makes the
 * source fail to open. That ends the stream with reason `camera_busy`, which the
 * service reports as `cameraBusy` and retries — it never takes the exam down.
 */
class StreamClient(
    context: Context,
    private val onEnded: (reason: String) -> Unit,
) {
    private val appContext = context.applicationContext

    @Volatile
    private var stream: WhipStream? = null

    fun publish(url: String) {
        if (stream?.isStreaming == true) return

        val checker = object : ConnectChecker {
            override fun onConnectionStarted(url: String) {
                proctorLog("conn started")
            }

            override fun onConnectionSuccess() {
                proctorLog("conn success")
            }

            override fun onConnectionFailed(reason: String) {
                proctorLog("conn failed: $reason")
                // "sps or pps is null" and the camera-open failures both mean the
                // source never delivered a frame, which on a phone almost always
                // means another app holds the camera.
                val busy = reason.contains("sps or pps", ignoreCase = true) ||
                    reason.contains("camera", ignoreCase = true) ||
                    reason.contains("access", ignoreCase = true)
                onEnded(if (busy) "camera_busy" else reason)
            }

            override fun onNewBitrate(bitrate: Long) = Unit

            override fun onDisconnect() {
                proctorLog("disconnected")
                onEnded("disconnected")
            }

            override fun onAuthError() = onEnded("auth")

            override fun onAuthSuccess() = Unit
        }

        // NoAudioSource, not the default microphone source: the app holds no
        // RECORD_AUDIO permission and asking for one would add an audio consent
        // problem the feature does not need. Seeing a face needs no sound.
        // Front camera, set before the source starts. switchCamera() only flips the
        // facing while the source is idle; called after startStream it restarts the
        // camera and the encoder can miss the first keyframe (SPS/PPS) again.
        val camera = Camera2Source(appContext)
        if (camera.getCameraFacing() != CameraHelper.Facing.FRONT) {
            camera.switchCamera()
        }
        val created = WhipStream(appContext, checker, camera, NoAudioSource())
        stream = created

        val prepared = runCatching {
            // prepareAudio is required even with NoAudioSource: startStream starts
            // every encoder it was prepared with, and an unprepared one throws
            // "AudioEncoder not prepared yet". NoAudioSource just supplies no frames.
            // Argument order is (width, height, bitrate, fps, ...). Swapping fps and
            // bitrate here is what produced a 159 kbps stream at 2 000 000 fps.
            created.prepareVideo(
                width = WIDTH,
                height = HEIGHT,
                bitrate = BITRATE,
                fps = FRAME_RATE,
                iFrameInterval = 1,
            ) &&
                created.prepareAudio(44_100, true, 64 * 1024, true, false)
        }.getOrElse {
            proctorLog("prepare failed: $it")
            Log.w(TAG, "prepare failed", it)
            false
        }
        if (!prepared) {
            runCatching { created.release() }
            stream = null
            onEnded("prepare")
            return
        }

        proctorLog("starting stream")
        runCatching { created.startStream(url) }.onFailure {
            proctorLog("startStream failed: $it")
            Log.w(TAG, "startStream failed", it)
            runCatching { created.release() }
            stream = null
            onEnded("prepare")
        }
        proctorLog("startStream returned, isStreaming=${created.isStreaming}")
        // The encoder only produces bytes once the GL pipeline receives camera
        // frames; a connected socket with no data is exactly this failing.
        Thread {
            repeat(6) { step ->
                Thread.sleep(1_000)
                proctorLog(
                    "t+${step + 1}s streaming=${created.isStreaming} " +
                        "cameraRunning=${camera.isRunning()}",
                )
            }
        }.start()
    }

    fun stop() {
        stream?.let { current ->
            runCatching { if (current.isStreaming) current.stopStream() }
            runCatching { current.release() }
        }
        stream = null
    }

    private companion object {
        const val TAG = "StreamClient"
        // 720p at 24 fps: a supervisor has to recognise a face on a small tile, and
        // 640x480 at 12 fps was visibly blocky. This costs about 2 Mbps per student
        // upstream; a class of 30 needs ~60 Mbps, which a school LAN carries.
        const val WIDTH = 1280
        const val HEIGHT = 720
        const val FRAME_RATE = 24
        const val BITRATE = 2_000 * 1024
    }
}
