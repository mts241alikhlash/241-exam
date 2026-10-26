package id.sch.mts241alikhlash.cbt

import android.os.Bundle
import android.view.WindowManager
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.*
import id.sch.mts241alikhlash.cbt.ui.screens.ExamScreen
import id.sch.mts241alikhlash.cbt.ui.screens.ExamViewModel
import id.sch.mts241alikhlash.cbt.ui.screens.TokenScreen
import id.sch.mts241alikhlash.cbt.ui.theme.AssessmentTheme

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
            AssessmentTheme {
                var currentSessionId by remember { mutableStateOf<String?>(null) }

                if (currentSessionId == null) {
                    TokenScreen(
                        onStartExam = { sessionId ->
                            isExamActive = true
                            startKioskMode()
                            currentSessionId = sessionId
                        }
                    )
                } else {
                    val viewModel = remember(currentSessionId) {
                        ExamViewModel(app.repository, currentSessionId!!)
                    }

                    ExamScreen(
                        viewModel = viewModel,
                        onFinish = {
                            isExamActive = false
                            stopKioskMode()
                            currentSessionId = null
                            Toast.makeText(this@MainActivity, "Ujian berhasil dikumpulkan", Toast.LENGTH_LONG).show()
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
