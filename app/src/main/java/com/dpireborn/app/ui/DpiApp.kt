package com.dpireborn.app.ui

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.dpireborn.app.AppViewModel
import com.dpireborn.app.ui.theme.BgBlack
import com.dpireborn.app.ui.theme.DpiTheme
import com.dpireborn.app.ui.theme.SurfaceDark
import com.dpireborn.app.ui.theme.TextSecondary
import com.dpireborn.app.ui.theme.accentColor

private data class NavTab(val label: String, val icon: ImageVector)

private val navTabs = listOf(
    NavTab("Início", Icons.Filled.Home),
    NavTab("Arquivos", Icons.Filled.FolderOpen),
    NavTab("Configurações", Icons.Filled.Settings),
)

@Composable
fun DpiApp(vm: AppViewModel) {
    val accentKey by vm.accent.collectAsStateWithLifecycle()
    val active by vm.active.collectAsStateWithLifecycle()
    val accent = accentColor(accentKey)

    DpiTheme(accent = accent) {
        var tab by rememberSaveable { mutableIntStateOf(0) }
        var homeSub by rememberSaveable { mutableIntStateOf(0) }
        val holder = rememberSaveableStateHolder()
        val snackbar = remember { SnackbarHostState() }

        LaunchedEffect(Unit) {
            vm.messages.collect { snackbar.showSnackbar(it) }
        }
        val showPicker by vm.showPicker.collectAsStateWithLifecycle()
        if (showPicker) FolderPickerDialog(vm) { vm.showPicker.value = false }

        Scaffold(
            containerColor = BgBlack,
            snackbarHost = { SnackbarHost(snackbar) },
            bottomBar = {
                NavigationBar(containerColor = SurfaceDark) {
                    navTabs.forEachIndexed { index, t ->
                        NavigationBarItem(
                            selected = tab == index,
                            onClick = { tab = index },
                            icon = {
                                if (index == 0 && active.isNotEmpty()) {
                                    BadgedBox(badge = { Badge { Text(active.size.toString()) } }) {
                                        Icon(t.icon, contentDescription = t.label)
                                    }
                                } else {
                                    Icon(t.icon, contentDescription = t.label)
                                }
                            },
                            label = { Text(t.label, maxLines = 1, fontSize = 11.sp) },
                            colors = NavigationBarItemDefaults.colors(
                                selectedIconColor = androidx.compose.ui.graphics.Color.White,
                                selectedTextColor = accent,
                                indicatorColor = accent.copy(alpha = 0.35f),
                                unselectedIconColor = TextSecondary,
                                unselectedTextColor = TextSecondary,
                            ),
                        )
                    }
                }
            },
        ) { padding ->
            Box(
                modifier = Modifier
                    .padding(padding)
                    .fillMaxSize()
            ) {
                Crossfade(targetState = tab, animationSpec = tween(180), label = "tabs") { current ->
                    holder.SaveableStateProvider(current) {
                        when (current) {
                            0 -> HomeScreen(vm, homeSub) { homeSub = it }
                            1 -> FilesHubScreen(vm) { homeSub = 2; tab = 0 }
                            else -> SettingsScreen(vm)
                        }
                    }
                }
            }
        }
    }
}
