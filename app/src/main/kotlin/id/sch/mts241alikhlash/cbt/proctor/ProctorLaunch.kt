package id.sch.mts241alikhlash.cbt.proctor

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.core.content.ContextCompat
import androidx.compose.ui.platform.LocalContext

/**
 * Asks for the camera permission once, when the app opens.
 *
 * Doing it here rather than at the start of an exam means the dialog never appears
 * over a running session, and a student who declines can still be asked again from
 * the exam screen. The `standard` flavor declares no CAMERA permission, so the
 * request is skipped there instead of being denied instantly.
 */
@Composable
fun RequestCameraOnLaunch() {
    if (!ProctorFlags.ENABLED) return

    val context = LocalContext.current
    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { }

    LaunchedEffect(Unit) {
        val held = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.CAMERA,
        ) == PackageManager.PERMISSION_GRANTED
        if (!held) launcher.launch(Manifest.permission.CAMERA)
    }
}
