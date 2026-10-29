package id.sch.mts241alikhlash.cbt.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import id.sch.mts241alikhlash.cbt.data.auth.SupervisorResult
import id.sch.mts241alikhlash.cbt.ui.theme.DMSansFontFamily
import id.sch.mts241alikhlash.cbt.ui.theme.StatusError
import id.sch.mts241alikhlash.cbt.ui.theme.TextMuted
import kotlinx.coroutines.launch

@Composable
fun SupervisorExitDialog(
    onDismiss: () -> Unit,
    onVerify: suspend (identifier: String, password: String) -> SupervisorResult,
    onConfirm: () -> Unit
) {
    var identifier by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var checking by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    AlertDialog(
        onDismissRequest = { if (!checking) onDismiss() },
        title = {
            Text(text = "Keluar darurat", fontFamily = DMSansFontFamily, fontSize = 18.sp)
        },
        text = {
            Column {
                Text(
                    text = "Hanya untuk pengawas. Masuk dengan akun guru untuk melepas kunci perangkat.",
                    fontFamily = DMSansFontFamily,
                    fontSize = 13.sp,
                    color = TextMuted
                )
                OutlinedTextField(
                    value = identifier,
                    onValueChange = {
                        identifier = it
                        message = null
                    },
                    modifier = Modifier.padding(top = 12.dp),
                    singleLine = true,
                    enabled = !checking,
                    label = { Text("Akun guru", fontFamily = DMSansFontFamily) },
                    isError = message != null
                )
                OutlinedTextField(
                    value = password,
                    onValueChange = {
                        password = it
                        message = null
                    },
                    modifier = Modifier.padding(top = 8.dp),
                    singleLine = true,
                    enabled = !checking,
                    label = { Text("Kata sandi", fontFamily = DMSansFontFamily) },
                    isError = message != null,
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password)
                )
                message?.let {
                    Text(
                        text = it,
                        fontFamily = DMSansFontFamily,
                        fontSize = 12.sp,
                        color = StatusError,
                        modifier = Modifier.padding(top = 6.dp)
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = !checking,
                onClick = {
                    checking = true
                    scope.launch {
                        val result = onVerify(identifier, password)
                        checking = false
                        when (result) {
                            SupervisorResult.Approved -> onConfirm()
                            SupervisorResult.Denied -> message = "Akun bukan pengawas atau kata sandi salah"
                            SupervisorResult.Offline -> message = "Pengawas perlu koneksi untuk membuka kunci."
                        }
                    }
                }
            ) {
                Text(if (checking) "Memeriksa..." else "Keluar", fontFamily = DMSansFontFamily)
            }
        },
        dismissButton = {
            TextButton(enabled = !checking, onClick = onDismiss) {
                Text("Batal", fontFamily = DMSansFontFamily)
            }
        }
    )
}
