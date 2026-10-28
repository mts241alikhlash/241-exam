package id.sch.mts241alikhlash.cbt.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import id.sch.mts241alikhlash.cbt.ui.theme.DMSansFontFamily
import id.sch.mts241alikhlash.cbt.ui.theme.StatusError
import id.sch.mts241alikhlash.cbt.ui.theme.TextMuted

/**
 * Supervisor-only escape hatch. The PIN is compiled into the APK, so this stops a
 * student but not someone who extracts the build; it exists for crash recovery.
 */
@Composable
fun SupervisorExitDialog(
    expectedPin: String,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit
) {
    var pin by remember { mutableStateOf("") }
    var wrong by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(text = "Keluar darurat", fontFamily = DMSansFontFamily, fontSize = 18.sp)
        },
        text = {
            Column {
                Text(
                    text = "Hanya untuk pengawas. Masukkan PIN untuk melepas kunci perangkat.",
                    fontFamily = DMSansFontFamily,
                    fontSize = 13.sp,
                    color = TextMuted
                )
                OutlinedTextField(
                    value = pin,
                    onValueChange = {
                        pin = it
                        wrong = false
                    },
                    modifier = Modifier.padding(top = 12.dp),
                    singleLine = true,
                    label = { Text("PIN pengawas", fontFamily = DMSansFontFamily) },
                    isError = wrong,
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword)
                )
                if (wrong) {
                    Text(
                        text = "PIN salah",
                        fontFamily = DMSansFontFamily,
                        fontSize = 12.sp,
                        color = StatusError,
                        modifier = Modifier.padding(top = 6.dp)
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { if (pin == expectedPin) onConfirm() else wrong = true }) {
                Text("Keluar", fontFamily = DMSansFontFamily)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Batal", fontFamily = DMSansFontFamily)
            }
        }
    )
}
