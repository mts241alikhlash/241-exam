package id.sch.mts241alikhlash.cbt.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import id.sch.mts241alikhlash.cbt.ui.theme.BrandNavy
import id.sch.mts241alikhlash.cbt.ui.theme.BrandSurface
import id.sch.mts241alikhlash.cbt.ui.theme.DMSansFontFamily
import id.sch.mts241alikhlash.cbt.ui.theme.StatusError
import id.sch.mts241alikhlash.cbt.ui.theme.TextMuted
import id.sch.mts241alikhlash.cbt.ui.theme.TextPrimary

/** Covers the exam when the OS reports the device is not pinned. */
@Composable
fun KioskWarningOverlay(onRetry: () -> Unit) {
    val family: androidx.compose.ui.text.font.FontFamily = DMSansFontFamily
    Surface(modifier = Modifier.fillMaxSize(), color = BrandSurface) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(BrandSurface)
                .padding(28.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "Perangkat tidak terkunci",
                fontFamily = family,
                fontWeight = FontWeight.Bold,
                fontSize = 20.sp,
                color = StatusError,
                textAlign = TextAlign.Center
            )
            Text(
                text = "Ujian dijeda karena kunci layar ditolak. Panggil pengawas, lalu kunci kembali perangkat untuk melanjutkan.",
                fontFamily = family,
                fontSize = 14.sp,
                color = TextPrimary,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = 12.dp)
            )
            Text(
                text = "Jawaban yang sudah tersimpan tetap aman.",
                fontFamily = family,
                fontSize = 12.sp,
                color = TextMuted,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = 8.dp, bottom = 24.dp)
            )
            Button(
                onClick = onRetry,
                shape = RoundedCornerShape(8.dp),
                colors = ButtonDefaults.buttonColors(containerColor = BrandNavy)
            ) {
                Text(
                    text = "Kunci Lagi",
                    fontFamily = family,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 14.sp
                )
            }
        }
    }
}
