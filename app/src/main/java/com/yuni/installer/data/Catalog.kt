package com.yuni.installer.data

import org.json.JSONArray
import org.json.JSONObject

data class Catalog(val name: String, val apps: List<CatalogApp>)

data class CatalogApp(
    val id: String,
    val name: String,
    val description: String,
    val packageName: String,
    val kind: String,
    val base: BaseApp?,
    val dependencies: List<String> = emptyList(),
    val releases: List<Release>,
) {
    val latest: Release? get() = releases.maxByOrNull { it.versionCode }
}

data class BaseApp(
    val name: String,
    val packageName: String,
    val supportedVersions: List<String>,
    val recommendedVersion: String?,
    val downloadUrl: String?,
    val sha256: String?,
)

data class Release(
    val versionName: String,
    val versionCode: Long,
    val url: String,
    val sha256: String?,
    val notes: String,
)

object CatalogParser {
    fun parse(json: String): Catalog {
        val root = JSONObject(json)
        val apps = root.optJSONArray("apps") ?: JSONArray()
        return Catalog(
            name = root.str("name") ?: "Yuni Catalog",
            apps = (0 until apps.length()).map { parseApp(apps.getJSONObject(it)) },
        )
    }

    private fun parseApp(o: JSONObject): CatalogApp {
        val releases = o.optJSONArray("releases") ?: JSONArray()
        val deps = o.optJSONArray("dependencies") ?: JSONArray()
        return CatalogApp(
            id = o.getString("id"),
            name = o.getString("name"),
            description = o.str("description") ?: "",
            packageName = o.getString("packageName"),
            kind = o.str("kind") ?: "app",
            base = o.optJSONObject("base")?.let(::parseBase),
            dependencies = (0 until deps.length()).map { deps.getString(it) },
            releases = (0 until releases.length()).map { parseRelease(releases.getJSONObject(it)) },
        )
    }

    private fun parseBase(o: JSONObject): BaseApp {
        val versions = o.optJSONArray("supportedVersions") ?: JSONArray()
        return BaseApp(
            name = o.str("name") ?: o.getString("packageName"),
            packageName = o.getString("packageName"),
            supportedVersions = (0 until versions.length()).map { versions.getString(it) },
            recommendedVersion = o.str("recommendedVersion"),
            downloadUrl = o.str("downloadUrl"),
            sha256 = o.str("sha256"),
        )
    }

    private fun parseRelease(o: JSONObject) = Release(
        versionName = o.getString("versionName"),
        versionCode = o.getLong("versionCode"),
        url = o.getString("url"),
        sha256 = o.str("sha256"),
        notes = o.str("notes") ?: "",
    )

    private fun JSONObject.str(key: String): String? =
        if (isNull(key)) null else optString(key).takeIf { it.isNotBlank() }
}
