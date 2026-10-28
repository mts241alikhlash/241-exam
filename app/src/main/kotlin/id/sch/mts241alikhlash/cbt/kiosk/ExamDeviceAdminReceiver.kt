package id.sch.mts241alikhlash.cbt.kiosk

import android.app.admin.DeviceAdminReceiver

/**
 * Admin receiver required so the app can be provisioned as Device Owner.
 * As Device Owner, lock-task runs silently: no pinning dialog, no escape gesture.
 */
class ExamDeviceAdminReceiver : DeviceAdminReceiver()
