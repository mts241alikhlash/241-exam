package id.sch.mts241alikhlash.cbt.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import id.sch.mts241alikhlash.cbt.ui.theme.DoubtfulOrange
import id.sch.mts241alikhlash.cbt.ui.theme.GreenPrimary

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExamScreen(
    viewModel: ExamViewModel,
    onFinish: () -> Unit
) {
    val state by viewModel.uiState.collectAsState()
    var showConfirmDialog by remember { mutableStateOf(false) }

    if (state.isSubmitted) {
        LaunchedEffect(Unit) {
            onFinish()
        }
    }

    if (showConfirmDialog) {
        AlertDialog(
            onDismissRequest = { showConfirmDialog = false },
            title = { Text("Kumpulkan Ujian?") },
            text = {
                val answered = state.answers.size
                val total = state.questions.size
                Text("Anda telah menjawab $answered dari $total soal. Yakin ingin mengakhiri ujian?")
            },
            confirmButton = {
                Button(onClick = {
                    showConfirmDialog = false
                    viewModel.submitExam()
                }) {
                    Text("Ya, Kumpulkan")
                }
            },
            dismissButton = {
                TextButton(onClick = { showConfirmDialog = false }) {
                    Text("Batal")
                }
            }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    val minutes = state.remainingSeconds / 60
                    val seconds = state.remainingSeconds % 60
                    Text(
                        text = "Sisa Waktu: %02d:%02d".format(minutes, seconds),
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold
                    )
                },
                actions = {
                    Button(
                        onClick = { showConfirmDialog = true },
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                    ) {
                        Text("Selesai")
                    }
                }
            )
        }
    ) { padding ->
        if (state.isLoading) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator()
            }
        } else if (state.questions.isNotEmpty()) {
            val currentQ = state.questions[state.currentIndex]
            val selectedChoice = state.answers[currentQ.id]
            val isDoubt = state.doubtfuls.contains(currentQ.id)

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(16.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Soal Nomor ${state.currentIndex + 1} / ${state.questions.size}",
                        fontWeight = FontWeight.Bold,
                        fontSize = 18.sp
                    )

                    FilterChip(
                        selected = isDoubt,
                        onClick = { viewModel.toggleDoubtful(currentQ.id) },
                        label = { Text("Ragu-ragu") },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = DoubtfulOrange,
                            selectedLabelColor = Color.White
                        )
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                ) {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(16.dp)
                    ) {
                        item {
                            Text(
                                text = currentQ.bodyHtml.replace(Regex("<[^>]*>"), ""),
                                fontSize = 16.sp,
                                modifier = Modifier.padding(bottom = 16.dp)
                            )
                        }

                        if (currentQ.choices != null) {
                            itemsIndexed(currentQ.choices) { index, choice ->
                                val letter = ('A' + index).toString()
                                val isSelected = selectedChoice == choice

                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 6.dp)
                                        .border(
                                            width = if (isSelected) 2.dp else 1.dp,
                                            color = if (isSelected) GreenPrimary else Color.LightGray,
                                            shape = RoundedCornerShape(8.dp)
                                        )
                                        .background(
                                            color = if (isSelected) GreenPrimary.copy(alpha = 0.1f) else Color.Transparent,
                                            shape = RoundedCornerShape(8.dp)
                                        )
                                        .clickable { viewModel.selectAnswer(currentQ.id, choice) }
                                        .padding(12.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    RadioButton(
                                        selected = isSelected,
                                        onClick = { viewModel.selectAnswer(currentQ.id, choice) }
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(text = "$letter. $choice", fontSize = 15.sp)
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Button(
                        onClick = { viewModel.goToIndex(state.currentIndex - 1) },
                        enabled = state.currentIndex > 0
                    ) {
                        Text("Sebelumnya")
                    }

                    Button(
                        onClick = { viewModel.goToIndex(state.currentIndex + 1) },
                        enabled = state.currentIndex < state.questions.size - 1
                    ) {
                        Text("Selanjutnya")
                    }
                }
            }
        }
    }
}
