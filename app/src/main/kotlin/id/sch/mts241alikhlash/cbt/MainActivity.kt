package id.sch.mts241alikhlash.cbt

import android.os.Bundle
import android.view.WindowManager
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.lifecycleScope
import id.sch.mts241alikhlash.cbt.data.session.RestoreOutcome
import id.sch.mts241alikhlash.cbt.data.session.endExamSession
import id.sch.mts241alikhlash.cbt.data.session.restoreExamSession
import id.sch.mts241alikhlash.cbt.ui.screens.ExamScreen
import id.sch.mts241alikhlash.cbt.ui.screens.ExamViewModel
import id.sch.mts241alikhlash.cbt.ui.screens.TokenScreen
import id.sch.mts241alikhlash.cbt.ui.theme.BrandBackground
import id.sch.mts241alikhlash.cbt.ui.theme.BrandNavy
import id.sch.mts241alikhlash.cbt.ui.theme.DMSansFontFamily
import id.sch.mts241alikhlash.cbt.ui.theme.TextMuted
import id.sch.mts241alikhlash.cbt.ui.theme.TextPrimary
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    private var cheatWarningCount = 0
    private var isExamActive = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        window.setFlags(
            WindowManager.LayoutParams.FLAG_SECURE,
            WindowManager.LayoutParams.FLAG_SECURE
        )

        val app = application as CBTApplication

        setContent {
            var currentSessionId by remember { mutableStateOf<String?>(null) }
            var sessionDeadline by remember { mutableStateOf<Long?>(null) }
            var restoreComplete by remember { mutableStateOf(false) }
            var restoreError by remember { mutableStateOf<String?>(null) }
            var restoreAttempt by remember { mutableStateOf(0) }

            LaunchedEffect(restoreAttempt) {
                restoreError = null
                when (
                    val outcome = restoreExamSession(
                        sessionRepository = app.sessionRepository,
                        scope = lifecycleScope,
                        syncPending = app.repository::syncPending
                    ) { result ->
                        result.exceptionOrNull()?.let {
                            // Startup sync is best effort: report, never block the exam.
                            android.util.Log.w("MainActivity", "Startup answer sync failed", it)
                        }
                    }
                ) {
                    is RestoreOutcome.Restored -> {
                        isExamActive = true
                        startKioskMode()
                        currentSessionId = outcome.session.sessionId
                        sessionDeadline = outcome.session.deadline
                    }

                    is RestoreOutcome.Failed -> {
                        restoreError = outcome.message
                    }

                    RestoreOutcome.NoSession -> Unit
                }
                restoreComplete = true
            }

            when {
                !restoreComplete -> {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(BrandBackground),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator(color = BrandNavy)
                    }
                }

                restoreError != null -> {
                    RestoreErrorScreen(
                        message = restoreError!!,
                        onRetry = {
                            restoreComplete = false
                            restoreAttempt++
                        }
                    )
                }

                currentSessionId == null -> {
                    TokenScreen(
                        onStartExam = { sessionId ->
                            isExamActive = true
                            startKioskMode()
                            lifecycleScope.launch {
                                runCatching { app.sessionRepository.start(sessionId) }
                                    .onFailure {
                                        android.util.Log.e("MainActivity", "Failed to persist session", it)
                                        runOnUiThread {
                                            isExamActive = false
                                            stopKioskMode()
                                            Toast.makeText(
                                                this@MainActivity,
                                                "Gagal memulai ujian. Coba lagi.",
                                                Toast.LENGTH_LONG
                                            ).show()
                                        }
                                        currentSessionId = null
                                        return@launch
                                    }
                                val deadline = runCatching { app.sessionRepository.deadline() }.getOrNull()
                                currentSessionId = sessionId
                                sessionDeadline = deadline
                            }
                        }
                    )
                }

                else -> {
                    val sessionId = currentSessionId!!
                    val viewModel = remember(sessionId) {
                        ExamViewModel(
                            repository = app.repository,
                            sessionId = sessionId,
                            deadline = sessionDeadline
                        )
                    }

                    ExamScreen(
                        viewModel = viewModel,
                        onFinish = {
                            // Teardown must finish before success is announced: if the
                            // session survives, the next launch would resume a submitted
                            // paper.
                            lifecycleScope.launch {
                                val ended = runCatching {
                                    endExamSession(
                                        sessionRepository = app.sessionRepository,
                                        sessionId = sessionId,
                                        clearQuestions = app.repository::clearQuestions
                                    )
                                }.getOrElse { error ->
                                    android.util.Log.e("MainActivity", "Teardown failed", error)
                                    false
                                }

                                isExamActive = false
                                stopKioskMode()
                                currentSessionId = null
                                sessionDeadline = null

                                Toast.makeText(
                                    this@MainActivity,
                                    if (ended) "Ujian berhasil dikumpulkan"
                                    else "Ujian selesai, tetapi sesi gagal dibersihkan",
                                    Toast.LENGTH_LONG
                                ).show()
                            }
                        }
                    )
                }
            }
        }
    }

    override fun onPause() {
        super.onPause()
        if (isExamActive) {
            cheatWarningCount++
            Toast.makeText(
                this,
                "Peringatan: Dilarang keluar dari aplikasi ujian! ($cheatWarningCount)",
                Toast.LENGTH_SHORT
            ).show()
        }
    }

    private fun startKioskMode() {
        runCatching {
            startLockTask()
        }
    }

    private fun stopKioskMode() {
        runCatching {
            stopLockTask()
        }
    }
}

@androidx.compose.runtime.Composable
private fun RestoreErrorScreen(message: String, onRetry: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(BrandBackground),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = "Sesi ujian gagal dipulihkan",
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
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(18.dp))
            Button(
                onClick = onRetry,
                shape = RoundedCornerShape(8.dp),
                colors = ButtonDefaults.buttonColors(containerColor = BrandNavy)
            ) {
                Text(
                    text = "Coba Lagi",
                    fontFamily = DMSansFontFamily,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 13.sp,
                    color = androidx.compose.ui.graphics.Color.White
                )
            }
        }
    }
}
