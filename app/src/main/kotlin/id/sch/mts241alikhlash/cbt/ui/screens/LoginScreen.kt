package id.sch.mts241alikhlash.cbt.ui.screens

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import id.sch.mts241alikhlash.cbt.R
import id.sch.mts241alikhlash.cbt.ui.theme.BorderSubtle
import id.sch.mts241alikhlash.cbt.ui.theme.BrandBackground
import id.sch.mts241alikhlash.cbt.ui.theme.BrandNavy
import id.sch.mts241alikhlash.cbt.ui.theme.BrandSurface
import id.sch.mts241alikhlash.cbt.ui.theme.DMSansFontFamily
import id.sch.mts241alikhlash.cbt.ui.theme.StatusError
import id.sch.mts241alikhlash.cbt.ui.theme.TextMuted
import id.sch.mts241alikhlash.cbt.ui.theme.TextPrimary

@Composable
fun LoginScreen(viewModel: LoginViewModel) {
    val state by viewModel.state.collectAsState()
    var identifier by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(BrandBackground)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp, vertical = 28.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Image(
            painter = painterResource(id = R.drawable.logo_name),
            contentDescription = "Logo MTs 241 Al-Ikhlash",
            modifier = Modifier.height(48.dp),
            contentScale = ContentScale.Fit
        )
        Spacer(modifier = Modifier.height(24.dp))
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .border(1.dp, BorderSubtle, RoundedCornerShape(10.dp)),
            shape = RoundedCornerShape(10.dp),
            colors = CardDefaults.cardColors(containerColor = BrandSurface),
            elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "Masuk",
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = DMSansFontFamily,
                    color = TextPrimary
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Gunakan akun siswa 241 Al-Ikhlash.",
                    fontSize = 13.sp,
                    fontFamily = DMSansFontFamily,
                    color = TextMuted,
                    textAlign = TextAlign.Center
                )
                Spacer(modifier = Modifier.height(20.dp))
                OutlinedTextField(
                    value = identifier,
                    onValueChange = { identifier = it },
                    label = { Text("NIS / Email", fontFamily = DMSansFontFamily, fontSize = 13.sp) },
                    singleLine = true,
                    enabled = !state.loading,
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.fillMaxWidth(),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                    colors = fieldColors()
                )
                Spacer(modifier = Modifier.height(12.dp))
                OutlinedTextField(
                    value = password,
                    onValueChange = { password = it },
                    label = { Text("Kata sandi", fontFamily = DMSansFontFamily, fontSize = 13.sp) },
                    singleLine = true,
                    enabled = !state.loading,
                    visualTransformation = PasswordVisualTransformation(),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.fillMaxWidth(),
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Password,
                        imeAction = ImeAction.Go
                    ),
                    colors = fieldColors()
                )
                state.error?.let { message ->
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = message,
                        color = StatusError,
                        fontSize = 12.sp,
                        fontFamily = DMSansFontFamily,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                Spacer(modifier = Modifier.height(18.dp))
                Button(
                    onClick = { viewModel.submit(identifier, password) },
                    enabled = !state.loading,
                    shape = RoundedCornerShape(8.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = BrandNavy),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(44.dp)
                ) {
                    if (state.loading) {
                        CircularProgressIndicator(
                            modifier = Modifier.height(20.dp),
                            strokeWidth = 2.dp,
                            color = BrandSurface
                        )
                    } else {
                        Text(
                            text = "Masuk",
                            fontSize = 14.sp,
                            fontFamily = DMSansFontFamily,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun fieldColors() = OutlinedTextFieldDefaults.colors(
    focusedBorderColor = BrandNavy,
    focusedLabelColor = BrandNavy,
    unfocusedBorderColor = BorderSubtle,
    errorBorderColor = StatusError
)
