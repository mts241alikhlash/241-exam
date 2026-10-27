package id.sch.mts241alikhlash.cbt.ui.screens

import androidx.compose.foundation.Image
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import id.sch.mts241alikhlash.cbt.R
import id.sch.mts241alikhlash.cbt.ui.theme.BorderFocus
import id.sch.mts241alikhlash.cbt.ui.theme.BorderSubtle
import id.sch.mts241alikhlash.cbt.ui.theme.BrandBackground
import id.sch.mts241alikhlash.cbt.ui.theme.BrandMuted
import id.sch.mts241alikhlash.cbt.ui.theme.BrandNavy
import id.sch.mts241alikhlash.cbt.ui.theme.BrandNavySubtle
import id.sch.mts241alikhlash.cbt.ui.theme.BrandSurface
import id.sch.mts241alikhlash.cbt.ui.theme.DMSansFontFamily
import id.sch.mts241alikhlash.cbt.ui.theme.DoubtfulOrange
import id.sch.mts241alikhlash.cbt.ui.theme.DoubtfulOrangeLight
import id.sch.mts241alikhlash.cbt.ui.theme.StatusError
import id.sch.mts241alikhlash.cbt.ui.theme.StatusSuccess
import id.sch.mts241alikhlash.cbt.ui.theme.StatusSuccessLight
import id.sch.mts241alikhlash.cbt.ui.theme.TextMuted
import id.sch.mts241alikhlash.cbt.ui.theme.TextPrimary
import id.sch.mts241alikhlash.cbt.ui.theme.TextSecondary

@Composable
fun ExamScreen(
    viewModel: ExamViewModel,
    onFinish: () -> Unit
) {
    val state by viewModel.uiState.collectAsState()
    ExamContent(
        state = state,
        onSelectAnswer = viewModel::selectAnswer,
        onToggleDoubtful = viewModel::toggleDoubtful,
        onGoToIndex = viewModel::goToIndex,
        onSubmit = viewModel::submitExam,
        onRetry = viewModel::retry,
        onFinish = onFinish
    )
}

@Composable
fun ExamContent(
    state: ExamUiState,
    onSelectAnswer: (String, String) -> Unit,
    onToggleDoubtful: (String) -> Unit,
    onGoToIndex: (Int) -> Unit,
    onSubmit: () -> Unit,
    onRetry: () -> Unit,
    onFinish: () -> Unit
) {
    var showConfirmDialog by remember { mutableStateOf(false) }

    if (state.isSubmitted) {
        LaunchedEffect(Unit) {
            onFinish()
        }
    }

    LaunchedEffect(state.submitError) {
        if (state.submitError != null) {
            showConfirmDialog = true
        }
    }

    if (showConfirmDialog) {
        AlertDialog(
            onDismissRequest = { if (!state.isSubmitting) showConfirmDialog = false },
            shape = RoundedCornerShape(10.dp),
            containerColor = BrandSurface,
            title = {
                Text(
                    text = "Kumpulkan Lembar Ujian?",
                    fontFamily = DMSansFontFamily,
                    fontWeight = FontWeight.Bold,
                    fontSize = 17.sp,
                    color = TextPrimary
                )
            },
            text = {
                val answered = state.answers.size
                val total = state.questions.size
                val doubtfulCount = state.doubtfuls.size
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        text = "Anda telah menjawab $answered dari $total soal.",
                        fontFamily = DMSansFontFamily,
                        fontSize = 13.sp,
                        color = TextPrimary
                    )
                    if (doubtfulCount > 0) {
                        Text(
                            text = "Terdapat $doubtfulCount soal yang masih ditandai ragu-ragu.",
                            fontFamily = DMSansFontFamily,
                            fontSize = 12.sp,
                            color = DoubtfulOrange
                        )
                    }
                    Text(
                        text = "Setelah dikumpulkan, jawaban tidak dapat diubah kembali.",
                        fontFamily = DMSansFontFamily,
                        fontSize = 12.sp,
                        color = TextMuted
                    )
                    if (state.isSubmitting) {
                        Text(
                            text = "Mengirim jawaban…",
                            fontFamily = DMSansFontFamily,
                            fontWeight = FontWeight.Medium,
                            fontSize = 12.sp,
                            color = TextSecondary
                        )
                    }
                    state.submitError?.let { error ->
                        Text(
                            text = error,
                            fontFamily = DMSansFontFamily,
                            fontWeight = FontWeight.Medium,
                            fontSize = 12.sp,
                            color = StatusError
                        )
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        showConfirmDialog = false
                        onSubmit()
                    },
                    enabled = !state.isSubmitting,
                    shape = RoundedCornerShape(8.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = BrandNavy)
                ) {
                    Text(
                        text = "Ya, Kumpulkan",
                        fontFamily = DMSansFontFamily,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 13.sp
                    )
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { showConfirmDialog = false },
                    enabled = !state.isSubmitting
                ) {
                    Text(
                        text = "Periksa Kembali",
                        fontFamily = DMSansFontFamily,
                        fontWeight = FontWeight.Medium,
                        fontSize = 13.sp,
                        color = TextMuted
                    )
                }
            }
        )
    }

    Scaffold(
        topBar = {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(54.dp),
                color = BrandSurface,
                border = androidx.compose.foundation.BorderStroke(1.dp, BorderSubtle)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Image(
                            painter = painterResource(id = R.drawable.logo_name),
                            contentDescription = "Logo",
                            modifier = Modifier.height(28.dp),
                            contentScale = ContentScale.Fit
                        )
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(4.dp))
                                .background(BrandNavySubtle)
                                .padding(horizontal = 6.dp, vertical = 2.dp)
                        ) {
                            Text(
                                text = "ASSESSMENT",
                                fontFamily = DMSansFontFamily,
                                fontWeight = FontWeight.Bold,
                                fontSize = 10.sp,
                                color = BrandNavy
                            )
                        }
                    }

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        val minutes = state.remainingSeconds / 60
                        val seconds = state.remainingSeconds % 60
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = BrandMuted,
                            border = androidx.compose.foundation.BorderStroke(1.dp, BorderSubtle)
                        ) {
                            Text(
                                text = "%02d:%02d".format(minutes, seconds),
                                fontFamily = DMSansFontFamily,
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp,
                                color = if (state.remainingSeconds < 300) StatusError else TextPrimary,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                            )
                        }

                        Button(
                            onClick = { showConfirmDialog = true },
                            // Cannot finish a paper that never loaded, or one already sending.
                            enabled = state.canSubmit,
                            colors = ButtonDefaults.buttonColors(containerColor = StatusError),
                            shape = RoundedCornerShape(6.dp),
                            modifier = Modifier.height(32.dp),
                            contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 12.dp, vertical = 0.dp)
                        ) {
                            Text(
                                text = if (state.isSubmitting) "Mengirim…" else "Selesai",
                                fontFamily = DMSansFontFamily,
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 12.sp
                            )
                        }
                    }
                }
            }
        },
        bottomBar = {
            if (state.questions.isNotEmpty()) {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    color = BrandSurface,
                    border = androidx.compose.foundation.BorderStroke(1.dp, BorderSubtle)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 10.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        OutlinedButton(
                            onClick = { onGoToIndex(state.currentIndex - 1) },
                            enabled = state.currentIndex > 0 && !state.isSubmitting,
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.height(38.dp)
                        ) {
                            Text(
                                text = "Sebelumnya",
                                fontFamily = DMSansFontFamily,
                                fontWeight = FontWeight.Medium,
                                fontSize = 13.sp
                            )
                        }

                        val currentQ = state.questions[state.currentIndex]
                        val isDoubt = state.doubtfuls.contains(currentQ.id)

                        OutlinedButton(
                            onClick = { onToggleDoubtful(currentQ.id) },
                            enabled = state.canAnswer,
                            shape = RoundedCornerShape(8.dp),
                            border = androidx.compose.foundation.BorderStroke(
                                1.dp,
                                if (isDoubt) DoubtfulOrange else BorderSubtle
                            ),
                            colors = ButtonDefaults.outlinedButtonColors(
                                containerColor = if (isDoubt) DoubtfulOrangeLight else Color.Transparent
                            ),
                            modifier = Modifier.height(38.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Warning,
                                contentDescription = null,
                                tint = if (isDoubt) DoubtfulOrange else TextMuted,
                                modifier = Modifier.size(14.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "Ragu-ragu",
                                fontFamily = DMSansFontFamily,
                                fontWeight = FontWeight.Medium,
                                fontSize = 12.sp,
                                color = if (isDoubt) DoubtfulOrange else TextSecondary
                            )
                        }

                        if (state.currentIndex < state.questions.size - 1) {
                            Button(
                                onClick = { onGoToIndex(state.currentIndex + 1) },
                                enabled = !state.isSubmitting,
                                shape = RoundedCornerShape(8.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = BrandNavy),
                                modifier = Modifier.height(38.dp)
                            ) {
                                Text(
                                    text = "Selanjutnya",
                                    fontFamily = DMSansFontFamily,
                                    fontWeight = FontWeight.SemiBold,
                                    fontSize = 13.sp
                                )
                            }
                        } else {
                            Button(
                                onClick = { showConfirmDialog = true },
                                enabled = state.canSubmit,
                                shape = RoundedCornerShape(8.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = StatusSuccess),
                                modifier = Modifier.height(38.dp)
                            ) {
                                Text(
                                    text = if (state.isSubmitting) "Mengirim…" else "Kumpulkan",
                                    fontFamily = DMSansFontFamily,
                                    fontWeight = FontWeight.SemiBold,
                                    fontSize = 13.sp
                                )
                            }
                        }
                    }
                }
            }
        }
    ) { padding ->
        when {
            state.isLoading -> {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(BrandBackground)
                        .padding(padding),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(color = BrandNavy)
                }
            }

            state.errorMessage != null -> {
                ExamErrorContent(
                    message = state.errorMessage,
                    modifier = Modifier
                        .fillMaxSize()
                        .background(BrandBackground)
                        .padding(padding),
                    onRetry = onRetry
                )
            }

            state.questions.isEmpty() -> {
                ExamErrorContent(
                    message = ExamViewModel.EMPTY_PAPER_MESSAGE,
                    modifier = Modifier
                        .fillMaxSize()
                        .background(BrandBackground)
                        .padding(padding),
                    onRetry = onRetry
                )
            }

            else -> {
                val currentQ = state.questions[state.currentIndex]
                val selectedChoice = state.answers[currentQ.id]

                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(BrandBackground)
                        .padding(padding)
                        .padding(horizontal = 16.dp, vertical = 12.dp)
                ) {
                    if (state.isResumed) {
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = DoubtfulOrangeLight,
                            border = androidx.compose.foundation.BorderStroke(1.dp, DoubtfulOrange),
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(bottom = 10.dp)
                        ) {
                            Text(
                                text = "Ujian dilanjutkan. Sisa waktu mengikuti jam sebenarnya.",
                                fontFamily = DMSansFontFamily,
                                fontWeight = FontWeight.Medium,
                                fontSize = 12.sp,
                                color = DoubtfulOrange,
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
                            )
                        }
                    }

                    LazyRow(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 12.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        itemsIndexed(state.questions) { index, q ->
                            val isCurrent = index == state.currentIndex
                            val isAnswered = state.answers.containsKey(q.id)
                            val isDoubtItem = state.doubtfuls.contains(q.id)

                            val bgColor = when {
                                isCurrent -> BrandNavy
                                isDoubtItem -> DoubtfulOrange
                                isAnswered -> StatusSuccess
                                else -> BrandSurface
                            }

                            val textColor = when {
                                isCurrent || isDoubtItem || isAnswered -> Color.White
                                else -> TextSecondary
                            }

                            Box(
                                modifier = Modifier
                                    .size(34.dp)
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(bgColor)
                                    .border(
                                        1.dp,
                                        if (isCurrent) BrandNavy else BorderSubtle,
                                        RoundedCornerShape(6.dp)
                                    )
                                    .clickable { onGoToIndex(index) },
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = "${index + 1}",
                                    fontFamily = DMSansFontFamily,
                                    fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.Medium,
                                    fontSize = 12.sp,
                                    color = textColor
                                )
                            }
                        }
                    }

                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f)
                            .border(1.dp, BorderSubtle, RoundedCornerShape(10.dp)),
                        colors = CardDefaults.cardColors(containerColor = BrandSurface),
                        shape = RoundedCornerShape(10.dp),
                        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
                    ) {
                        LazyColumn(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(18.dp)
                        ) {
                            item {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(bottom = 14.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Surface(
                                        shape = RoundedCornerShape(6.dp),
                                        color = BrandMuted,
                                        border = androidx.compose.foundation.BorderStroke(1.dp, BorderSubtle)
                                    ) {
                                        Text(
                                            text = "Soal Nomor ${state.currentIndex + 1} dari ${state.questions.size}",
                                            fontFamily = DMSansFontFamily,
                                            fontWeight = FontWeight.SemiBold,
                                            fontSize = 12.sp,
                                            color = TextPrimary,
                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                        )
                                    }

                                    Surface(
                                        shape = RoundedCornerShape(6.dp),
                                        color = StatusSuccessLight
                                    ) {
                                        Text(
                                            text = currentQ.type.replace("_", " ").uppercase(),
                                            fontFamily = DMSansFontFamily,
                                            fontWeight = FontWeight.SemiBold,
                                            fontSize = 11.sp,
                                            color = StatusSuccess,
                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                                        )
                                    }
                                }
                            }

                            item {
                                Text(
                                    text = currentQ.bodyHtml.replace(Regex("<[^>]*>"), ""),
                                    fontFamily = DMSansFontFamily,
                                    fontSize = 15.sp,
                                    lineHeight = 22.sp,
                                    color = TextPrimary,
                                    modifier = Modifier.padding(bottom = 20.dp)
                                )
                            }

                            if (currentQ.choices != null) {
                                itemsIndexed(currentQ.choices) { index, choice ->
                                    val letter = ('A' + index).toString()
                                    val isSelected = selectedChoice == choice

                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(vertical = 5.dp)
                                            .clip(RoundedCornerShape(8.dp))
                                            .border(
                                                width = if (isSelected) 1.5.dp else 1.dp,
                                                color = if (isSelected) BorderFocus else BorderSubtle,
                                                shape = RoundedCornerShape(8.dp)
                                            )
                                            .background(
                                                color = if (isSelected) BrandNavySubtle else BrandSurface
                                            )
                                            // Answers are frozen while the paper is being submitted.
                                            .clickable(enabled = state.canAnswer) {
                                                onSelectAnswer(currentQ.id, choice)
                                            }
                                            .padding(horizontal = 14.dp, vertical = 12.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .size(28.dp)
                                                .clip(CircleShape)
                                                .background(if (isSelected) BrandNavy else BrandMuted)
                                                .border(
                                                    1.dp,
                                                    if (isSelected) BrandNavy else BorderSubtle,
                                                    CircleShape
                                                ),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Text(
                                                text = letter,
                                                fontFamily = DMSansFontFamily,
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 12.sp,
                                                color = if (isSelected) Color.White else TextSecondary
                                            )
                                        }

                                        Spacer(modifier = Modifier.width(12.dp))

                                        Text(
                                            text = choice,
                                            fontFamily = DMSansFontFamily,
                                            fontSize = 14.sp,
                                            color = if (isSelected) TextPrimary else TextSecondary,
                                            lineHeight = 20.sp,
                                            modifier = Modifier.weight(1f)
                                        )

                                        if (isSelected) {
                                            Icon(
                                                imageVector = Icons.Default.Check,
                                                contentDescription = null,
                                                tint = BrandNavy,
                                                modifier = Modifier.size(16.dp)
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * Shown when the paper cannot be displayed at all (no cache + offline, or an empty
 * payload for an active session). Offers a retry that forces a network fetch.
 */
@Composable
private fun ExamErrorContent(
    message: String,
    modifier: Modifier = Modifier,
    onRetry: () -> Unit
) {
    Box(
        modifier = modifier,
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                imageVector = Icons.Default.Warning,
                contentDescription = null,
                tint = StatusError,
                modifier = Modifier.size(36.dp)
            )
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = "Soal gagal dimuat",
                fontFamily = DMSansFontFamily,
                fontWeight = FontWeight.Bold,
                fontSize = 16.sp,
                color = TextPrimary,
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = message,
                fontFamily = DMSansFontFamily,
                fontSize = 13.sp,
                color = TextMuted,
                textAlign = TextAlign.Center,
                lineHeight = 19.sp
            )
            Spacer(modifier = Modifier.height(18.dp))
            Button(
                onClick = onRetry,
                shape = RoundedCornerShape(8.dp),
                colors = ButtonDefaults.buttonColors(containerColor = BrandNavy),
                modifier = Modifier.height(40.dp)
            ) {
                Text(
                    text = "Coba Lagi",
                    fontFamily = DMSansFontFamily,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 13.sp
                )
            }
        }
    }
}
