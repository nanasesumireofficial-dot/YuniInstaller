package com.yuni.installer.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.yuni.installer.MainViewModel

private data class Tab(val label: String, val icon: ImageVector)

private val tabs = listOf(
    Tab("Home", Icons.Default.Home),
    Tab("Catalog", Icons.Default.Menu),
    Tab("Modules", Icons.AutoMirrored.Filled.List),
    Tab("Patcher", Icons.Default.Build),
    Tab("Settings", Icons.Default.Settings),
)

@Composable
fun YuniApp(vm: MainViewModel) {
    val s by vm.state.collectAsStateWithLifecycle()
    var showSplash by remember { mutableStateOf(true) }
    var tab by rememberSaveable { mutableIntStateOf(0) }
    val snackbar = remember { SnackbarHostState() }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) vm.installFromUri(uri)
    }
    val pickFile = { picker.launch(arrayOf("*/*")) }

    LaunchedEffect(s.message) {
        s.message?.let { snackbar.showSnackbar(it); vm.clearMessage() }
    }

    if (showSplash) {
        SplashScreen(onSplashFinished = { showSplash = false })
    } else {
        AnimatedVisibility(
            visible = true,
            enter = fadeIn(),
            exit = fadeOut(),
        ) {
            GlowBackground {
                Scaffold(
                    containerColor = Color.Transparent,
                    snackbarHost = {
                        SnackbarHost(snackbar) { data ->
                            Snackbar(
                                data,
                                containerColor = Color(0xFF1E1418),
                                contentColor = Yuni.Text,
                                shape = RoundedCornerShape(14.dp),
                            )
                        }
                    },
                    bottomBar = {
                        Column {
                            HorizontalDivider(color = Yuni.Border)
                            NavigationBar(containerColor = Color(0xE60A0A0C), tonalElevation = 0.dp) {
                                tabs.forEachIndexed { i, t ->
                                    NavigationBarItem(
                                        selected = tab == i,
                                        onClick = { tab = i },
                                        icon = { Icon(t.icon, contentDescription = t.label) },
                                        label = { Text(t.label, fontSize = 10.sp, fontWeight = FontWeight.Bold) },
                                        colors = NavigationBarItemDefaults.colors(
                                            selectedIconColor = Yuni.Red,
                                            selectedTextColor = Yuni.Red,
                                            indicatorColor = Yuni.Red.copy(alpha = 0.16f),
                                            unselectedIconColor = Yuni.Muted,
                                            unselectedTextColor = Yuni.Muted,
                                        ),
                                    )
                                }
                            }
                        }
                    },
                ) { pad ->
                    Box(Modifier.padding(pad)) {
                        when (tab) {
                            0 -> HomeScreen(s, vm, pickFile, onNavigateToPatcher = { tab = 3 })
                            1 -> LibraryScreen(s, vm, onNavigateToPatcher = { tab = 3 })
                            2 -> ModulesScreen(s, vm)
                            3 -> PatcherScreen(s, vm, pickFile)
                            else -> SettingsScreen(s, vm)
                        }
                    }
                }
            }
        }
    }
}
