package com.yuni.installer.install

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.ConnectionPool
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.security.MessageDigest
import java.util.concurrent.TimeUnit
import java.util.zip.ZipInputStream

object Http {
    val client: OkHttpClient = OkHttpClient.Builder()
        .connectionPool(ConnectionPool(16, 5, TimeUnit.MINUTES))
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(45, TimeUnit.SECONDS)
        .retryOnConnectionFailure(true)
        .build()
}

/** High-performance downloads & bundle processing with automatic cache management. */
object ApkSource {
    private val BUNDLE_EXTENSIONS = setOf("apks", "xapk", "apkm", "zip")

    private fun workDir(context: Context) = File(context.cacheDir, "apks").apply { mkdirs() }

    fun clearCache(context: Context) {
        workDir(context).deleteRecursively()
    }

    fun getCacheSizeBytes(context: Context): Long {
        val dir = workDir(context)
        if (!dir.exists()) return 0L
        return dir.walkTopDown().filter { it.isFile }.sumOf { it.length() }
    }

    fun cleanStaleCache(context: Context) {
        val dir = workDir(context)
        val cutoff = System.currentTimeMillis() - (24 * 3600 * 1000L) // 24 hours
        if (dir.exists()) {
            dir.listFiles()?.forEach { file ->
                if (file.lastModified() < cutoff) {
                    file.deleteRecursively()
                }
            }
        }
    }

    suspend fun download(
        context: Context,
        url: String,
        sha256: String?,
        onProgress: (Float) -> Unit,
    ): List<File> = withContext(Dispatchers.IO) {
        cleanStaleCache(context)
        val name = File(url.substringBefore('?')).name.ifBlank { "download.apk" }
        val dest = File(workDir(context), name)
        val req = Request.Builder().url(url).build()
        Http.client.newCall(req).execute().use { resp ->
            check(resp.isSuccessful) { "Download failed: HTTP ${resp.code}" }
            val body = resp.body!!
            val total = body.contentLength()
            var done = 0L
            body.byteStream().use { input ->
                dest.outputStream().buffered(256 * 1024).use { output ->
                    val buf = ByteArray(256 * 1024) // 256KB high throughput buffer
                    while (true) {
                        val n = input.read(buf)
                        if (n < 0) break
                        output.write(buf, 0, n)
                        done += n
                        if (total > 0) onProgress(done.toFloat() / total)
                    }
                }
            }
        }
        if (!sha256.isNullOrBlank()) {
            val actual = sha256Of(dest)
            check(actual.equals(sha256.trim(), ignoreCase = true)) {
                "Checksum mismatch - the downloaded file is not what the catalog expects."
            }
        }
        prepare(dest, name)
    }

    suspend fun fromUri(context: Context, uri: Uri): List<File> = withContext(Dispatchers.IO) {
        val name = displayName(context, uri) ?: "picked.apk"
        val dest = File(workDir(context), File(name).name)
        context.contentResolver.openInputStream(uri)!!.use { input ->
            dest.outputStream().use { input.copyTo(it) }
        }
        prepare(dest, name)
    }

    private fun prepare(file: File, name: String): List<File> {
        val ext = name.substringAfterLast('.', "").lowercase()
        if (ext !in BUNDLE_EXTENSIONS) return listOf(file)

        val outDir = File(file.parentFile, file.nameWithoutExtension + "_splits").apply {
            deleteRecursively(); mkdirs()
        }
        val out = mutableListOf<File>()
        ZipInputStream(file.inputStream().buffered()).use { zip ->
            while (true) {
                val entry = zip.nextEntry ?: break
                val leaf = File(entry.name).name
                if (!entry.isDirectory && leaf.endsWith(".apk", ignoreCase = true)) {
                    val target = File(outDir, leaf)
                    target.outputStream().use { zip.copyTo(it) }
                    out += target
                }
            }
        }
        check(out.isNotEmpty()) { "No .apk files found inside $name" }
        return out
    }

    private fun displayName(context: Context, uri: Uri): String? =
        context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
            ?.use { c -> if (c.moveToFirst()) c.getString(0) else null }

    fun sha256Of(file: File): String {
        val md = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input ->
            val buf = ByteArray(256 * 1024)
            while (true) {
                val n = input.read(buf)
                if (n < 0) break
                md.update(buf, 0, n)
            }
        }
        return md.digest().joinToString("") { "%02x".format(it) }
    }
}
