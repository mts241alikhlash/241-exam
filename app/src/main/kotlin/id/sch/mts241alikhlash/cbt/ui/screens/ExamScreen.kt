package id.sch.mts241alikhlash.cbt.ui.screens

import id.sch.mts241alikhlash.cbt.ui.exam.ImageContext
import id.sch.mts241alikhlash.cbt.ui.exam.QuestionAnswerInput
import id.sch.mts241alikhlash.cbt.ui.exam.isFilled
import id.sch.mts241alikhlash.cbt.ui.exam.HtmlContent
import id.sch.mts241alikhlash.cbt.data.model.AnswerPayload
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
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
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
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
    sessionId: String,
    baseUrl: String,
    imageLoader: coil3.ImageLoader?,
    onFinish: () -> Unit,
    onSupervisorExit: () -> Unit = {},
    onSessionLost: (String) -> Unit = {}
) {
    val state by viewModel.uiState.collectAsState()
    ExamContent(
        state = state,
        onAnswer = viewModel::selectAnswer,
        onToggleKey = viewModel::toggleKey,
        onTextChange = viewModel::updateText,
        onToggleDoubtful = viewModel::toggleDoubtful,
        onGoToIndex = viewModel::goToIndex,
        onSubmit = viewModel::submitExam,
        onRetry = viewModel::retry,
        onFinish = onFinish,
        onSupervisorExit = onSupervisorExit,
        onSessionLost = onSessionLost,
        sessionId = sessionId,
        baseUrl = baseUrl,
        imageLoader = imageLoader
    )
}

@Composable
fun ExamContent(
    state: ExamUiState,
    onAnswer: (String, AnswerPayload) -> Unit,
    onToggleKey: (String, String) -> Unit = { _, _ -> },
    onTextChange: (String, String) -> Unit = { _, _ -> },
    onToggleDoubtful: (String) -> Unit,
    onGoToIndex: (Int) -> Unit,
    onSubmit: () -> Unit,
    onRetry: () -> Unit,
    onFinish: () -> Unit,
    onSupervisorExit: () -> Unit = {},
    onSessionLost: (String) -> Unit = {},
    sessionId: String = "",
    baseUrl: String = "",
    imageLoader: coil3.ImageLoader? = null
) {
    var showConfirmDialog by remember { mutableStateOf(false) }

    if (state.isSubmitted) {
        LaunchedEffect(Unit) {
            onFinish()
        }
    }

    LaunchedEffect(state.lostMessage) {
        state.lostMessage?.let(onSessionLost)
    }

    LaunchedEffect(state.submitError) {
        if (state.submitError != null && !state.isTimeOver) {
            showConfirmDialog = true
        }
    }

    if (state.isTimeOver && !state.isSubmitted) {
        AlertDialog(
            onDismissRequest = {},
            title = { Text("Waktu habis") },
            text = {
                Text(
                    state.submitError?.let { "Mengirim jawaban… $it Mencoba lagi." }
                        ?: "Mengirim jawaban…"
                )
            },
            confirmButton = {
                if (!state.isSubmitting && state.submitError != null) {
                    TextButton(onClick = onSubmit) { Text("Coba lagi") }
                }
            }
        )
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
                val answered = state.questions.count { q -> state.answers[q.id].isFilled() || !state.drafts[q.id].isNullOrBlank() }
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
            // At large font scales the fixed 54dp bar cannot hold logo + timer + action;
            // let it grow and drop the decorative badge instead of clipping the timer/action.
            val largeFont = LocalDensity.current.fontScale > 1.2f
            val compactTopBar = largeFont || LocalConfiguration.current.screenWidthDp < 360
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 54.dp),
                color = BrandSurface,
                border = androidx.compose.foundation.BorderStroke(1.dp, BorderSubtle)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 54.dp)
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
                        if (!compactTopBar) {
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
                            modifier = Modifier.heightIn(min = 32.dp),
                            contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 12.dp, vertical = 0.dp)
                        ) {
                            Text(
                                text = if (state.isSubmitting) "Mengirim…" else "Selesai",
                                fontFamily = DMSansFontFamily,
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 12.sp,
                                maxLines = 1,
                                softWrap = false
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
                    val currentQ = state.questions[state.currentIndex]
                    val isDoubt = state.doubtfuls.contains(currentQ.id)
                    val isLastQuestion = state.currentIndex >= state.questions.size - 1
                    val primaryLabel =
                        if (isLastQuestion) {
                            if (state.isSubmitting) "Mengirim…" else "Kumpulkan"
                        } else {
                            "Selanjutnya"
                        }
                    val primaryEnabled = if (isLastQuestion) state.canSubmit else !state.isSubmitting
                    val primaryColor = if (isLastQuestion) StatusSuccess else BrandNavy
                    val onPrimaryClick: () -> Unit =
                        if (isLastQuestion) {
                            { showConfirmDialog = true }
                        } else {
                            { onGoToIndex(state.currentIndex + 1) }
                        }

                    // At accessibility font scales three labels cannot share one 320dp
                    // row: "Selanjutnya" was cut mid-glyph because a single line with
                    // softWrap=false defaults to Clip. Two rows keep every label whole,
                    // which beats ellipsizing the primary verb ("Selanjut…") and beats
                    // shrinking the text back below the user's chosen size.
                    val useTwoRows = LocalDensity.current.fontScale > 1.2f

                    if (useTwoRows) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 8.dp, vertical = 10.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                ExamPreviousButton(
                                    enabled = state.currentIndex > 0 && !state.isSubmitting,
                                    onClick = { onGoToIndex(state.currentIndex - 1) },
                                    // Let the buttons grow with the user's font size
                                    // instead of pinning a height the text outgrows.
                                    modifier = Modifier
                                        .weight(1f)
                                        .heightIn(min = 38.dp)
                                )
                                ExamDoubtButton(
                                    isDoubt = isDoubt,
                                    enabled = state.canAnswer,
                                    onClick = { onToggleDoubtful(currentQ.id) },
                                    modifier = Modifier
                                        .weight(1f)
                                        .heightIn(min = 38.dp)
                                )
                            }
                            ExamPrimaryAction(
                                label = primaryLabel,
                                enabled = primaryEnabled,
                                containerColor = primaryColor,
                                onClick = onPrimaryClick,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .heightIn(min = 38.dp)
                            )
                        }
                    } else {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 8.dp, vertical = 10.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            ExamPreviousButton(
                                enabled = state.currentIndex > 0 && !state.isSubmitting,
                                onClick = { onGoToIndex(state.currentIndex - 1) },
                                modifier = Modifier
                                    .weight(1f)
                                    .height(38.dp)
                            )
                            ExamDoubtButton(
                                isDoubt = isDoubt,
                                enabled = state.canAnswer,
                                onClick = { onToggleDoubtful(currentQ.id) },
                                modifier = Modifier
                                    .weight(1f)
                                    .height(38.dp)
                            )
                            ExamPrimaryAction(
                                label = primaryLabel,
                                enabled = primaryEnabled,
                                containerColor = primaryColor,
                                onClick = onPrimaryClick,
                                modifier = Modifier
                                    .weight(1.2f)
                                    .height(38.dp)
                            )
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
                    onRetry = onRetry,
                    onSupervisorExit = onSupervisorExit
                )
            }

            state.questions.isEmpty() -> {
                ExamErrorContent(
                    message = ExamViewModel.EMPTY_PAPER_MESSAGE,
                    modifier = Modifier
                        .fillMaxSize()
                        .background(BrandBackground)
                        .padding(padding),
                    onSupervisorExit = onSupervisorExit,
                    onRetry = onRetry
                )
            }

            else -> {
                val currentQ = state.questions[state.currentIndex]

                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(BrandBackground)
                        .padding(padding)
                        .padding(horizontal = 16.dp, vertical = 12.dp)
                ) {
                    state.deliveryWarning?.let { warning ->
                        Text(
                            text = warning,
                            fontFamily = DMSansFontFamily,
                            fontSize = 12.sp,
                            color = StatusError,
                            modifier = Modifier.padding(bottom = 8.dp)
                        )
                    }
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
                            val isAnswered = state.answers[q.id].isFilled() || !state.drafts[q.id].isNullOrBlank()
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
                                        border = androidx.compose.foundation.BorderStroke(1.dp, BorderSubtle),
                                        modifier = Modifier.weight(1f, fill = false)
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
                                            text = questionTypeLabel(currentQ.type),
                                            fontFamily = DMSansFontFamily,
                                            fontWeight = FontWeight.SemiBold,
                                            fontSize = 11.sp,
                                            maxLines = 1,
                                            softWrap = false,
                                            overflow = TextOverflow.Ellipsis,
                                            color = StatusSuccess,
                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                                        )
                                    }
                                }
                            }

                            item {
                                HtmlContent(
                                    html = currentQ.bodyHtml,
                                    sessionId = sessionId,
                                    baseUrl = baseUrl,
                                    imageLoader = imageLoader,
                                    modifier = Modifier.padding(bottom = 20.dp)
                                )
                            }

                            item {
                                QuestionAnswerInput(
                                    question = currentQ,
                                    answer = state.answers[currentQ.id],
                                    draft = state.drafts[currentQ.id],
                                    enabled = state.canAnswer,
                                    images = ImageContext(sessionId, baseUrl, imageLoader),
                                    onAnswer = { onAnswer(currentQ.id, it) },
                                    onToggleKey = { onToggleKey(currentQ.id, it) },
                                    onTextChange = { onTextChange(currentQ.id, it) }
                                )
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
    onRetry: () -> Unit,
    onSupervisorExit: () -> Unit = {}
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
            Spacer(modifier = Modifier.height(14.dp))
            Text(
                text = "Keluar darurat (pengawas)",
                fontFamily = DMSansFontFamily,
                fontWeight = FontWeight.Medium,
                fontSize = 12.sp,
                color = TextMuted,
                modifier = Modifier
                    .clip(RoundedCornerShape(6.dp))
                    .clickable(onClick = onSupervisorExit)
                    .padding(horizontal = 10.dp, vertical = 6.dp)
            )
        }
    }
}

/**
 * Bottom-bar "previous question" action.
 *
 * The label is allowed to ellipsize: skipping back is recoverable and its meaning
 * survives an abbreviation, unlike the primary action. The primary never uses this.
 */
@Composable
private fun ExamPreviousButton(
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    OutlinedButton(
        onClick = onClick,
        enabled = enabled,
        shape = RoundedCornerShape(8.dp),
        contentPadding = PaddingValues(horizontal = 8.dp),
        modifier = modifier
    ) {
        Text(
            text = "Sebelumnya",
            fontFamily = DMSansFontFamily,
            fontWeight = FontWeight.Medium,
            fontSize = 13.sp,
            maxLines = 1,
            softWrap = false,
            overflow = TextOverflow.Ellipsis
        )
    }
}

/** Bottom-bar "mark doubtful" toggle; secondary, so it may ellipsize like [ExamPreviousButton]. */
@Composable
private fun ExamDoubtButton(
    isDoubt: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    OutlinedButton(
        onClick = onClick,
        enabled = enabled,
        shape = RoundedCornerShape(8.dp),
        border = androidx.compose.foundation.BorderStroke(
            1.dp,
            if (isDoubt) DoubtfulOrange else BorderSubtle
        ),
        colors = ButtonDefaults.outlinedButtonColors(
            containerColor = if (isDoubt) DoubtfulOrangeLight else Color.Transparent
        ),
        contentPadding = PaddingValues(horizontal = 8.dp),
        modifier = modifier
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
            color = if (isDoubt) DoubtfulOrange else TextSecondary,
            maxLines = 1,
            softWrap = false,
            overflow = TextOverflow.Ellipsis
        )
    }
}

/**
 * The bottom bar's primary action ("Selanjutnya" / "Kumpulkan").
 *
 * It wraps instead of clipping: `softWrap = true` with `maxLines = 2` and an explicit
 * [TextOverflow.Ellipsis] means a label that still does not fit is visibly truncated
 * rather than silently cut mid-glyph. The two-row layout gives it a full-width row at
 * large font scales, so in practice it renders whole.
 */
@Composable
private fun ExamPrimaryAction(
    label: String,
    enabled: Boolean,
    containerColor: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Button(
        onClick = onClick,
        enabled = enabled,
        shape = RoundedCornerShape(8.dp),
        colors = ButtonDefaults.buttonColors(containerColor = containerColor),
        contentPadding = PaddingValues(horizontal = 8.dp),
        modifier = modifier
    ) {
        Text(
            text = label,
            fontFamily = DMSansFontFamily,
            fontWeight = FontWeight.SemiBold,
            fontSize = 13.sp,
            maxLines = 2,
            softWrap = true,
            overflow = TextOverflow.Ellipsis
        )
    }
}
