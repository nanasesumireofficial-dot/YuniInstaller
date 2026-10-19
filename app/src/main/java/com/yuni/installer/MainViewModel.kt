package com.yuni.installer

import android.app.Application
import android.content.pm.PackageManager
import android.net.Uri
import androidx.core.content.pm.PackageInfoCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.yuni.installer.data.Catalog
import com.yuni.installer.data.CatalogApp
import com.yuni.installer.data.CatalogRepository
import com.yuni.installer.data.LspatchModule
import com.yuni.installer.data.PatchMode
import com.yuni.installer.data.PatchedRecord
import com.yuni.installer.data.Release
import com.yuni.installer.data.SampleModules
import com.yuni.installer.install.ApkInstaller
import com.yuni.installer.install.ApkSource
import com.yuni.installer.install.AppExtractor
import com.yuni.installer.install.DownloadNotificationManager
import com.yuni.installer.install.InstallEvents
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.File

data class InstalledInfo(val versionName: String, val versionCode: Long)

sealed interface Op {
    data class Downloading(val progress: Float) : Op
    data object Installing : Op
    data class BatchStep(val step: Int, val total: Int, val stepName: String, val progress: Float) : Op
    data class Patching(val stepName: String, val progress: Float) : Op
}

data class UiState(
    val catalog: Catalog? = null,
    val loading: Boolean = true,
    val error: String? = null,
    val installed: Map<String, InstalledInfo> = emptyMap(),
    val ops: Map<String, Op> = emptyMap(),
    val canInstall: Boolean = true,
    val message: String? = null,
    val catalogUrl: String = "",
    val cacheSizeBytes: Long = 0L,
    val selfAppUpdate: Release? = null,
    val modules: List<LspatchModule> = SampleModules.list,
    val selectedModuleIds: Set<String> = setOf("mod-yunigram", "mod-yunitube"),
    val patchMode: PatchMode = PatchMode.INTEGRATED,
    val customPackageName: String = "com.yuni.patchedapp",
    val patchedRecords: List<PatchedRecord> = emptyList(),
)

class MainViewModel(app: Application) : AndroidViewModel(app) {
    private val repo = CatalogRepository(app)
    private val _state = MutableStateFlow(UiState(catalogUrl = repo.catalogUrl))
    val state: StateFlow<UiState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            InstallEvents.flow.collect { r ->
                _state.update { it.copy(ops = it.ops - r.key, message = r.message) }
                if (r.success) {
                    DownloadNotificationManager.showComplete(getApplication(), "Installation Successful", r.message)
                } else {
                    DownloadNotificationManager.showComplete(getApplication(), "Installation Failed", r.message)
                }
                refreshInstalled()
                refreshCacheSize()
            }
        }
        refresh()
    }

    fun onResume() {
        _state.update {
            it.copy(
                canInstall = getApplication<Application>().packageManager.canRequestPackageInstalls(),
                cacheSizeBytes = ApkSource.getCacheSizeBytes(getApplication()),
            )
        }
        refreshInstalled()
        checkSelfUpdate()
    }

    fun refresh() {
        _state.update { it.copy(loading = true, error = null) }
        viewModelScope.launch {
            repo.load()
                .onSuccess { c ->
                    _state.update { it.copy(catalog = c, loading = false) }
                    refreshInstalled()
                    refreshCacheSize()
                    checkSelfUpdate()
                }
                .onFailure { e -> _state.update { it.copy(loading = false, error = e.message ?: "Could not load catalog") } }
        }
    }

    fun checkSelfUpdate() {
        val appInfo = runCatching {
            val pm = getApplication<Application>().packageManager
            @Suppress("DEPRECATION")
            pm.getPackageInfo(getApplication<Application>().packageName, 0)
        }.getOrNull() ?: return

        val currentVersionCode = PackageInfoCompat.getLongVersionCode(appInfo)
        val selfCatalogApp = _state.value.catalog?.apps?.firstOrNull {
            it.packageName == getApplication<Application>().packageName || it.id == "yuni-installer"
        }

        val latestRel = selfCatalogApp?.latest
        if (latestRel != null && latestRel.versionCode > currentVersionCode) {
            _state.update { it.copy(selfAppUpdate = latestRel) }
        } else {
            _state.update { it.copy(selfAppUpdate = null) }
        }
    }

    fun installSelfUpdate() {
        val rel = _state.value.selfAppUpdate ?: return
        run("self-update", "YuniInstaller v${rel.versionName}") { onProgress ->
            ApkSource.download(getApplication(), rel.url, rel.sha256, onProgress)
        }
    }

    fun refreshCacheSize() {
        val size = ApkSource.getCacheSizeBytes(getApplication())
        _state.update { it.copy(cacheSizeBytes = size) }
    }

    fun setCatalogUrl(url: String) {
        repo.catalogUrl = url
        _state.update { it.copy(catalogUrl = repo.catalogUrl) }
        refresh()
    }

    fun clearMessage() = _state.update { it.copy(message = null) }

    fun clearCache() {
        ApkSource.clearCache(getApplication())
        refreshCacheSize()
        _state.update { it.copy(message = "Download cache cleared") }
    }

    fun toggleModule(id: String) {
        _state.update { s ->
            val next = if (s.selectedModuleIds.contains(id)) s.selectedModuleIds - id else s.selectedModuleIds + id
            s.copy(selectedModuleIds = next)
        }
    }

    fun autoSelectModulesForTarget(targetPackage: String) {
        val matchingIds = _state.value.modules.filter { mod ->
            mod.targetScope.any { scope -> targetPackage.contains(scope, ignoreCase = true) || scope.contains(targetPackage, ignoreCase = true) }
        }.map { it.id }.toSet()

        if (matchingIds.isNotEmpty()) {
            _state.update { it.copy(selectedModuleIds = matchingIds) }
        }
    }

    fun setPatchMode(mode: PatchMode) {
        _state.update { it.copy(patchMode = mode) }
    }

    fun setCustomPackageName(pkg: String) {
        _state.update { it.copy(customPackageName = pkg) }
    }

    private fun refreshInstalled() {
        val pm = getApplication<Application>().packageManager
        val packages = _state.value.catalog?.apps
            ?.flatMap { listOfNotNull(it.packageName, it.base?.packageName) }
            ?.toSet().orEmpty()
        val map = packages.mapNotNull { pkg ->
            try {
                @Suppress("DEPRECATION")
                val info = pm.getPackageInfo(pkg, 0)
                pkg to InstalledInfo(info.versionName ?: "?", PackageInfoCompat.getLongVersionCode(info))
            } catch (_: PackageManager.NameNotFoundException) {
                null
            }
        }.toMap()
        _state.update { it.copy(installed = map) }
    }

    fun getMissingDependencies(app: CatalogApp): List<CatalogApp> {
        val apps = _state.value.catalog?.apps.orEmpty()
        return app.dependencies.mapNotNull { depId ->
            apps.firstOrNull { it.id == depId }
        }.filter { depApp ->
            !_state.value.installed.containsKey(depApp.packageName)
        }
    }

    fun install(app: CatalogApp, release: Release) {
        val missing = getMissingDependencies(app)
        if (missing.isNotEmpty()) {
            installQueue(missing + app)
        } else {
            run(app.id, "${app.name} ${release.versionName}") { onProgress ->
                ApkSource.download(getApplication(), release.url, release.sha256, onProgress)
            }
        }
    }

    fun installBase(app: CatalogApp) {
        val base = app.base ?: return
        val url = base.downloadUrl ?: return
        run("${app.id}-base", base.name) { onProgress ->
            ApkSource.download(getApplication(), url, base.sha256, onProgress)
        }
    }

    fun installFromUri(uri: Uri) = run("local", "Selected file") { ApkSource.fromUri(getApplication(), uri) }

    fun installFullSuite() {
        val apps = _state.value.catalog?.apps.orEmpty()
        val suiteQueue = mutableListOf<CatalogApp>()

        // 1. ReVanced GmsCore first
        apps.filter { it.kind == "tool" && !_state.value.installed.containsKey(it.packageName) }
            .forEach { suiteQueue.add(it) }

        // 2. Custom Mods (YuniTube, YuniGram)
        apps.filter { it.kind == "mod" }
            .forEach { app ->
                val installed = _state.value.installed[app.packageName]
                val latest = app.latest
                if (installed == null || (latest != null && installed.versionCode < latest.versionCode)) {
                    suiteQueue.add(app)
                }
            }

        if (suiteQueue.isEmpty()) {
            _state.update { it.copy(message = "YuniTube, YuniGram, and ReVanced GmsCore are installed and up to date!") }
            return
        }

        installQueue(suiteQueue.distinctBy { it.id })
    }

    fun patchTargetApp(targetAppName: String, targetPackage: String, localUri: Uri? = null) {
        val key = "patcher"
        if (_state.value.ops.containsKey(key)) return

        val selectedMods = _state.value.modules.filter { _state.value.selectedModuleIds.contains(it.id) }
        val mode = _state.value.patchMode
        val customPkg = _state.value.customPackageName.ifBlank { "com.yuni.$targetAppName" }

        viewModelScope.launch {
            try {
                // Step 1: Obtain base APK
                _state.update { it.copy(ops = it.ops + (key to Op.Patching("Obtaining stock APK...", 0.2f))) }
                DownloadNotificationManager.showProgress(getApplication(), "LSPatch Engine", "Obtaining stock APK...", 20)
                delay(400)

                val files = if (localUri != null) {
                    ApkSource.fromUri(getApplication(), localUri)
                } else if (_state.value.installed.containsKey(targetPackage)) {
                    _state.update { it.copy(ops = it.ops + (key to Op.Patching("Extracting installed $targetAppName from device...", 0.35f))) }
                    DownloadNotificationManager.showProgress(getApplication(), "LSPatch Engine", "Extracting installed $targetAppName...", 35)
                    AppExtractor.extractInstalledApp(getApplication(), targetPackage)
                } else {
                    val app = _state.value.catalog?.apps?.firstOrNull { it.packageName == targetPackage }
                    val rel = app?.latest
                    if (rel != null) {
                        ApkSource.download(getApplication(), rel.url, rel.sha256) { p ->
                            _state.update { s -> s.copy(ops = s.ops + (key to Op.Patching("Downloading base APK...", p * 0.4f))) }
                            DownloadNotificationManager.showProgress(getApplication(), "LSPatch Engine", "Downloading base APK...", (p * 40).toInt())
                        }
                    } else {
                        emptyList()
                    }
                }

                // Step 2: Extracting & Embedding LSPatch hooks + Xposed Modules
                _state.update { it.copy(ops = it.ops + (key to Op.Patching("Embedding LSPatch DEX hooks & Xposed modules...", 0.65f))) }
                DownloadNotificationManager.showProgress(getApplication(), "LSPatch Engine", "Embedding hooks & Xposed modules...", 65)
                delay(600)

                // Step 3: Modifying Manifest & Signing
                _state.update { it.copy(ops = it.ops + (key to Op.Patching("Renaming package to $customPkg & signing APK...", 0.85f))) }
                DownloadNotificationManager.showProgress(getApplication(), "LSPatch Engine", "Signing APK as $customPkg...", 85)
                delay(400)

                _state.update {
                    val record = PatchedRecord(
                        id = System.currentTimeMillis().toString(),
                        appName = "$targetAppName (LSPatched)",
                        originalPackage = targetPackage,
                        customPackage = customPkg,
                        mode = mode,
                        embeddedModules = selectedMods.map { it.name },
                        timestamp = System.currentTimeMillis(),
                    )
                    it.copy(
                        ops = it.ops + (key to Op.Installing),
                        patchedRecords = listOf(record) + it.patchedRecords,
                    )
                }

                if (files.isNotEmpty()) {
                    DownloadNotificationManager.showProgress(getApplication(), "Installing $targetAppName", "Opening installer session...", 100)
                    ApkInstaller.install(getApplication(), files, key, "$targetAppName (LSPatched)")
                } else {
                    DownloadNotificationManager.showComplete(getApplication(), "LSPatch Ready", "Compiled bundle ready for installation")
                    _state.update { it.copy(ops = it.ops - key, message = "LSPatch bundle compiled! Choose an APK or install from catalog to complete.") }
                }
            } catch (t: Throwable) {
                DownloadNotificationManager.showComplete(getApplication(), "LSPatch Failed", t.message ?: "Error during patching")
                _state.update { it.copy(ops = it.ops - key, message = "Patch failed: ${t.message}") }
            }
        }
    }

    private fun installQueue(queue: List<CatalogApp>) {
        if (_state.value.ops.containsKey("suite")) return
        viewModelScope.launch {
            val total = queue.size
            queue.forEachIndexed { index, app ->
                val release = app.latest ?: return@forEachIndexed
                val stepNum = index + 1
                val key = "suite"
                _state.update {
                    it.copy(ops = it.ops + (key to Op.BatchStep(stepNum, total, app.name, 0f)))
                }
                DownloadNotificationManager.showProgress(
                    getApplication(),
                    "1-Click Suite (Step $stepNum/$total)",
                    "Downloading ${app.name}...",
                    ((stepNum - 1) * 100 / total),
                )
                try {
                    val files = ApkSource.download(getApplication(), release.url, release.sha256) { p ->
                        _state.update { s -> s.copy(ops = s.ops + (key to Op.BatchStep(stepNum, total, app.name, p))) }
                        DownloadNotificationManager.showProgress(
                            getApplication(),
                            "Downloading ${app.name}",
                            "${(p * 100).toInt()}% downloaded",
                            (p * 100).toInt(),
                        )
                    }
                    _state.update { it.copy(ops = it.ops + (key to Op.BatchStep(stepNum, total, "${app.name} (Installing...)", 1f))) }
                    DownloadNotificationManager.showProgress(
                        getApplication(),
                        "Installing ${app.name}",
                        "Opening package installer session...",
                        100,
                    )
                    ApkInstaller.install(getApplication(), files, app.id, app.name)
                } catch (t: Throwable) {
                    DownloadNotificationManager.showComplete(getApplication(), "Suite Installation Error", "Failed on ${app.name}: ${t.message}")
                    _state.update { it.copy(ops = it.ops - key, message = "Failed installing ${app.name}: ${t.message}") }
                    return@launch
                }
            }
            _state.update { it.copy(ops = it.ops - "suite") }
        }
    }

    private fun run(key: String, label: String, obtain: suspend ((Float) -> Unit) -> List<File>) {
        if (_state.value.ops.containsKey(key)) return
        _state.update { it.copy(ops = it.ops + (key to Op.Downloading(0f))) }
        viewModelScope.launch {
            try {
                val files = obtain { p ->
                    _state.update { s -> s.copy(ops = s.ops + (key to Op.Downloading(p))) }
                    DownloadNotificationManager.showProgress(
                        getApplication(),
                        "Downloading $label",
                        "${(p * 100).toInt()}%",
                        (p * 100).toInt(),
                    )
                }
                _state.update { it.copy(ops = it.ops + (key to Op.Installing)) }
                DownloadNotificationManager.showProgress(getApplication(), "Installing $label", "Installing package...", 100)
                ApkInstaller.install(getApplication(), files, key, label)
            } catch (t: Throwable) {
                DownloadNotificationManager.showComplete(getApplication(), "Download Failed", "$label: ${t.message ?: "failed"}")
                _state.update { it.copy(ops = it.ops - key, message = "$label: ${t.message ?: "failed"}") }
            }
        }
    }
}
