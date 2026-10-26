package id.sch.mts241alikhlash.cbt.ui.screens

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import id.sch.mts241alikhlash.cbt.R
import id.sch.mts241alikhlash.cbt.ui.theme.BorderSubtle
import id.sch.mts241alikhlash.cbt.ui.theme.BrandBackground
import id.sch.mts241alikhlash.cbt.ui.theme.BrandMuted
import id.sch.mts241alikhlash.cbt.ui.theme.BrandNavy
import id.sch.mts241alikhlash.cbt.ui.theme.BrandSurface
import id.sch.mts241alikhlash.cbt.ui.theme.DMSansFontFamily
import id.sch.mts241alikhlash.cbt.ui.theme.StatusError
import id.sch.mts241alikhlash.cbt.ui.theme.TextMuted
import id.sch.mts241alikhlash.cbt.ui.theme.TextPrimary
import id.sch.mts241alikhlash.cbt.ui.theme.TextSecondary

@Composable
fun TokenScreen(
    onStartExam: (sessionId: String) -> Unit
) {
    var sessionId by remember { mutableStateOf("") }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var showProctorHint by remember { mutableStateOf(false) }

    fun submit() {
        val trimmed = sessionId.trim()
        if (trimmed.isEmpty()) {
            errorMessage = "Kode sesi atau token ujian wajib diisi"
        } else {
            errorMessage = null
            onStartExam(trimmed)
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(BrandBackground)
    ) {
        Image(
            painter = painterResource(id = R.drawable.bg),
            contentDescription = null,
            modifier = Modifier
                .fillMaxSize()
                .alpha(0.035f),
            contentScale = ContentScale.Crop
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp, vertical = 28.dp),
            verticalArrangement = Arrangement.SpaceBetween,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 12.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Image(
                    painter = painterResource(id = R.drawable.logo_name),
                    contentDescription = "Logo MTs 241 Al-Ikhlash",
                    modifier = Modifier
                        .height(48.dp)
                        .padding(horizontal = 12.dp),
                    contentScale = ContentScale.Fit
                )
            }

            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 20.dp)
                    .border(1.dp, BorderSubtle, RoundedCornerShape(10.dp)),
                shape = RoundedCornerShape(10.dp),
                colors = CardDefaults.cardColors(containerColor = BrandSurface),
                elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "Selamat Datang",
                        fontSize = 22.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = DMSansFontFamily,
                        color = TextPrimary,
                        letterSpacing = (-0.3).sp,
                        textAlign = TextAlign.Center
                    )

                    Spacer(modifier = Modifier.height(4.dp))

                    Text(
                        text = "Masukkan kode sesi atau token ujian dari pengawas untuk memulai.",
                        fontSize = 13.sp,
                        fontFamily = DMSansFontFamily,
                        color = TextMuted,
                        textAlign = TextAlign.Center,
                        lineHeight = 18.sp
                    )

                    Spacer(modifier = Modifier.height(22.dp))

                    OutlinedTextField(
                        value = sessionId,
                        onValueChange = {
                            sessionId = it
                            if (errorMessage != null) {
                                errorMessage = null
                            }
                        },
                        label = {
                            Text(
                                text = "Kode Sesi / Token Ujian",
                                fontFamily = DMSansFontFamily,
                                fontSize = 13.sp
                            )
                        },
                        placeholder = {
                            Text(
                                text = "Contoh: SESI-01",
                                fontFamily = DMSansFontFamily,
                                fontSize = 13.sp
                            )
                        },
                        singleLine = true,
                        isError = errorMessage != null,
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.fillMaxWidth(),
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Go),
                        keyboardActions = KeyboardActions(onGo = { submit() }),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = BrandNavy,
                            focusedLabelColor = BrandNavy,
                            unfocusedBorderColor = BorderSubtle,
                            errorBorderColor = StatusError
                        )
                    )

                    if (errorMessage != null) {
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = errorMessage!!,
                            color = StatusError,
                            fontSize = 12.sp,
                            fontFamily = DMSansFontFamily,
                            modifier = Modifier.fillMaxWidth(),
                            textAlign = TextAlign.Start
                        )
                    }

                    Spacer(modifier = Modifier.height(18.dp))

                    Button(
                        onClick = { submit() },
                        shape = RoundedCornerShape(8.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = BrandNavy),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(44.dp)
                    ) {
                        Text(
                            text = "Mulai Ujian",
                            fontSize = 14.sp,
                            fontFamily = DMSansFontFamily,
                            fontWeight = FontWeight.SemiBold
                        )
                    }

                    Spacer(modifier = Modifier.height(18.dp))

                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = BrandMuted,
                        border = androidx.compose.foundation.BorderStroke(1.dp, BorderSubtle),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp, vertical = 9.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Lock,
                                contentDescription = null,
                                tint = TextMuted,
                                modifier = Modifier.size(13.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "Mode Kiosk Terproteksi · Anti-Curang",
                                fontSize = 11.sp,
                                fontFamily = DMSansFontFamily,
                                fontWeight = FontWeight.Medium,
                                color = TextSecondary
                            )
                        }
                    }
                }
            }

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 8.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                TextButton(
                    onClick = { showProctorHint = !showProctorHint }
                ) {
                    Text(
                        text = "Masuk sebagai pengawas",
                        fontSize = 13.sp,
                        fontFamily = DMSansFontFamily,
                        fontWeight = FontWeight.Medium,
                        color = TextMuted
                    )
                }

                if (showProctorHint) {
                    Text(
                        text = "Akses pengawas dikelola melalui portal web admin madrasah.",
                        fontSize = 11.sp,
                        fontFamily = DMSansFontFamily,
                        color = TextSecondary,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
                    )
                }

                Spacer(modifier = Modifier.height(6.dp))

                Text(
                    text = "241 Assessment · MTs 241 Al-Ikhlash",
                    fontSize = 11.sp,
                    fontFamily = DMSansFontFamily,
                    color = TextMuted,
                    textAlign = TextAlign.Center
                )
            }
        }
    }
}
