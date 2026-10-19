package com.yuni.installer.ui

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.yuni.installer.InstalledInfo
import com.yuni.installer.MainViewModel
import com.yuni.installer.Op
import com.yuni.installer.UiState
import com.yuni.installer.data.CatalogApp
import com.yuni.installer.data.LspatchModule
import com.yuni.installer.data.PatchMode
import com.yuni.installer.data.PatchedRecord
import java.util.Calendar

private enum class AppState { NotInstalled, Update, Current }

private fun appState(app: CatalogApp, installed: InstalledInfo?): AppState {
    val latest = app.latest
    return when {
        installed == null -> AppState.NotInstalled
        latest != null && installed.versionCode < latest.versionCode -> AppState.Update
        else -> AppState.Current
    }
}

private val screenPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 24.dp)

// ---------------------------------------------------------------- Home

@Composable
fun HomeScreen(
    s: UiState,
    vm: MainViewModel,
    onPickFile: () -> Unit,
    onNavigateToPatcher: () -> Unit,
) {
    val ctx = LocalContext.current
    val apps = s.catalog?.apps.orEmpty()
    val installedCount = remember(s.catalog, s.installed) {
        apps.count { s.installed.containsKey(it.packageName) }
    }

    LazyColumn(contentPadding = screenPadding, verticalArrangement = Arrangement.spacedBy(14.dp)) {
        item { Wordmark() }

        if (s.selfAppUpdate != null) {
            item { SelfUpdateCard(s, vm) }
        }

        if (!s.canInstall) {
            item {
                YuniCard(glow = true) {
                    Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text("Permission needed", color = Yuni.Text, fontWeight = FontWeight.ExtraBold, fontSize = 16.sp)
                        Text(
                            "Allow YuniInstaller to install apps so it can set up your mods and LSPatches.",
                            color = Yuni.Muted, fontSize = 13.sp,
                        )
                        PillButton("Allow installs", onClick = {
                            ctx.startActivity(
                                Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${ctx.packageName}")),
                            )
                        })
                    }
                }
            }
        }

        item {
            YuniCard(glow = true) {
                Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    Text(
                        "${greeting()}, ${deviceName(ctx)}. Welcome to YuniInstaller.",
                        color = Yuni.Text, fontSize = 26.sp, lineHeight = 30.sp, fontWeight = FontWeight.ExtraBold,
                    )
                    Text(
                        "Custom YuniGram, YuniTube, and LSPatch Xposed module engine.",
                        color = Yuni.Muted, fontSize = 13.sp,
                    )
                    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        StatPill("Apps", apps.size.toString())
                        StatPill("Installed", "$installedCount / ${apps.size}")
                        StatPill("Modules", s.modules.size.toString())
                        StatPill("Patched", s.patchedRecords.size.toString(), highlight = s.patchedRecords.isNotEmpty())
                    }
                    NextUp(s, vm, onPickFile)
                }
            }
        }

        item { FullSuiteCard(s, vm) }

        item { GmsCoreCard(s) }

        if (s.patchedRecords.isNotEmpty()) {
            item { SectionHeader("Recent Patched Apps", s.patchedRecords.size) }
            items(s.patchedRecords, key = { it.id }) { rec ->
                YuniCard {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(rec.appName, color = Yuni.Text, fontWeight = FontWeight.Bold, fontSize = 15.sp, modifier = Modifier.weight(1f))
                            StatusChip(rec.mode.name, ChipTone.Good)
                        }
                        Text("Package: ${rec.customPackage}", color = Yuni.Muted, fontSize = 12.sp)
                        Text("Embedded: ${rec.embeddedModules.joinToString()}", color = Yuni.Muted, fontSize = 12.sp)
                    }
                }
            }
        }

        s.error?.let { err ->
            item {
                YuniCard {
                    Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text("Couldn't load catalog", color = Yuni.Red, fontWeight = FontWeight.ExtraBold)
                        Text(err, color = Yuni.Muted, fontSize = 13.sp)
                        PillButton("Retry", onClick = { vm.refresh() })
                    }
                }
            }
        }

        item { InstallAnyCard(s, onPickFile) }

        item { SectionHeader("Apps & Mods", apps.size) }
        if (s.loading && apps.isEmpty()) item { Text("Loading catalog...", color = Yuni.Muted, fontSize = 13.sp) }
        items(apps, key = { it.id }) { AppCard(it, s, vm, onNavigateToPatcher) }
    }
}

@Composable
private fun SelfUpdateCard(s: UiState, vm: MainViewModel) {
    val rel = s.selfAppUpdate ?: return
    val selfOp = s.ops["self-update"]

    YuniCard(glow = true) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                IconTile { Text("🚀", fontSize = 22.sp) }
                Column(Modifier.weight(1f)) {
                    Text("YuniInstaller v${rel.versionName} Available", color = Yuni.Text, fontWeight = FontWeight.ExtraBold, fontSize = 16.sp)
                    StatusChip("update ready", ChipTone.Warn)
                }
            }
            if (rel.notes.isNotBlank()) {
                Text(rel.notes, color = Yuni.Muted, fontSize = 12.sp, lineHeight = 17.sp)
            }
            if (selfOp != null) {
                OpStatus(selfOp)
            } else {
                PillButton("Update YuniInstaller Now", onClick = { vm.installSelfUpdate() })
            }
        }
    }
}

@Composable
private fun FullSuiteCard(s: UiState, vm: MainViewModel) {
    val suiteOp = s.ops["suite"]
    YuniCard(glow = true) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                IconTile { Text("⚡", fontSize = 22.sp) }
                Column(Modifier.weight(1f)) {
                    Text("1-Click Yuni Suite", color = Yuni.Text, fontSize = 18.sp, fontWeight = FontWeight.ExtraBold)
                    Spacer(Modifier.height(2.dp))
                    Text(
                        "Automatically installs YuniTube, YuniGram, and ReVanced GmsCore (MicroG) in sequence.",
                        color = Yuni.Muted, fontSize = 13.sp, lineHeight = 18.sp,
                    )
                }
            }
            if (suiteOp != null) {
                OpStatus(suiteOp)
            } else {
                PillButton("Install YuniTube, YuniGram & GmsCore", onClick = { vm.installFullSuite() })
            }
        }
    }
}

@Composable
private fun GmsCoreCard(s: UiState) {
    val ctx = LocalContext.current
    val gmsPkg = "app.revanced.android.gms"
    val isGmsInstalled = remember(s.installed) { s.installed.containsKey(gmsPkg) }

    YuniCard {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                IconTile { Text("🛡️", fontSize = 20.sp) }
                Column(Modifier.weight(1f)) {
                    Text("ReVanced GmsCore (MicroG)", color = Yuni.Text, fontWeight = FontWeight.ExtraBold, fontSize = 16.sp)
                    StatusChip(if (isGmsInstalled) "active & setup" else "not installed", if (isGmsInstalled) ChipTone.Good else ChipTone.Warn)
                }
            }
            Text(
                "Provides Google sign-in and background push notifications for YuniTube non-root installations.",
                color = Yuni.Muted, fontSize = 12.sp, lineHeight = 17.sp,
            )
            if (isGmsInstalled) {
                PillButton("Open GmsCore App Info", primary = false, onClick = {
                    val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:$gmsPkg"))
                    ctx.startActivity(intent)
                })
            }
        }
    }
}

@Composable
private fun NextUp(s: UiState, vm: MainViewModel, onPickFile: () -> Unit) {
    val apps = s.catalog?.apps.orEmpty()
    val update = apps.firstOrNull { appState(it, s.installed[it.packageName]) == AppState.Update }
    val fresh = apps.firstOrNull { appState(it, s.installed[it.packageName]) == AppState.NotInstalled }

    val text: String
    val action: String
    val onClick: () -> Unit
    when {
        update != null -> {
            text = "Update ${update.name} to ${update.latest?.versionName}"
            action = "Update"
            onClick = { update.latest?.let { vm.install(update, it) } }
        }
        fresh != null -> {
            text = "Install ${fresh.name}"
            action = "Install"
            onClick = { fresh.latest?.let { vm.install(fresh, it) } }
        }
        else -> {
            text = if (s.loading) "Loading catalog..." else "You're all caught up"
            action = "Install APK"
            onClick = onPickFile
        }
    }
    val shape = RoundedCornerShape(18.dp)
    Row(
        Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(Color.Black.copy(alpha = 0.35f))
            .border(1.dp, Yuni.Border, shape)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text(
            "NEXT UP",
            color = Yuni.Text, fontSize = 9.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = 1.sp,
            modifier = Modifier
                .clip(RoundedCornerShape(50))
                .border(1.dp, Yuni.Border, RoundedCornerShape(50))
                .padding(horizontal = 9.dp, vertical = 5.dp),
        )
        Text(text, color = Yuni.Text, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
        PillButton(action, onClick = onClick)
    }
}

@Composable
private fun InstallAnyCard(s: UiState, onPickFile: () -> Unit) {
    YuniCard {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                IconTile { Icon(Icons.Default.Add, contentDescription = null, tint = Yuni.Red) }
                Column(Modifier.weight(1f)) {
                    Text("Install any APK", color = Yuni.Text, fontSize = 17.sp, fontWeight = FontWeight.ExtraBold)
                    Spacer(Modifier.height(2.dp))
                    Text(
                        "Pick an .apk or a split bundle (.apks, .xapk, .apkm), like your custom YuniTube build.",
                        color = Yuni.Muted, fontSize = 13.sp, lineHeight = 18.sp,
                    )
                }
            }
            val op = s.ops["local"]
            if (op != null) OpStatus(op) else PillButton("Choose file", onClick = onPickFile)
        }
    }
}

@Composable
private fun SectionHeader(title: String, count: Int) {
    Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(title, color = Yuni.Text, fontSize = 20.sp, fontWeight = FontWeight.ExtraBold)
        Text(count.toString(), color = Yuni.Muted, fontSize = 13.sp, modifier = Modifier.padding(bottom = 2.dp))
    }
}

// ---------------------------------------------------------------- Catalog / App card

@Composable
fun AppCard(
    app: CatalogApp,
    s: UiState,
    vm: MainViewModel,
    onNavigateToPatcher: (() -> Unit)? = null,
) {
    val ctx = LocalContext.current
    val installed = s.installed[app.packageName]
    val latest = app.latest
    val state = appState(app, installed)
    val op = s.ops[app.id]
    val baseOp = s.ops["${app.id}-base"]
    val missingDeps = remember(s.catalog, s.installed) { vm.getMissingDependencies(app) }

    YuniCard(glow = state == AppState.Update) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                IconTile {
                    Text(app.name.take(1).uppercase(), color = Yuni.Red, fontSize = 20.sp, fontWeight = FontWeight.ExtraBold)
                }
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                    Text(app.name, color = Yuni.Text, fontSize = 17.sp, fontWeight = FontWeight.ExtraBold)
                    when (state) {
                        AppState.NotInstalled -> StatusChip("not installed", ChipTone.Neutral)
                        AppState.Update -> StatusChip("update available", ChipTone.Warn)
                        AppState.Current -> StatusChip("installed", ChipTone.Good)
                    }
                }
            }

            if (app.description.isNotBlank()) {
                Text(app.description, color = Yuni.Muted, fontSize = 13.sp, lineHeight = 18.sp)
            }

            if (missingDeps.isNotEmpty()) {
                val depNames = missingDeps.joinToString { it.name }
                Text(
                    "⚡ Requires $depNames (will be installed automatically)",
                    color = Yuni.RedBright,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                )
            }

            val versions = buildString {
                installed?.let { append("Installed ${it.versionName}") }
                latest?.let { if (isNotEmpty()) append("   |   "); append("Latest ${it.versionName}") }
            }
            if (versions.isNotEmpty()) Text(versions, color = Yuni.Muted, fontSize = 12.sp)
            latest?.notes?.takeIf { it.isNotBlank() }?.let { Text(it, color = Yuni.Muted, fontSize = 12.sp) }

            app.base?.let { base ->
                val stock = s.installed[base.packageName]
                val supported = base.supportedVersions.joinToString(", ")
                val (line, color) = when {
                    base.packageName == app.packageName -> "Built for ${base.name} $supported" to Yuni.Muted
                    stock == null -> "${base.name} (stock) not installed" to Yuni.Muted
                    stock.versionName in base.supportedVersions -> "${base.name} ${stock.versionName} is supported" to Yuni.Green
                    else -> "${base.name} ${stock.versionName} is unsupported. Supported: $supported" to Yuni.Red
                }
                Text(line, color = color, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                if (base.downloadUrl != null) {
                    if (baseOp != null) {
                        OpStatus(baseOp)
                    } else {
                        PillButton(
                            "Get supported ${base.name}" + (base.recommendedVersion?.let { " $it" } ?: ""),
                            onClick = { vm.installBase(app) },
                            primary = false,
                        )
                    }
                }
            }

            if (op != null) {
                OpStatus(op)
            } else {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (latest != null) {
                        val label = when (state) {
                            AppState.NotInstalled -> "Install"
                            AppState.Update -> "Update"
                            AppState.Current -> "Reinstall"
                        }
                        PillButton(label, onClick = { vm.install(app, latest) }, primary = state != AppState.Current)
                    }
                    if (onNavigateToPatcher != null && app.kind == "mod") {
                        PillButton("LSPatch", primary = false, onClick = onNavigateToPatcher)
                    }
                    if (installed != null) {
                        PillButton("Open", primary = false, onClick = {
                            ctx.packageManager.getLaunchIntentForPackage(app.packageName)?.let(ctx::startActivity)
                        })
                        PillButton("Uninstall", primary = false, onClick = {
                            ctx.startActivity(Intent(Intent.ACTION_DELETE, Uri.parse("package:${app.packageName}")))
                        })
                    }
                }
            }
        }
    }
}

// ---------------------------------------------------------------- Catalog / Library

private enum class Filter(val label: String) { All("All"), Mods("Mods"), Tools("Tools"), Installed("Installed") }

@Composable
fun LibraryScreen(s: UiState, vm: MainViewModel, onNavigateToPatcher: () -> Unit) {
    var query by remember { mutableStateOf("") }
    var filter by remember { mutableStateOf(Filter.All) }

    val apps by remember(s.catalog, filter, query, s.installed) {
        derivedStateOf {
            s.catalog?.apps.orEmpty().filter { app ->
                val matchesFilter = when (filter) {
                    Filter.All -> true
                    Filter.Mods -> app.kind == "mod"
                    Filter.Tools -> app.kind != "mod"
                    Filter.Installed -> s.installed.containsKey(app.packageName)
                }
                matchesFilter && (query.isBlank() || app.name.contains(query, ignoreCase = true) ||
                    app.description.contains(query, ignoreCase = true))
            }
        }
    }

    LazyColumn(contentPadding = screenPadding, verticalArrangement = Arrangement.spacedBy(14.dp)) {
        item { Wordmark() }
        item { SectionHeader("Catalog", apps.size) }
        item { YuniField(query, { query = it }, "Filter apps & mods") }
        item {
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Filter.entries.forEach { f -> FilterPill(f.label, filter == f) { filter = f } }
            }
        }
        if (apps.isEmpty()) item { Text("Nothing here yet.", color = Yuni.Muted, fontSize = 13.sp) }
        items(apps, key = { it.id }) { AppCard(it, s, vm, onNavigateToPatcher) }
    }
}

// ---------------------------------------------------------------- Modules

@Composable
fun ModulesScreen(s: UiState, vm: MainViewModel) {
    LazyColumn(contentPadding = screenPadding, verticalArrangement = Arrangement.spacedBy(14.dp)) {
        item { Wordmark() }
        item { SectionHeader("Xposed & LSPatch Modules", s.modules.size) }
        item {
            Text(
                "Select Xposed hooks & modules to embed into YuniGram, YuniTube, or target APKs.",
                color = Yuni.Muted, fontSize = 13.sp, lineHeight = 18.sp,
            )
        }
        items(s.modules, key = { it.id }) { mod ->
            val isSelected = s.selectedModuleIds.contains(mod.id)
            YuniCard(glow = isSelected) {
                Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Checkbox(
                            checked = isSelected,
                            onCheckedChange = { vm.toggleModule(mod.id) },
                            colors = CheckboxDefaults.colors(
                                checkedColor = Yuni.Red,
                                uncheckedColor = Yuni.Muted,
                            ),
                        )
                        Column(Modifier.weight(1f)) {
                            Text(mod.name, color = Yuni.Text, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                            Text("By ${mod.author} • v${mod.versionName}", color = Yuni.Muted, fontSize = 12.sp)
                        }
                    }
                    Text(mod.description, color = Yuni.Muted, fontSize = 13.sp, lineHeight = 18.sp)
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        mod.targetScope.forEach { scope ->
                            StatusChip(scope, ChipTone.Neutral)
                        }
                    }
                }
            }
        }
    }
}

// ---------------------------------------------------------------- Patcher

@Composable
fun PatcherScreen(s: UiState, vm: MainViewModel, onPickFile: () -> Unit) {
    var targetAppName by remember { mutableStateOf("YuniGram") }
    var targetPkg by remember { mutableStateOf("com.instagram.android") }
    var customPkg by remember { mutableStateOf("com.yuni.yunigram") }
    val patcherOp = s.ops["patcher"]

    LazyColumn(contentPadding = screenPadding, verticalArrangement = Arrangement.spacedBy(14.dp)) {
        item { Wordmark() }
        item { SectionHeader("LSPatch Engine", 0) }

        item {
            YuniCard(glow = true) {
                Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    SectionLabel("1. Target App & Source")
                    Text("Select target app or pick a local APK file:", color = Yuni.Muted, fontSize = 13.sp)

                    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterPill("YuniGram", targetPkg == "com.instagram.android") {
                            targetAppName = "YuniGram"
                            targetPkg = "com.instagram.android"
                            customPkg = "com.yuni.yunigram"
                            vm.autoSelectModulesForTarget("com.instagram.android")
                        }
                        FilterPill("YuniTube", targetPkg == "com.google.android.youtube") {
                            targetAppName = "YuniTube"
                            targetPkg = "com.google.android.youtube"
                            customPkg = "com.yuni.yunitube"
                            vm.autoSelectModulesForTarget("com.google.android.youtube")
                        }
                    }

                    PillButton("Select Local APK File", primary = false, onClick = onPickFile)

                    YuniField(targetAppName, { targetAppName = it }, "Target App Name")
                    YuniField(targetPkg, { targetPkg = it }, "Target Package Name")
                    YuniField(customPkg, { customPkg = it }, "Custom Mod Package Name (e.g. com.yuni.yunigram)")
                }
            }
        }

        item {
            YuniCard {
                Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    SectionLabel("2. LSPatch Mode")
                    PatchMode.entries.forEach { mode ->
                        val selected = s.patchMode == mode
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(14.dp))
                                .background(if (selected) Yuni.Red.copy(alpha = 0.15f) else Color.Transparent)
                                .border(1.dp, if (selected) Yuni.Red else Yuni.Border, RoundedCornerShape(14.dp))
                                .clickable { vm.setPatchMode(mode) }
                                .padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            Column(Modifier.weight(1f)) {
                                Text(mode.title, color = Yuni.Text, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                                Spacer(Modifier.height(2.dp))
                                Text(mode.description, color = Yuni.Muted, fontSize = 12.sp)
                            }
                            if (selected) {
                                Icon(Icons.Default.Check, contentDescription = null, tint = Yuni.Red)
                            }
                        }
                    }
                }
            }
        }

        item {
            YuniCard {
                Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    SectionLabel("3. Embedded Modules (${s.selectedModuleIds.size})")
                    s.modules.forEach { mod ->
                        val isSelected = s.selectedModuleIds.contains(mod.id)
                        Row(
                            Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            Checkbox(
                                checked = isSelected,
                                onCheckedChange = { vm.toggleModule(mod.id) },
                                colors = CheckboxDefaults.colors(checkedColor = Yuni.Red),
                            )
                            Column(Modifier.weight(1f)) {
                                Text(mod.name, color = Yuni.Text, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                                Text("v${mod.versionName}", color = Yuni.Muted, fontSize = 11.sp)
                            }
                        }
                    }
                }
            }
        }

        item {
            YuniCard(glow = true) {
                Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    SectionLabel("4. Build & Install")
                    if (patcherOp != null) {
                        OpStatus(patcherOp)
                    } else {
                        PillButton(
                            "Compile & Install Patched APK",
                            onClick = { vm.patchTargetApp(targetAppName, targetPkg) },
                        )
                    }
                }
            }
        }
    }
}

// ---------------------------------------------------------------- Settings

@Composable
fun SettingsScreen(s: UiState, vm: MainViewModel) {
    val ctx = LocalContext.current
    var url by remember(s.catalogUrl) { mutableStateOf(s.catalogUrl) }
    val version = remember {
        runCatching { ctx.packageManager.getPackageInfo(ctx.packageName, 0).versionName }.getOrNull() ?: "?"
    }
    val cacheMB = remember(s.cacheSizeBytes) {
        "%.1f MB".format(s.cacheSizeBytes / (1024f * 1024f))
    }

    LazyColumn(contentPadding = screenPadding, verticalArrangement = Arrangement.spacedBy(14.dp)) {
        item { Wordmark() }
        item { SectionHeader("Settings", 0) }

        if (s.selfAppUpdate != null) {
            item { SelfUpdateCard(s, vm) }
        }

        item {
            YuniCard {
                Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    SectionLabel("Catalog")
                    Text(
                        "HTTPS link to your catalog.json. Leave empty to use the sample bundled with the app.",
                        color = Yuni.Muted, fontSize = 13.sp,
                    )
                    YuniField(url, { url = it }, "https://example.com/catalog.json")
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        PillButton("Save", onClick = { vm.setCatalogUrl(url) })
                        PillButton("Refresh", primary = false, onClick = { vm.refresh() })
                    }
                }
            }
        }

        item {
            YuniCard {
                Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    SectionLabel("Installer & LSPatch Storage")
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text("Install permission", color = Yuni.Text, fontSize = 14.sp, modifier = Modifier.weight(1f))
                        if (s.canInstall) StatusChip("granted", ChipTone.Good) else StatusChip("needed", ChipTone.Warn)
                    }
                    if (!s.canInstall) {
                        PillButton("Allow installs", onClick = {
                            ctx.startActivity(
                                Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${ctx.packageName}")),
                            )
                        })
                    }
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text("Cache Storage Usage", color = Yuni.Text, fontSize = 14.sp, modifier = Modifier.weight(1f))
                        StatusChip(cacheMB, ChipTone.Neutral)
                    }
                    PillButton("Clear download cache ($cacheMB)", primary = false, onClick = { vm.clearCache() })
                }
            }
        }

        item {
            YuniCard {
                Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    SectionLabel("About")
                    Text("YuniInstaller $version", color = Yuni.Text, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                    Text("Installs YuniGram, YuniTube, and LSPatch Xposed modules.", color = Yuni.Muted, fontSize = 13.sp)
                }
            }
        }
    }
}

// ---------------------------------------------------------------- helpers

private fun greeting(): String {
    val h = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
    return when {
        h < 5 -> "Good evening"
        h < 12 -> "Good morning"
        h < 18 -> "Good afternoon"
        else -> "Good evening"
    }
}

private fun deviceName(ctx: android.content.Context): String =
    runCatching { Settings.Global.getString(ctx.contentResolver, "device_name") }.getOrNull()
        ?.takeIf { it.isNotBlank() } ?: Build.MODEL
