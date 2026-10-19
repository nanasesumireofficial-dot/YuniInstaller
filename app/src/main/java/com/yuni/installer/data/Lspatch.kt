package com.yuni.installer.data

enum class PatchMode(val title: String, val description: String) {
    INTEGRATED("Integrated (Portable)", "Embeds modules directly into the target APK. No manager app required."),
    LOCAL("Local Dynamic", "Loads modules dynamically at runtime. Easy to update modules without rebuilding."),
}

data class LspatchModule(
    val id: String,
    val name: String,
    val description: String,
    val author: String,
    val targetScope: List<String>,
    val versionName: String,
    val versionCode: Long,
    val downloadUrl: String,
    val isEnabled: Boolean = true,
)

data class PatchTask(
    val targetAppName: String,
    val targetPackageName: String,
    val customPackageName: String,
    val patchMode: PatchMode,
    val selectedModules: List<LspatchModule>,
)

data class PatchedRecord(
    val id: String,
    val appName: String,
    val originalPackage: String,
    val customPackage: String,
    val mode: PatchMode,
    val embeddedModules: List<String>,
    val timestamp: Long,
)

object SampleModules {
    val list = listOf(
        LspatchModule(
            id = "mod-yunigram",
            name = "YuniGram Hook",
            description = "Unlocks developer options, high-quality media upload, hidden story viewing, and ad blocking for Instagram.",
            author = "Yuni Team",
            targetScope = listOf("com.instagram.android", "com.yuni.yunigram"),
            versionName = "1.0.0",
            versionCode = 100,
            downloadUrl = "https://example.com/yunigram-module.apk",
        ),
        LspatchModule(
            id = "mod-yunitube",
            name = "YuniTube Hook",
            description = "Ad-blocking, background playback, SponsorBlock skip, RYD dislike counts, and custom AMOLED controls.",
            author = "Yuni Team",
            targetScope = listOf("com.google.android.youtube", "com.yuni.yunitube"),
            versionName = "19.16.39",
            versionCode = 191639,
            downloadUrl = "https://example.com/yunitube-module.apk",
        ),
        LspatchModule(
            id = "mod-privacy-guard",
            name = "Yuni Privacy & Analytics Blocker",
            description = "Strips embedded telemetry, analytics trackers, and forced update popups across all social apps.",
            author = "Yuni Team",
            targetScope = listOf("com.instagram.android", "com.google.android.youtube", "com.facebook.katana"),
            versionName = "1.2.0",
            versionCode = 120,
            downloadUrl = "https://example.com/privacy-guard-module.apk",
        ),
    )
}
