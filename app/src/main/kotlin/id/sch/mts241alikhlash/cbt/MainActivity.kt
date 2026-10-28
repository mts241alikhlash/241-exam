package id.sch.mts241alikhlash.cbt

import android.app.ActivityManager
import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.os.Bundle
import android.view.WindowManager
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.*
import androidx.lifecycle.lifecycleScope
import id.sch.mts241alikhlash.cbt.data.session.RestoreOutcome
import id.sch.mts241alikhlash.cbt.kiosk.ExamDeviceAdminReceiver
import id.sch.mts241alikhlash.cbt.kiosk.KioskPolicy
import id.sch.mts241alikhlash.cbt.ui.KioskWarningOverlay
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

    /** True when the OS confirms the task is actually pinned; gates the exam UI. */
    private val isPinned = mutableStateOf(false)

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
                    if (isExamActive && !isPinned.value) {
                        KioskWarningOverlay(
                            onRetry = {
                                runCatching { startLockTask() }
                                verifyPinState()
                            }
                        )
                        return@AssessmentAppRoot
                    }
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

    /** Test seam: toggles exam-active state without touching the UI tree. */
    internal fun setExamActiveForTest(active: Boolean) {
        isExamActive = active
        applyExamWindowFlags()
    }

    private fun applyExamWindowFlags() {
        if (isExamActive) {
            window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        } else {
            window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
    }

    private fun startKioskMode() {
        applyExamWindowFlags()
        isPinned.value = false
        runCatching {
            // With Device Owner the system pins silently and blocks the escape
            // gesture; without it Android always shows a dismissible dialog.
            startLockTask()
        }
    }

    private fun isDeviceOwner(): Boolean {
        val dpm = getSystemService(DevicePolicyManager::class.java) ?: return false
        return dpm.isDeviceOwnerApp(packageName)
    }

    /** Hides the system UI chrome that would otherwise let a student escape. */
    private fun applyDeviceOwnerPolicies() {
        if (!isDeviceOwner()) return
        runCatching {
            val dpm = getSystemService(DevicePolicyManager::class.java)
            val admin = ComponentName(this, ExamDeviceAdminReceiver::class.java)
            dpm.setLockTaskPackages(admin, arrayOf(packageName))
            dpm.setStatusBarDisabled(admin, true)
            dpm.setKeyguardDisabled(admin, true)
        }
    }

    private fun clearDeviceOwnerPolicies() {
        if (!isDeviceOwner()) return
        runCatching {
            val dpm = getSystemService(DevicePolicyManager::class.java)
            val admin = ComponentName(this, ExamDeviceAdminReceiver::class.java)
            dpm.setStatusBarDisabled(admin, false)
            dpm.setKeyguardDisabled(admin, false)
        }
    }

    private fun stopKioskMode() {
        isExamActive = false
        isPinned.value = false
        clearDeviceOwnerPolicies()
        applyExamWindowFlags()
        runCatching {
            stopLockTask()
        }
    }

    override fun onResume() {
        super.onResume()
        if (isExamActive) {
            applyDeviceOwnerPolicies()
            applyExamWindowFlags()
            // Lock-task pinning is dropped when the screen turns off or the task is
            // backgrounded; re-arm it so returning to the app restores the kiosk.
            runCatching { startLockTask() }
            verifyPinState()
        }
    }

    /**
     * The system may silently refuse pinning (user tapped "No thanks", or an OEM
     * dropped the lock). Never assume it worked: ask, then gate the exam.
     */
    private fun verifyPinState() {
        val am = getSystemService(ActivityManager::class.java)
        val state = am?.lockTaskModeState ?: ActivityManager.LOCK_TASK_MODE_NONE
        isPinned.value = KioskPolicy.isSecure(state)
        if (!isPinned.value) {
            android.util.Log.w("MainActivity", "Exam running without pin; state=$state")
        }
    }
}
