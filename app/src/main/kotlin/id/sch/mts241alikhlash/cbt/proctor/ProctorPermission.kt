package id.sch.mts241alikhlash.cbt.proctor

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import id.sch.mts241alikhlash.cbt.ui.theme.WebTokens

/** What the exam screen has to show about the camera permission right now. */
enum class CameraPermissionState { GRANTED, ASK, DENIED, BLOCKED }

/**
 * Asks for the camera permission when the exam screen opens and starts the stream
 * once it is held.
 *
 * The request has to happen while the exam screen is visible: from Android 14 a
 * `camera` foreground service cannot be created without the permission, and from
 * Android 11 it cannot be created from the background at all.
 *
 * A denial never blocks the exam. It does, however, have to be recoverable: a
 * student who taps "Don't allow" by accident gets a button to ask again, and once
 * Android stops showing the dialog (permanently denied) the button opens the app's
 * settings page instead of doing nothing.
 */
@Composable
fun rememberProctorPermission(
    sessionId: String,
    controller: ProctorController,
): CameraPermissionState {
    val context = LocalContext.current
    var state by remember { mutableStateOf(CameraPermissionState.ASK) }

    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        if (granted) {
            state = CameraPermissionState.GRANTED
            controller.start(sessionId)
        } else {
            val canAskAgain = ActivityCompat.shouldShowRequestPermissionRationale(
                context as? android.app.Activity ?: return@rememberLauncherForActivityResult,
                Manifest.permission.CAMERA,
            )
            state = if (canAskAgain) {
                CameraPermissionState.DENIED
            } else {
                CameraPermissionState.BLOCKED
            }
            controller.blocked(sessionId, "permission")
        }
    }

    LaunchedEffect(sessionId, controller) {
        // `standard` declares no CAMERA permission; asking for one would be an
        // instant denial and a pointless dialog.
        if (!ProctorFlags.ENABLED) return@LaunchedEffect

        val held = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.CAMERA,
        ) == PackageManager.PERMISSION_GRANTED

        // The launch-time request already asked once; asking again the instant the
        // exam opens would be a second dialog for the same answer. Show the banner
        // and let the student tap it.
        state = if (held) {
            controller.start(sessionId)
            CameraPermissionState.GRANTED
        } else {
            CameraPermissionState.DENIED
        }
    }

    return state
}

/** Asks again, or opens Settings when Android will no longer show the dialog. */
fun requestCameraAgain(
    context: android.content.Context,
    state: CameraPermissionState,
    launch: () -> Unit,
) {
    if (state == CameraPermissionState.BLOCKED) {
        context.startActivity(
            Intent(
                Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                Uri.fromParts("package", context.packageName, null),
            ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        )
    } else {
        launch()
    }
}

/**
 * The banner the exam screen shows while the camera permission is missing. It is
 * deliberately visible: a student who cannot see why the camera is off cannot fix
 * it, and a supervisor sees the same seat red on the other side.
 */
@Composable
fun CameraPermissionBanner(
    state: CameraPermissionState,
    onRequest: () -> Unit,
    modifier: Modifier = Modifier,
) {
    if (state == CameraPermissionState.GRANTED || state == CameraPermissionState.ASK) return

    val blocked = state == CameraPermissionState.BLOCKED
    Card(
        modifier = modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
        colors = CardDefaults.cardColors(containerColor = WebTokens.Background),
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = "Kamera belum diizinkan",
                fontSize = 13.sp,
                fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold,
                color = WebTokens.Foreground,
            )
            Text(
                text = if (blocked) {
                    "Izin kamera dimatikan. Buka Pengaturan, lalu izinkan Kamera untuk aplikasi ini."
                } else {
                    "Ujian tetap berjalan, tetapi pengawas tidak bisa melihat kamera. Izinkan kamera sekarang."
                },
                fontSize = 12.sp,
                color = WebTokens.MutedForeground,
            )
            Button(
                onClick = onRequest,
                colors = ButtonDefaults.buttonColors(containerColor = WebTokens.Primary),
            ) {
                Text(if (blocked) "Buka Pengaturan" else "Izinkan kamera", fontSize = 13.sp)
            }
        }
    }
}
