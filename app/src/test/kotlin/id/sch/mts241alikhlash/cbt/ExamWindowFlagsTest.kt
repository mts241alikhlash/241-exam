package id.sch.mts241alikhlash.cbt

import android.view.WindowManager
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ExamWindowFlagsTest {

    @Test
    fun `exam window keeps the screen on`() {
        val activity = org.robolectric.Robolectric.buildActivity(MainActivity::class.java).setup().get()
        activity.setExamActiveForTest(true)
        val flags = activity.window.attributes.flags
        assertTrue(
            "FLAG_KEEP_SCREEN_ON must be set while the exam UI is active",
            flags and WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON != 0
        )
        activity.finish()
    }

    @Test
    fun `screen on flag is released when the exam ends`() {
        val activity = org.robolectric.Robolectric.buildActivity(MainActivity::class.java).setup().get()
        activity.setExamActiveForTest(false)
        val flags = activity.window.attributes.flags
        assertEquals(
            "KEEP_SCREEN_ON must be cleared after the exam ends",
            0,
            flags and WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON
        )
        activity.finish()
    }
}
