package com.yuni.installer.install

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import android.os.Build
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow

data class InstallResult(val key: String, val label: String, val success: Boolean, val message: String)

object InstallEvents {
    private val _flow = MutableSharedFlow<InstallResult>(extraBufferCapacity = 16)
    val flow: SharedFlow<InstallResult> = _flow.asSharedFlow()
    fun emit(result: InstallResult) { _flow.tryEmit(result) }
}

class InstallReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val status = intent.getIntExtra(PackageInstaller.EXTRA_STATUS, PackageInstaller.STATUS_FAILURE)
        val detail = intent.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE)
        val key = intent.getStringExtra(ApkInstaller.EXTRA_KEY) ?: "unknown"
        val label = intent.getStringExtra(ApkInstaller.EXTRA_LABEL) ?: "App"

        when (status) {
            PackageInstaller.STATUS_PENDING_USER_ACTION -> {
                @Suppress("DEPRECATION")
                val confirm = if (Build.VERSION.SDK_INT >= 33) {
                    intent.getParcelableExtra(Intent.EXTRA_INTENT, Intent::class.java)
                } else {
                    intent.getParcelableExtra(Intent.EXTRA_INTENT)
                }
                confirm?.let {
                    it.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    context.startActivity(it)
                }
            }
            PackageInstaller.STATUS_SUCCESS ->
                InstallEvents.emit(InstallResult(key, label, true, "$label installed"))
            else ->
                InstallEvents.emit(InstallResult(key, label, false, "$label: ${explain(status, detail)}"))
        }
    }

    private fun explain(status: Int, detail: String?): String = when (status) {
        PackageInstaller.STATUS_FAILURE_ABORTED -> "Installation cancelled"
        PackageInstaller.STATUS_FAILURE_BLOCKED -> "Blocked by the system or a device policy"
        PackageInstaller.STATUS_FAILURE_CONFLICT ->
            "Conflicts with an installed app. If it was signed with a different key, uninstall the existing one first."
        PackageInstaller.STATUS_FAILURE_INCOMPATIBLE -> "Not compatible with this device"
        PackageInstaller.STATUS_FAILURE_INVALID -> "The APK is invalid or corrupted"
        PackageInstaller.STATUS_FAILURE_STORAGE -> "Not enough storage"
        else -> detail ?: "Installation failed"
    }
}
