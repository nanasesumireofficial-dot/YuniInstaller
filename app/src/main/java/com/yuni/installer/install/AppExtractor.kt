package com.yuni.installer.install

import android.content.Context
import android.content.pm.PackageManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

object AppExtractor {
    suspend fun extractInstalledApp(context: Context, packageName: String): List<File> = withContext(Dispatchers.IO) {
        val pm = context.packageManager
        val appInfo = try {
            @Suppress("DEPRECATION")
            pm.getApplicationInfo(packageName, 0)
        } catch (_: PackageManager.NameNotFoundException) {
            throw IllegalArgumentException("App $packageName is not installed on this device")
        }

        val sourceDir = File(appInfo.sourceDir)
        val files = mutableListOf<File>()

        val outDir = File(context.cacheDir, "extracted_${packageName.replace('.', '_')}").apply {
            deleteRecursively()
            mkdirs()
        }

        val baseDest = File(outDir, "base.apk")
        sourceDir.inputStream().use { input ->
            baseDest.outputStream().use { input.copyTo(it) }
        }
        files.add(baseDest)

        appInfo.splitSourceDirs?.forEachIndexed { index, splitPath ->
            val splitFile = File(splitPath)
            if (splitFile.exists()) {
                val splitDest = File(outDir, "split_$index.apk")
                splitFile.inputStream().use { input ->
                    splitDest.outputStream().use { input.copyTo(it) }
                }
                files.add(splitDest)
            }
        }

        files
    }
}
