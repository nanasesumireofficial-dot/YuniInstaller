package com.yuni.installer.data

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Request
import com.yuni.installer.install.Http

class CatalogRepository(private val context: Context) {
    private val prefs = context.getSharedPreferences("yuni_prefs", Context.MODE_PRIVATE)

    companion object {
        const val DEFAULT_CATALOG_URL = "https://raw.githubusercontent.com/nanasesumireofficial-dot/YuniInstaller/main/app/src/main/assets/catalog.json"
    }

    var catalogUrl: String
        get() = prefs.getString("catalog_url", DEFAULT_CATALOG_URL) ?: DEFAULT_CATALOG_URL
        set(value) = prefs.edit().putString("catalog_url", value.trim()).apply()

    suspend fun load(): Result<Catalog> = withContext(Dispatchers.IO) {
        val url = catalogUrl
        val json = try {
            if (url.isBlank()) loadBundled() else loadRemote(url)
        } catch (_: Throwable) {
            loadBundled()
        }
        runCatching { CatalogParser.parse(json) }
    }

    private fun loadBundled(): String =
        context.assets.open("catalog.json").bufferedReader().use { it.readText() }

    private fun loadRemote(url: String): String {
        val req = Request.Builder().url(url).build()
        Http.client.newCall(req).execute().use { resp ->
            check(resp.isSuccessful) { "HTTP ${resp.code}" }
            return resp.body!!.string()
        }
    }
}
