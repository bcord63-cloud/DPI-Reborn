package com.dpireborn.app.ui

import android.os.Environment
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.SdStorage
import androidx.compose.material.icons.filled.VideogameAsset
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.dpireborn.app.AppViewModel
import com.dpireborn.app.data.StoragePermission
import com.dpireborn.app.ui.theme.BgBlack
import com.dpireborn.app.ui.theme.OutlineDim
import com.dpireborn.app.ui.theme.TextSecondary
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

private fun storageRoots(): List<Pair<String, File>> {
    val out = ArrayList<Pair<String, File>>()
    @Suppress("DEPRECATION")
    out.add("Armazenamento interno" to Environment.getExternalStorageDirectory())
    try {
        File("/storage").listFiles()?.forEach {
            if (it.isDirectory && it.name != "emulated" && it.name != "self" && it.canRead()) {
                out.add("Cartão SD (${it.name})" to it)
            }
        }
    } catch (e: Exception) {
        // sem cartão SD
    }
    return out
}

/**
 * Seletor de pastas do próprio DPI Reborn, no estilo do DirectPackageInstaller: navega pelos caminhos reais
 * do celular, inclusive Download. Toque numa pasta para entrar; "Usar esta pasta" adiciona à biblioteca;
 * toque num .pkg para importá-lo sozinho. Exige "Acesso a todos os arquivos".
 */
@Composable
fun FolderPickerDialog(vm: AppViewModel, onDismiss: () -> Unit) {
    val ctx = LocalContext.current
    var tick by remember { mutableIntStateOf(0) }
    val hasPermission = remember(tick) { StoragePermission.hasAllFiles(ctx) }
    val settingsLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { tick++ }
    val legacyLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { tick++ }
    var current by remember { mutableStateOf<File?>(null) }
    var entries by remember { mutableStateOf<List<File>>(emptyList()) }
    var loading by remember { mutableStateOf(false) }
    val roots = remember { storageRoots() }

    LaunchedEffect(current, hasPermission) {
        val dir = current
        if (dir == null || !hasPermission) {
            entries = emptyList()
            return@LaunchedEffect
        }
        loading = true
        entries = withContext(Dispatchers.IO) {
            try {
                (dir.listFiles() ?: emptyArray())
                    .filter { !it.name.startsWith(".") && (it.isDirectory || it.name.endsWith(".pkg", ignoreCase = true)) }
                    .sortedWith(compareBy({ !it.isDirectory }, { it.name.lowercase() }))
            } catch (e: Exception) {
                emptyList()
            }
        }
        loading = false
    }

    val goUp: () -> Unit = {
        val c = current
        if (c == null || roots.any { it.second == c }) current = null else current = c.parentFile
    }

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(modifier = Modifier.fillMaxSize(), color = BgBlack) {
            Column(modifier = Modifier.statusBarsPadding().navigationBarsPadding().padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = { if (current == null) onDismiss() else goUp() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Voltar")
                    }
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Selecionar pasta", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                        Text(
                            current?.absolutePath ?: "Escolha o armazenamento",
                            style = MaterialTheme.typography.bodySmall, color = TextSecondary,
                            maxLines = 2, overflow = TextOverflow.Ellipsis,
                        )
                    }
                }

                if (!hasPermission) {
                    Box(modifier = Modifier.weight(1f)) {
                        EmptyState(
                            icon = Icons.Filled.Folder,
                            title = "Permita o acesso aos arquivos",
                            text = "Para navegar por todas as pastas, inclusive Download, o Android pede a permissão " +
                                "\"Acesso a todos os arquivos\". Ligue para o DPI Reborn e volte.",
                            action = {
                                Button(onClick = {
                                    if (android.os.Build.VERSION.SDK_INT >= 30) {
                                        settingsLauncher.launch(StoragePermission.settingsIntent(ctx))
                                    } else {
                                        legacyLauncher.launch(
                                            arrayOf(
                                                android.Manifest.permission.READ_EXTERNAL_STORAGE,
                                                android.Manifest.permission.WRITE_EXTERNAL_STORAGE,
                                            )
                                        )
                                    }
                                }) { Text("Permitir acesso") }
                            },
                        )
                    }
                } else {
                    @Suppress("DEPRECATION")
                    val base = Environment.getExternalStorageDirectory()
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(vertical = 6.dp)) {
                        for (name in listOf("Download", "Documents", "DCIM")) {
                            val f = File(base, name)
                            if (f.isDirectory) {
                                AssistChip(onClick = { current = f }, label = { Text(name) })
                            }
                        }
                    }
                    Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                        if (current == null) {
                            LazyColumn {
                                items(roots, key = { it.second.absolutePath }) { (label, f) ->
                                    PickerRow(Icons.Filled.SdStorage, label, f.absolutePath) { current = f }
                                }
                            }
                        } else if (loading) {
                            CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
                        } else if (entries.isEmpty()) {
                            Text(
                                "Nenhuma subpasta nem arquivo .pkg aqui. Você ainda pode usar esta pasta.",
                                color = TextSecondary, style = MaterialTheme.typography.bodyMedium,
                                modifier = Modifier.align(Alignment.Center).padding(24.dp),
                            )
                        } else {
                            LazyColumn {
                                items(entries, key = { it.absolutePath }) { f ->
                                    if (f.isDirectory) {
                                        PickerRow(Icons.Filled.Folder, f.name, null) { current = f }
                                    } else {
                                        PickerRow(Icons.Filled.VideogameAsset, f.name, "${formatSize(f.length())} • toque para importar") {
                                            vm.importPkg(android.net.Uri.fromFile(f))
                                            onDismiss()
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth().padding(top = 8.dp)) {
                    OutlinedButton(onClick = onDismiss, modifier = Modifier.weight(1f)) { Text("Cancelar") }
                    Button(
                        onClick = {
                            current?.let { vm.addLocalFolder(it) }
                            onDismiss()
                        },
                        enabled = current != null && hasPermission,
                        modifier = Modifier.weight(1f),
                    ) { Text("Usar esta pasta") }
                }
            }
        }
    }
}

@Composable
private fun PickerRow(icon: androidx.compose.ui.graphics.vector.ImageVector, title: String, sub: String?, onClick: () -> Unit) {
    Column {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 12.dp, horizontal = 4.dp),
        ) {
            Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(26.dp))
            Column(modifier = Modifier.weight(1f).padding(start = 14.dp)) {
                Text(title, maxLines = 1, overflow = TextOverflow.Ellipsis)
                if (sub != null) Text(sub, style = MaterialTheme.typography.bodySmall, color = TextSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            Icon(Icons.Filled.ChevronRight, contentDescription = null, tint = TextSecondary)
        }
        HorizontalDivider(color = OutlineDim)
    }
}
