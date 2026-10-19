package com.yuni.installer.install

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import android.os.Build
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

object ApkInstaller {
    const val ACTION_RESULT = "com.yuni.installer.INSTALL_RESULT"
    const val EXTRA_KEY = "yuni_key"
    const val EXTRA_LABEL = "yuni_label"

    suspend fun install(context: Context, files: List<File>, key: String, label: String) =
        withContext(Dispatchers.IO) {
            val installer = context.packageManager.packageInstaller
            val params = PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL)
            if (Build.VERSION.SDK_INT >= 31) {
                params.setRequireUserAction(PackageInstaller.SessionParams.USER_ACTION_NOT_REQUIRED)
            }
            val sessionId = installer.createSession(params)
            try {
                installer.openSession(sessionId).use { session ->
                    files.forEachIndexed { i, file ->
                        file.inputStream().use { input ->
                            session.openWrite("part_$i.apk", 0, file.length()).use { out ->
                                input.copyTo(out)
                                session.fsync(out)
                            }
                        }
                    }
                    val intent = Intent(context, InstallReceiver::class.java)
                        .setAction(ACTION_RESULT)
                        .putExtra(EXTRA_KEY, key)
                        .putExtra(EXTRA_LABEL, label)
                    val pending = PendingIntent.getBroadcast(
                        context, sessionId, intent,
                        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE,
                    )
                    session.commit(pending.intentSender)
                }
            } catch (t: Throwable) {
                runCatching { installer.abandonSession(sessionId) }
                throw t
            }
        }
}
