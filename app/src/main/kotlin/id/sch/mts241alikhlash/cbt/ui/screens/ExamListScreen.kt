package id.sch.mts241alikhlash.cbt.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import id.sch.mts241alikhlash.cbt.data.model.ExamSummaryDto
import id.sch.mts241alikhlash.cbt.ui.theme.BorderSubtle
import id.sch.mts241alikhlash.cbt.ui.theme.BrandBackground
import id.sch.mts241alikhlash.cbt.ui.theme.BrandNavy
import id.sch.mts241alikhlash.cbt.ui.theme.BrandSurface
import id.sch.mts241alikhlash.cbt.ui.theme.DMSansFontFamily
import id.sch.mts241alikhlash.cbt.ui.theme.TextMuted
import id.sch.mts241alikhlash.cbt.ui.theme.TextPrimary
import id.sch.mts241alikhlash.cbt.ui.theme.TextSecondary

private fun statusLabel(status: String) = when (status) {
    "OPEN" -> "Dibuka"
    "IN_PROGRESS" -> "Sedang berlangsung"
    "UPCOMING" -> "Belum dibuka"
    "FINISHED" -> "Selesai"
    else -> status
}

@Composable
fun ExamListScreen(
    studentName: String,
    viewModel: ExamListViewModel,
    onSelect: (ExamSummaryDto) -> Unit,
    onLogout: () -> Unit
) {
    val state by viewModel.state.collectAsState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(BrandBackground)
            .padding(horizontal = 20.dp, vertical = 24.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Daftar Ujian",
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = DMSansFontFamily,
                    color = TextPrimary
                )
                Text(
                    text = studentName,
                    fontSize = 13.sp,
                    fontFamily = DMSansFontFamily,
                    color = TextMuted
                )
            }
            TextButton(onClick = onLogout) {
                Text("Keluar", fontFamily = DMSansFontFamily, color = TextMuted)
            }
        }
        Spacer(modifier = Modifier.height(16.dp))

        when (val current = state) {
            ExamListUiState.Loading -> Box(Modifier.fillMaxSize(), Alignment.Center) {
                CircularProgressIndicator(color = BrandNavy)
            }

            ExamListUiState.Empty -> Message(
                title = "Belum ada ujian",
                body = "Tidak ada ujian untuk Anda saat ini.",
                actionLabel = "Muat Ulang",
                onAction = viewModel::refresh
            )

            is ExamListUiState.Error -> Message(
                title = "Daftar ujian gagal dimuat",
                body = current.message,
                actionLabel = if (current.unauthorized) "Masuk Ulang" else "Coba Lagi",
                onAction = if (current.unauthorized) onLogout else viewModel::refresh
            )

            is ExamListUiState.Content -> LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                items(current.exams, key = { it.id }) { exam ->
                    ExamRow(exam, onSelect)
                }
            }
        }
    }
}

@Composable
private fun ExamRow(exam: ExamSummaryDto, onSelect: (ExamSummaryDto) -> Unit) {
    val selectable = exam.isSelectable()
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .alpha(if (selectable) 1f else 0.55f)
            .border(1.dp, BorderSubtle, RoundedCornerShape(10.dp))
            .background(BrandSurface, RoundedCornerShape(10.dp))
            .clickable(enabled = selectable) { onSelect(exam) }
            .padding(16.dp)
    ) {
        Text(
            text = exam.title,
            fontSize = 15.sp,
            fontWeight = FontWeight.SemiBold,
            fontFamily = DMSansFontFamily,
            color = TextPrimary
        )
        Spacer(modifier = Modifier.height(2.dp))
        Text(
            text = "${exam.durationMinutes} menit",
            fontSize = 12.sp,
            fontFamily = DMSansFontFamily,
            color = TextSecondary
        )
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = statusLabel(exam.status),
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium,
            fontFamily = DMSansFontFamily,
            color = if (selectable) BrandNavy else TextMuted
        )
    }
}

@Composable
private fun Message(title: String, body: String, actionLabel: String, onAction: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = title,
            fontSize = 16.sp,
            fontWeight = FontWeight.Bold,
            fontFamily = DMSansFontFamily,
            color = TextPrimary,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = body,
            fontSize = 13.sp,
            fontFamily = DMSansFontFamily,
            color = TextMuted,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(18.dp))
        Button(
            onClick = onAction,
            shape = RoundedCornerShape(8.dp),
            colors = ButtonDefaults.buttonColors(containerColor = BrandNavy)
        ) {
            Text(actionLabel, fontFamily = DMSansFontFamily, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
        }
    }
}
