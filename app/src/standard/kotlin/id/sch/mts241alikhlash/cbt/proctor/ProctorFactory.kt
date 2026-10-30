package id.sch.mts241alikhlash.cbt.proctor

import android.content.Context

/**
 * `standard` flavor: the camera feature is not compiled in at all, so the
 * controller does nothing. The `proctor` flavor replaces this file with one that
 * binds the real service.
 */
object ProctorFactory {
    fun controller(@Suppress("UNUSED_PARAMETER") context: Context) = ProctorController()
}
