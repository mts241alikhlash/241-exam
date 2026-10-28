package id.sch.mts241alikhlash.cbt.kiosk

import android.content.Context

/**
 * Local record of exam-integrity events. Kept in SharedPreferences because the
 * writes are tiny, synchronous, and must survive a process death caused by the
 * student force-stopping the app.
 */
class ViolationStore(context: Context) {

    private val prefs = context.applicationContext
        .getSharedPreferences("exam_violations", Context.MODE_PRIVATE)

    fun count(): Int = prefs.getInt(KEY_COUNT, 0)

    /** Appends one event and returns the new total. */
    fun record(reason: String): Int {
        val total = count() + 1
        val entry = "${System.currentTimeMillis()}|$reason"
        val log = (listOf(entry) + entries()).take(MAX_ENTRIES)
        prefs.edit()
            .putInt(KEY_COUNT, total)
            .putString(KEY_LOG, log.joinToString("\n"))
            .apply()
        return total
    }

    /** Newest first. */
    fun entries(): List<String> =
        prefs.getString(KEY_LOG, null)
            ?.lineSequence()
            ?.filter { it.isNotBlank() }
            ?.toList()
            .orEmpty()

    fun reset() {
        prefs.edit().clear().apply()
    }

    private companion object {
        const val KEY_COUNT = "count"
        const val KEY_LOG = "log"
        const val MAX_ENTRIES = 200
    }
}
