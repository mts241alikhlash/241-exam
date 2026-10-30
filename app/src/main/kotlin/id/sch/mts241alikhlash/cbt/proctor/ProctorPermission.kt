package id.sch.mts241alikhlash.cbt.proctor

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat

/**
 * Starts the camera stream for a session, asking for the camera permission first
 * when it has not been granted.
 *
 * The request has to happen while the exam screen is visible: from Android 14 a
 * `camera` foreground service cannot be created without the permission, and from
 * Android 11 it cannot be created from the background at all.
 *
 * A denied permission never blocks the exam. The student answers as usual and the
 * supervisor sees a seat with no camera, with the reason attached.
 */
@Composable
fun rememberProctorPermission(sessionId: String, controller: ProctorController) {
    val context = LocalContext.current

    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        if (granted) controller.start(sessionId) else controller.blocked(sessionId, "permission")
    }

    LaunchedEffect(sessionId, controller) {
        // `standard` declares no CAMERA permission; asking for one would be an
        // instant denial and a pointless dialog.
        if (!ProctorFlags.ENABLED) return@LaunchedEffect
        val granted = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.CAMERA,
        ) == PackageManager.PERMISSION_GRANTED
        if (granted) {
            controller.start(sessionId)
        } else {
            launcher.launch(Manifest.permission.CAMERA)
        }
    }
}
