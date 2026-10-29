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
import id.sch.mts241alikhlash.cbt.ui.ReloginDialog
import androidx.lifecycle.lifecycleScope
import id.sch.mts241alikhlash.cbt.data.auth.SupervisorResult
import id.sch.mts241alikhlash.cbt.data.remote.userMessage
import id.sch.mts241alikhlash.cbt.data.session.RestoreOutcome
import id.sch.mts241alikhlash.cbt.data.session.startExamSession
import id.sch.mts241alikhlash.cbt.ui.screens.HomeScreen
import id.sch.mts241alikhlash.cbt.ui.screens.ProfileViewModel
import id.sch.mts241alikhlash.cbt.ui.screens.ExamListViewModel
import id.sch.mts241alikhlash.cbt.ui.screens.LoginScreen
import id.sch.mts241alikhlash.cbt.ui.screens.LoginViewModel
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import id.sch.mts241alikhlash.cbt.kiosk.ExamDeviceAdminReceiver
import id.sch.mts241alikhlash.cbt.kiosk.KioskPolicy
import id.sch.mts241alikhlash.cbt.kiosk.ViolationStore
import id.sch.mts241alikhlash.cbt.ui.KioskWarningOverlay
import id.sch.mts241alikhlash.cbt.ui.SupervisorExitDialog
import id.sch.mts241alikhlash.cbt.data.session.endExamSession
import id.sch.mts241alikhlash.cbt.data.session.restoreExamSession
import id.sch.mts241alikhlash.cbt.ui.AppRootState
import id.sch.mts241alikhlash.cbt.ui.AssessmentAppRoot
import id.sch.mts241alikhlash.cbt.ui.screens.ExamScreen
import id.sch.mts241alikhlash.cbt.ui.screens.ExamViewModel
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    private lateinit var violations: ViolationStore
    private var isExamActive = false
        set(value) {
            field = value
            (application as? CBTApplication)?.examActive = value
        }
    private var showSupervisorExit by mutableStateOf(false)
    private var resumeCount by mutableIntStateOf(0)
    private var sessionRestored: Boolean? = null

    /** True when the OS confirms the task is actually pinned; gates the exam UI. */
    private val isPinned = mutableStateOf(false)

    private val mainHandler = android.os.Handler(android.os.Looper.getMainLooper())
    private val pinRecheck = Runnable { verifyPinState() }

    /**
     * The OS offers no public callback when lock-task mode ends (the listener is
     * @SystemApi), and a student can drop the pin while the app stays foreground.
     * Sample while an exam runs so the gate closes immediately.
     */
    private val pinWatch = object : Runnable {
        override fun run() {
            if (!isExamActive) return
            verifyPinState()
            mainHandler.postDelayed(this, 1000)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (!BuildConfig.DEBUG) {
            window.setFlags(
                WindowManager.LayoutParams.FLAG_SECURE,
                WindowManager.LayoutParams.FLAG_SECURE
            )
        }

        val app = application as CBTApplication
        violations = ViolationStore(this)

        setContent {
            var appState by remember { mutableStateOf(AppRootState()) }
            var restoreAttempt by remember { mutableStateOf(0) }

            LaunchedEffect(restoreAttempt) {
                appState = appState.copy(restoreError = null)
                when (
                    val outcome = restoreExamSession(
                        sessionRepository = app.sessionRepository,
                        scope = lifecycleScope,
                        syncPending = app.repository::syncPending,
                        validate = { id ->
                            app.examActive = true
                            app.repository.pullServerAnswers(id)
                        }
                    ) { result ->
                        result.exceptionOrNull()?.let {
                            // Startup sync is best effort: report, never block the exam.
                            android.util.Log.w("MainActivity", "Startup answer sync failed", it)
                        }
                    }
                ) {
                    is RestoreOutcome.Restored -> {
                        isExamActive = true
                        sessionRestored = true
                        startKioskMode()
                        appState = AppRootState(
                            restoreComplete = true,
                            user = runCatching { app.authRepository.currentStudent() }.getOrNull(),
                            sessionId = outcome.session.sessionId,
                            deadline = outcome.session.deadline
                        )
                    }

                    is RestoreOutcome.SessionLost -> {
                        app.examActive = false
                        val storedUser = runCatching { app.authRepository.currentStudent() }.getOrNull()
                        appState = AppRootState(
                            restoreComplete = true,
                            user = storedUser,
                            notice = outcome.message
                        )
                    }

                    is RestoreOutcome.Failed -> {
                        appState = AppRootState(
                            restoreComplete = true,
                            restoreError = outcome.message
                        )
                    }

                    RestoreOutcome.NoSession -> {
                        val storedUser = runCatching { app.authRepository.currentStudent() }.getOrNull()
                        appState = AppRootState(restoreComplete = true, user = storedUser)
                    }
                }
            }

            LaunchedEffect(appState.sessionId) {
                val running = appState.sessionId
                if (running != null && appState.activeExam == null) {
                    val exam = runCatching { app.catalogRepository.listExams() }.getOrNull()
                        ?.firstOrNull { it.sessionId == running }
                    if (exam != null && appState.sessionId == running) {
                        appState = appState.copy(activeExam = exam)
                    }
                }
            }

            AssessmentAppRoot(
                state = appState,
                onRetryRestore = {
                    appState = appState.copy(restoreComplete = false)
                    restoreAttempt++
                },
                onBackToList = { appState = appState.copy(selectedExam = null, startError = null) },
                onStartExam = { accessCode ->
                    val exam = appState.selectedExam
                    if (exam != null && !appState.starting) {
                        appState = appState.copy(starting = true, startError = null)
                        lifecycleScope.launch {
                            val started = runCatching {
                                startExamSession(
                                    app.catalogRepository,
                                    app.sessionRepository,
                                    exam.id,
                                    accessCode,
                                    pullAnswers = { id ->
                                        app.examActive = true
                                        app.repository.pullServerAnswers(id)
                                    }
                                )
                            }
                            val failure = started.exceptionOrNull()
                            if (failure != null) {
                                appState = appState.copy(
                                    starting = false,
                                    startError = failure.userMessage()
                                )
                                return@launch
                            }
                            isExamActive = true
                            sessionRestored = false
                            startKioskMode()
                            val deadline = runCatching { app.sessionRepository.deadline() }.getOrNull()
                            appState = appState.copy(
                                starting = false,
                                selectedExam = null,
                                activeExam = exam,
                                sessionId = started.getOrThrow().sessionId,
                                deadline = deadline
                            )
                        }
                    }
                },
                loginContent = {
                    val loginViewModel = viewModel<LoginViewModel>(
                        factory = viewModelFactory {
                            initializer { LoginViewModel(app.authRepository) }
                        }
                    )
                    val loggedInUser by loginViewModel.loggedIn.collectAsState()
                    LaunchedEffect(loggedInUser) {
                        loggedInUser?.let {
                            appState = appState.copy(user = it)
                            loginViewModel.consumed()
                        }
                    }
                    LoginScreen(loginViewModel)
                },
                examListContent = {
                    val listViewModel = viewModel<ExamListViewModel>(
                        key = "exam-list-${appState.user?.id}",
                        factory = viewModelFactory {
                            initializer { ExamListViewModel(app.catalogRepository) }
                        }
                    )
                    val profileViewModel = viewModel<ProfileViewModel>(
                        key = "profile-${appState.user?.id}",
                        factory = viewModelFactory {
                            initializer {
                                ProfileViewModel(app.profileRepository, appState.user?.name.orEmpty())
                            }
                        }
                    )
                    HomeScreen(
                        userName = appState.user?.name.orEmpty(),
                        listViewModel = listViewModel,
                        profileViewModel = profileViewModel,
                        notice = appState.notice,
                        onSelect = { appState = appState.copy(selectedExam = it, startError = null, notice = null) },
                        onLogout = {
                            lifecycleScope.launch {
                                app.authRepository.logout()
                                appState = appState.copy(user = null, selectedExam = null)
                            }
                        }
                    )
                },
                examContent = { sessionId, deadline ->
                    if (isExamActive && !isPinned.value) {
                        KioskWarningOverlay(
                            onRetry = {
                                runCatching { startLockTask() }
                                verifyPinState()
                            },
                            onSupervisorAccess = { showSupervisorExit = true }
                        )
                        return@AssessmentAppRoot
                    }
                    val viewModel = viewModel<ExamViewModel>(
                        key = "exam-$sessionId",
                        factory = viewModelFactory {
                            initializer {
                                ExamViewModel(
                                    repository = app.repository,
                                    sessionId = sessionId,
                                    deadline = deadline,
                                    restored = sessionRestored,
                                    onSubmitConfirmed = { id -> app.sessionRepository.finishIfCurrent(id) }
                                )
                            }
                        }
                    )

                    ExamScreen(
                        viewModel = viewModel,
                        sessionId = sessionId,
                        exam = appState.activeExam,
                        baseUrl = BuildConfig.BASE_URL,
                        imageLoader = app.imageLoader,
                        onSupervisorExit = { showSupervisorExit = true },
                        onSessionLost = { message ->
                            lifecycleScope.launch {
                                runCatching { app.sessionRepository.finishIfCurrent(sessionId) }
                                isExamActive = false
                                stopKioskMode()
                                appState = appState.copy(
                                    sessionId = null,
                                    deadline = null,
                                    selectedExam = null,
                                    notice = message
                                )
                            }
                        },
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
                                appState = appState.copy(sessionId = null, deadline = null, selectedExam = null)

                                val unsent = viewModel.uiState.value.unsentAnswers
                                Toast.makeText(
                                    this@MainActivity,
                                    when {
                                        unsent > 0 -> "Ujian dikumpulkan, tetapi $unsent jawaban tidak sempat terkirim"
                                        ended -> "Ujian berhasil dikumpulkan"
                                        else -> "Ujian selesai, tetapi sesi gagal dibersihkan"
                                    },
                                    Toast.LENGTH_LONG
                                ).show()
                            }
                        }
                    )
                }
            )

            val tokensRejected by app.authRepository.rejected.collectAsState()
            var reloginDismissed by remember { mutableStateOf(false) }
            LaunchedEffect(tokensRejected, resumeCount) {
                if (tokensRejected) reloginDismissed = false
            }
            val activeSessionId = appState.sessionId
            if (tokensRejected && activeSessionId != null && !reloginDismissed) {
                ReloginDialog(
                    onSubmit = { identifier, password ->
                        val failure = runCatching { app.authRepository.relogin(identifier, password) }
                            .exceptionOrNull()
                        if (failure == null) {
                            lifecycleScope.launch {
                                runCatching { app.repository.syncPending(activeSessionId) }
                            }
                        }
                        failure?.userMessage()
                    },
                    onDismiss = { reloginDismissed = true }
                )
            }

            if (showSupervisorExit) {
                SupervisorExitDialog(
                    onVerify = { identifier, password ->
                        app.supervisorVerifier.verify(identifier, password).also {
                            if (it != SupervisorResult.Approved) violations.record("supervisor_exit_denied")
                        }
                    },
                    onDismiss = { showSupervisorExit = false },
                    onConfirm = {
                        showSupervisorExit = false
                        emergencyExit()
                    }
                )
            }
        }
    }

    /**
     * Supervisor escape hatch: drop the kiosk and leave the exam shell so a broken
     * device can be recovered. Answers already saved stay on disk.
     */
    private fun emergencyExit() {
        isExamActive = false
        mainHandler.removeCallbacks(pinWatch)
        mainHandler.removeCallbacks(pinRecheck)
        runCatching { stopLockTask() }
        clearDeviceOwnerPolicies()
        applyExamWindowFlags()
        violations.record("supervisor_exit")
        finishAndRemoveTask()
    }

    override fun onPause() {
        super.onPause()
        // Turning the screen off is not leaving the app; only count real exits.
        val power = getSystemService(android.os.PowerManager::class.java)
        val interactive = power?.isInteractive ?: true
        if (isExamActive && interactive) {
            val total = violations.record("left_app")
            Toast.makeText(
                this,
                "Peringatan: Dilarang keluar dari aplikasi ujian! ($total)",
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
        mainHandler.removeCallbacks(pinWatch)
        mainHandler.post(pinWatch)
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
        mainHandler.removeCallbacks(pinRecheck)
        mainHandler.removeCallbacks(pinWatch)
        clearDeviceOwnerPolicies()
        applyExamWindowFlags()
        runCatching {
            stopLockTask()
        }
    }

    override fun onResume() {
        super.onResume()
        resumeCount++
        if (isExamActive) {
            applyDeviceOwnerPolicies()
            applyExamWindowFlags()
            // Lock-task pinning is dropped when the screen turns off or the task is
            // backgrounded; re-arm it so returning to the app restores the kiosk.
            runCatching { startLockTask() }
            // The pinning dialog answers asynchronously; sample now and again shortly.
            verifyPinState()
            mainHandler.removeCallbacks(pinRecheck)
            mainHandler.postDelayed(pinRecheck, 700)
        }
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        // The pinning dialog steals focus; when it closes (accepted or rejected)
        // the real state is finally observable.
        if (hasFocus && isExamActive) verifyPinState()
    }

    /**
     * The system may silently refuse pinning (user tapped "No thanks", or an OEM
     * dropped the lock). Never assume it worked: ask, then gate the exam.
     */
    private fun verifyPinState() {
        val am = getSystemService(ActivityManager::class.java)
        val state = am?.lockTaskModeState ?: ActivityManager.LOCK_TASK_MODE_NONE
        val secure = KioskPolicy.isSecure(state)
        if (secure != isPinned.value) {
            android.util.Log.i("MainActivity", "Pin state changed: secure=$secure (state=$state)")
            // Losing the pin during an exam is an integrity event: the student either
            // rejected the dialog or used the system escape gesture.
            if (!secure && isExamActive) violations.record("pin_lost")
        }
        isPinned.value = secure
    }
}
