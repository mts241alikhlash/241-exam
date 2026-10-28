package id.sch.mts241alikhlash.cbt

import androidx.compose.runtime.Composable
import app.cash.paparazzi.DeviceConfig
import app.cash.paparazzi.Paparazzi
import id.sch.mts241alikhlash.cbt.data.auth.AuthUser
import id.sch.mts241alikhlash.cbt.data.model.ExamSummaryDto
import id.sch.mts241alikhlash.cbt.data.model.StudentQuestionDto
import id.sch.mts241alikhlash.cbt.ui.AppRootState
import id.sch.mts241alikhlash.cbt.ui.AssessmentAppRoot
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

    /**
     * R1: composes the *real* application root, which must supply its own theme.
     *
     * This snapshot previously could not catch a missing theme because it wrapped
     * `TokenScreen` in `CBTTheme` itself. Going through [AssessmentAppRoot] means a root
     * that forgets `AssessmentTheme` renders here with the wrong palette and fails review.
     */
    @Test
    fun appRootTokenScreen() {
        paparazzi.snapshot {
            AssessmentAppRoot(
                state = AppRootState(
                    restoreComplete = true,
                    user = AuthUser("u1", "Ahmad", setOf("STUDENT")),
                    selectedExam = ExamSummaryDto(
                        "e1", "UTS Matematika", "Matematika", "2026-11-01T01:00:00Z",
                        "2026-11-01T03:00:00Z", 90, "OPEN"
                    )
                ),
                onRetryRestore = {},
                onStartExam = {},
                examContent = { _, _ -> }
            )
        }
    }

    @Test
    fun appRootRestoreError() {
        paparazzi.snapshot {
            AssessmentAppRoot(
                state = AppRootState(
                    restoreComplete = true,
                    restoreError = "Gagal memulihkan sesi ujian. Coba lagi."
                ),
                onRetryRestore = {},
                onStartExam = {},
                examContent = { _, _ -> }
            )
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
                    onRetry = {},
                    onFinish = {},
                )
            }
        }
    }

    /**
     * R5: the error panel must always offer a way out.
     *
     * "Coba Lagi" alone traps the student when the network stays down, so the
     * supervisor exit link is part of the layout contract.
     */
    @Test
    fun examErrorScreenHasSupervisorExit() {
        paparazzi.snapshot {
            CBTTheme {
                ExamContent(
                    state = ExamUiState(
                        isLoading = false,
                        errorMessage = "Tidak dapat memuat soal. Periksa koneksi.",
                    ),
                    onSelectAnswer = { _, _ -> },
                    onToggleDoubtful = {},
                    onGoToIndex = {},
                    onSubmit = {},
                    onRetry = {},
                    onFinish = {},
                    onSupervisorExit = {},
                )
            }
        }
    }

    /**
     * R3: the bottom bar must fit a 320dp-wide screen.
     *
     * "Selanjutnya" is the longest label and used to be clipped at this width, so this
     * snapshot pins the whole bar at the narrowest common Android width.
     */
    @Test
    fun examScreenNarrow() {
        paparazzi.unsafeUpdateConfig(deviceConfig = NarrowDeviceConfig)
        paparazzi.snapshot { PreviewExamScreen() }
    }

    /**
     * R4: the primary action must survive the narrowest width *and* a large font.
     *
     * At fontScale 1.5 the three-button row cannot fit "Selanjutnya" at 13sp, and the
     * old single-row layout clipped it mid-glyph ("Selanjutn") because `maxLines = 1`
     * with `softWrap = false` defaults to [TextOverflow.Clip]. The bar now switches to
     * two rows above fontScale 1.2, so this snapshot pins the primary label intact.
     */
    @Test
    fun examScreenNarrowLargeFont() {
        paparazzi.unsafeUpdateConfig(deviceConfig = NarrowLargeFontDeviceConfig)
        paparazzi.snapshot { PreviewExamScreen() }
    }

    /** R2: a genuinely resumed paper renders the notice banner. */
    @Test
    fun examScreenResumed() {
        val state = ExamUiState(
            isLoading = false,
            questions = listOf(
                StudentQuestionDto(
                    id = "q1", number = 1, type = "multiple_choice",
                    bodyHtml = "Hasil dari 12 x 8 adalah ...",
                    choices = listOf("86", "96", "104", "112"),
                )
            ),
            remainingSeconds = 2700,
            isResumed = true,
        )
        paparazzi.snapshot {
            CBTTheme {
                ExamContent(
                    state = state,
                    onSelectAnswer = { _, _ -> },
                    onToggleDoubtful = {},
                    onGoToIndex = {},
                    onSubmit = {},
                    onRetry = {},
                    onFinish = {},
                )
            }
        }
    }
}

/**
 * A 320dp-wide phone: the narrowest common Android width.
 *
 * [DeviceConfig.PIXEL_5] is 1080x2340 px at 440dpi (≈393dp wide). Overriding the
 * pixel size to 320x640 while pinning the density to mdpi gives an exact 320x640dp
 * canvas, so the layout under test is a true 320dp-wide screen.
 */
private val NarrowDeviceConfig = DeviceConfig.PIXEL_5.copy(
    screenWidth = 320,
    screenHeight = 640,
    xdpi = 160,
    ydpi = 160,
    density = com.android.resources.Density.MEDIUM,
)

/**
 * [NarrowDeviceConfig] at the accessibility font scale the defect was reported at.
 *
 * 1.5x is the largest "Large" setting in Android's display-size settings; anything
 * below it that fits will fit at 1.5x only if the layout adapts, which it now does.
 */
private val NarrowLargeFontDeviceConfig = NarrowDeviceConfig.copy(fontScale = 1.5f)

/** Renders the exam screen with the same fixture used by [ScreenshotTest.examScreen]. */
@Composable
private fun PreviewExamScreen() {
    val state = ExamUiState(
        isLoading = false,
        questions = listOf(
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
        ),
        currentIndex = 0,
        answers = mapOf("q1" to "96"),
        doubtfuls = setOf("q2"),
        remainingSeconds = 5340,
    )
    CBTTheme {
        ExamContent(
            state = state,
            onSelectAnswer = { _, _ -> },
            onToggleDoubtful = {},
            onGoToIndex = {},
            onSubmit = {},
            onRetry = {},
            onFinish = {},
        )
    }
}
