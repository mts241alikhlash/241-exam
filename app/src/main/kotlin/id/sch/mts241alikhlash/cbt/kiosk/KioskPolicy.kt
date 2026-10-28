package id.sch.mts241alikhlash.cbt.kiosk

import android.app.ActivityManager

/**
 * Lock-task behaviour differs per OEM: rejecting the pinning dialog may leave the
 * device fully locked on one phone and fully navigable on another. The app must
 * never assume pinning succeeded — it asks the system and gates the exam.
 */
object KioskPolicy {
    fun isSecure(lockTaskModeState: Int): Boolean =
        lockTaskModeState == ActivityManager.LOCK_TASK_MODE_PINNED ||
            lockTaskModeState == ActivityManager.LOCK_TASK_MODE_LOCKED
}
