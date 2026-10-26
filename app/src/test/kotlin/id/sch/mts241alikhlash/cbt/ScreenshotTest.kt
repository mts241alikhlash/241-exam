package id.sch.mts241alikhlash.cbt

import app.cash.paparazzi.DeviceConfig
import app.cash.paparazzi.Paparazzi
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
}
