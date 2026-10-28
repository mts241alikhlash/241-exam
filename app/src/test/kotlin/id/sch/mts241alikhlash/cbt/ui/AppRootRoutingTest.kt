package id.sch.mts241alikhlash.cbt.ui

import app.cash.paparazzi.DeviceConfig
import app.cash.paparazzi.Paparazzi
import id.sch.mts241alikhlash.cbt.data.auth.AuthUser
import id.sch.mts241alikhlash.cbt.data.model.ExamSummaryDto
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class AppRootRoutingTest {

    @get:Rule
    val paparazzi = Paparazzi(
        deviceConfig = DeviceConfig.PIXEL_5,
        theme = "android:Theme.Material.NoActionBar",
    )

    private val user = AuthUser("u1", "Ahmad", setOf("STUDENT"))
    private val exam = ExamSummaryDto("e1", "UTS", "Matematika", "a", "b", 90, "OPEN")

    private fun screenFor(state: AppRootState): String {
        var shown = ""
        paparazzi.snapshot {
            AssessmentAppRoot(
                state = state,
                onRetryRestore = {},
                onStartExam = {},
                examContent = { _, _ -> shown = "exam" },
                loginContent = { shown = "login" },
                examListContent = { shown = "list" },
                tokenContent = { shown = "token:${it.id}" }
            )
        }
        return shown
    }

    @Test
    fun root_shows_login_when_no_user_and_no_session() {
        assertEquals("login", screenFor(AppRootState(restoreComplete = true)))
    }

    @Test
    fun root_shows_exam_list_when_user_without_selected_exam() {
        assertEquals("list", screenFor(AppRootState(restoreComplete = true, user = user)))
    }

    @Test
    fun root_shows_token_screen_when_exam_selected() {
        assertEquals(
            "token:e1",
            screenFor(AppRootState(restoreComplete = true, user = user, selectedExam = exam))
        )
    }

    @Test
    fun root_shows_exam_content_when_session_restored_without_user() {
        assertEquals(
            "exam",
            screenFor(AppRootState(restoreComplete = true, sessionId = "s1"))
        )
    }
}
