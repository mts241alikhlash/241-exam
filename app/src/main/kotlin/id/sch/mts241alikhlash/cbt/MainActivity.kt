package id.sch.mts241alikhlash.cbt

import android.os.Bundle
import android.view.WindowManager
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.*
import androidx.lifecycle.lifecycleScope
import id.sch.mts241alikhlash.cbt.data.session.RestoreOutcome
import id.sch.mts241alikhlash.cbt.data.session.endExamSession
import id.sch.mts241alikhlash.cbt.data.session.restoreExamSession
import id.sch.mts241alikhlash.cbt.ui.AppRootState
import id.sch.mts241alikhlash.cbt.ui.AssessmentAppRoot
import id.sch.mts241alikhlash.cbt.ui.screens.ExamScreen
import id.sch.mts241alikhlash.cbt.ui.screens.ExamViewModel
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
            var appState by remember { mutableStateOf(AppRootState()) }
            var restoreAttempt by remember { mutableStateOf(0) }

            LaunchedEffect(restoreAttempt) {
                appState = appState.copy(restoreError = null)
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
                        appState = AppRootState(
                            restoreComplete = true,
                            sessionId = outcome.session.sessionId,
                            deadline = outcome.session.deadline
                        )
                    }

                    is RestoreOutcome.Failed -> {
                        appState = AppRootState(
                            restoreComplete = true,
                            restoreError = outcome.message
                        )
                    }

                    RestoreOutcome.NoSession -> {
                        appState = AppRootState(restoreComplete = true)
                    }
                }
            }

            AssessmentAppRoot(
                state = appState,
                onRetryRestore = {
                    appState = appState.copy(restoreComplete = false)
                    restoreAttempt++
                },
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
                                appState = appState.copy(sessionId = null, deadline = null)
                                return@launch
                            }
                        val deadline = runCatching { app.sessionRepository.deadline() }.getOrNull()
                        appState = appState.copy(sessionId = sessionId, deadline = deadline)
                    }
                },
                examContent = { sessionId, deadline ->
                    val viewModel = remember(sessionId) {
                        ExamViewModel(
                            repository = app.repository,
                            sessionId = sessionId,
                            deadline = deadline
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
                                appState = appState.copy(sessionId = null, deadline = null)

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
            )
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
