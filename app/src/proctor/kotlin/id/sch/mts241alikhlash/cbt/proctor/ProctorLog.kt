package id.sch.mts241alikhlash.cbt.proctor

import java.io.File

/**
 * Xiaomi drops most logcat output, so the trial keeps its own trail. The file
 * lives in the app's private storage; `run-as <pkg> cat files/proctor.log` reads it.
 */
internal var proctorLogFile: File? = null

internal fun proctorLog(line: String) {
    runCatching {
        proctorLogFile?.appendText("${System.currentTimeMillis()} $line\n")
    }
}
