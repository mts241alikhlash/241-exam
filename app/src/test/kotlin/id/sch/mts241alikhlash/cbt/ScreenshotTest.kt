package id.sch.mts241alikhlash.cbt

import app.cash.paparazzi.DeviceConfig
import app.cash.paparazzi.Paparazzi
import id.sch.mts241alikhlash.cbt.data.model.StudentQuestionDto
import id.sch.mts241alikhlash.cbt.ui.screens.ExamContent
import id.sch.mts241alikhlash.cbt.ui.screens.ExamUiState
import id.sch.mts241alikhlash.cbt.ui.screens.TokenScreen
import id.sch.mts241alikhlash.cbt.ui.theme.CBTTheme
import org.junit.Rule
import org.junit.Test

class ScreenshotTest {
    @get:Rule
    val paparazzi = Paparazzi(
        deviceConfig = DeviceConfig.PIXEL_5,
        theme = "android:Theme.Material.NoActionBar",
    )

    @Test
    fun tokenScreen() {
        paparazzi.snapshot {
            CBTTheme { TokenScreen(onStartExam = {}) }
        }
    }

    @Test
    fun examScreen() {
        val questions = listOf(
            StudentQuestionDto(
                id = "q1", number = 1, type = "multiple_choice",
                bodyHtml = "Hasil dari 12 x 8 adalah ...",
                choices = listOf("86", "96", "104", "112"),
            ),
            StudentQuestionDto(
                id = "q2", number = 2, type = "multiple_choice",
                bodyHtml = "Ibu kota Provinsi Jawa Timur adalah ...",
                choices = listOf("Malang", "Surabaya", "Kediri", "Madiun"),
            ),
            StudentQuestionDto(
                id = "q3", number = 3, type = "multiple_choice",
                bodyHtml = "Kalimat yang menggunakan ejaan baku adalah ...",
                choices = listOf("A", "B", "C", "D"),
            ),
        )
        val state = ExamUiState(
            isLoading = false,
            questions = questions,
            currentIndex = 0,
            answers = mapOf("q1" to "96"),
            doubtfuls = setOf("q2"),
            remainingSeconds = 5340,
        )
        paparazzi.snapshot {
            CBTTheme {
                ExamContent(
                    state = state,
                    onSelectAnswer = { _, _ -> },
                    onToggleDoubtful = {},
                    onGoToIndex = {},
                    onSubmit = {},
                    onFinish = {},
                )
            }
        }
    }
}
