@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)

package com.dpireborn.app.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.VideogameAsset
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.dpireborn.app.AppViewModel
import com.dpireborn.app.data.FileItem
import com.dpireborn.app.data.LibraryFolder
import com.dpireborn.app.data.PkgInfo
import com.dpireborn.app.data.StoragePermission
import com.dpireborn.app.ui.theme.OutlineDim
import com.dpireborn.app.ui.theme.SurfaceDark
import com.dpireborn.app.ui.theme.SurfaceHigh
import com.dpireborn.app.ui.theme.TextSecondary

fun categoryLabel(info: PkgInfo?): String {
    val c = info?.category ?: return "PKG"
    return when (c) {
        "gd" -> "Jogo"
        "gp" -> "Atualização"
        "ac" -> "DLC"
        else -> c.uppercase()
    }
}

@Composable
fun LibraryScreen(vm: AppViewModel, onGoTransfers: () -> Unit) {
    val folders by vm.folders.collectAsStateWithLifecycle()
    val files by vm.files.collectAsStateWithLifecycle()
    val infos by vm.pkgInfo.collectAsStateWithLifecycle()
    val scanning by vm.scanning.collectAsStateWithLifecycle()
    val grid by vm.gridView.collectAsStateWithLifecycle()
    var query by rememberSaveable { mutableStateOf("") }
    var detail by remember { mutableStateOf<FileItem?>(null) }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        if (uri != null) vm.addFolder(uri)
    }

    // "Todo o celular": o Android 11+ bloqueia a pasta Download no seletor de pastas, então usamos
    // a permissão "Acesso a todos os arquivos" (o usuário liga na tela de ajustes do Android).
    val ctx = LocalContext.current
    val settingsLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        vm.addAllStorage()
    }
    val legacyLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        vm.addAllStorage()
    }
    val scanAll: () -> Unit = {
        if (StoragePermission.hasAllFiles(ctx)) {
            vm.addAllStorage()
        } else if (android.os.Build.VERSION.SDK_INT >= 30) {
            settingsLauncher.launch(StoragePermission.settingsIntent(ctx))
        } else {
            legacyLauncher.launch(
                arrayOf(
                    android.Manifest.permission.READ_EXTERNAL_STORAGE,
                    android.Manifest.permission.WRITE_EXTERNAL_STORAGE,
                )
            )
        }
    }

    val pkgs = remember(files, infos, query) {
        val q = query.trim().lowercase()
        files.filter { it.isPkg }
            .filter { f ->
                q.isEmpty() ||
                    f.name.lowercase().contains(q) ||
                    (infos[f.uri]?.title ?: "").lowercase().contains(q) ||
                    (infos[f.uri]?.titleId ?: "").lowercase().contains(q)
            }
            .sortedBy { (infos[it.uri]?.title ?: it.name).lowercase() }
    }
    val totalSize = remember(files) { files.filter { it.isPkg }.sumOf { it.size } }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp)
    ) {
        ScreenHeader(
            title = "Biblioteca",
            subtitle = if (folders.isEmpty()) "Escolha uma pasta com seus PKG"
            else "${files.count { it.isPkg }} PKG • ${formatSize(totalSize)}",
        ) {
            IconButton(onClick = { vm.rescan() }) {
                Icon(Icons.Filled.Refresh, contentDescription = "Atualizar", tint = MaterialTheme.colorScheme.primary)
            }
            FilledTonalButton(onClick = { picker.launch(null) }) {
                Icon(Icons.Filled.FolderOpen, contentDescription = null, modifier = Modifier.size(18.dp))
                Text("  Pasta")
            }
        }

        if (folders.isNotEmpty()) {
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.padding(bottom = 8.dp),
            ) {
                items(folders, key = { it.uri }) { f -> FolderChip(f) { vm.removeFolder(f) } }
                if (folders.none { it.uri == StoragePermission.ALL_STORAGE_URI }) {
                    item {
                        AssistChip(
                            onClick = scanAll,
                            label = { Text("Todo o celular") },
                            leadingIcon = {
                                Icon(Icons.Filled.Search, contentDescription = null, modifier = Modifier.size(16.dp))
                            },
                        )
                    }
                }
            }
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                singleLine = true,
                placeholder = { Text("Buscar por nome ou ID do título") },
                leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                trailingIcon = {
                    if (query.isNotEmpty()) {
                        IconButton(onClick = { query = "" }) {
                            Icon(Icons.Filled.Close, contentDescription = "Limpar")
                        }
                    }
                },
                shape = RoundedCornerShape(14.dp),
                colors = fieldColors(),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 8.dp),
            )
        }

        if (scanning) {
            LinearProgressIndicator(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 8.dp),
                color = MaterialTheme.colorScheme.primary,
                trackColor = OutlineDim,
            )
        }

        when {
            folders.isEmpty() -> EmptyState(
                icon = Icons.Filled.VideogameAsset,
                title = "Sua biblioteca está vazia",
                text = "Procure os PKG em todo o celular (inclusive na pasta Download) ou escolha uma pasta. " +
                    "O seletor de pastas do Android bloqueia a pasta Download e a raiz do armazenamento; " +
                    "para isso existe a busca em todo o celular, que pede a permissão \"Acesso a todos os arquivos\".",
                action = {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Button(onClick = scanAll) { Text("Buscar PKG em todo o celular") }
                        FilledTonalButton(onClick = { picker.launch(null) }) { Text("Escolher uma pasta") }
                    }
                },
            )

            pkgs.isEmpty() && !scanning -> EmptyState(
                icon = Icons.Filled.Search,
                title = if (query.isEmpty()) "Nenhum PKG encontrado" else "Nada encontrado",
                text = if (query.isEmpty()) "As pastas escolhidas não têm arquivos .pkg." else "Tente outro termo de busca.",
            )

            grid -> LazyVerticalGrid(
                columns = GridCells.Adaptive(150.dp),
                contentPadding = PaddingValues(bottom = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.fillMaxSize(),
            ) {
                items(pkgs, key = { it.uri }) { item ->
                    PkgCard(item, infos[item.uri]) { detail = item }
                }
            }

            else -> LazyColumn(
                contentPadding = PaddingValues(bottom = 16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.fillMaxSize(),
            ) {
                items(pkgs, key = { it.uri }) { item ->
                    PkgRow(item, infos[item.uri]) { detail = item }
                }
            }
        }
    }

    val current = detail
    if (current != null) {
        PkgDetailDialog(
            item = current,
            info = infos[current.uri],
            onDismiss = { detail = null },
            onSend = {
                vm.sendFiles(listOf(current))
                detail = null
                onGoTransfers()
            },
        )
    }
}

@Composable
private fun FolderChip(folder: LibraryFolder, onRemove: () -> Unit) {
    Surface(shape = RoundedCornerShape(50), color = SurfaceHigh) {
        Row(
            modifier = Modifier.padding(start = 12.dp, end = 6.dp, top = 6.dp, bottom = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                Icons.Filled.FolderOpen,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(16.dp),
            )
            Text(
                text = "  ${folder.name}  ",
                style = MaterialTheme.typography.labelLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Icon(
                Icons.Filled.Close,
                contentDescription = "Remover pasta",
                tint = TextSecondary,
                modifier = Modifier
                    .size(20.dp)
                    .clip(RoundedCornerShape(50))
                    .clickable { onRemove() },
            )
        }
    }
}

@Composable
private fun PkgCard(item: FileItem, info: PkgInfo?, onClick: () -> Unit) {
    val shape = RoundedCornerShape(16.dp)
    Card(
        modifier = Modifier
            .clip(shape)
            .clickable { onClick() },
        shape = shape,
        colors = CardDefaults.cardColors(containerColor = SurfaceDark),
        border = androidx.compose.foundation.BorderStroke(1.dp, OutlineDim),
    ) {
        Column {
            PkgCover(
                iconPath = info?.iconPath,
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(1f),
            )
            Column(modifier = Modifier.padding(10.dp)) {
                Text(
                    text = info?.title ?: item.name.removeSuffix(".pkg"),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = "${formatSize(item.size)} • ${categoryLabel(info)}",
                    style = MaterialTheme.typography.labelSmall,
                    color = TextSecondary,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }
        }
    }
}

@Composable
private fun PkgRow(item: FileItem, info: PkgInfo?, onClick: () -> Unit) {
    val shape = RoundedCornerShape(14.dp)
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .clickable { onClick() },
        shape = shape,
        colors = CardDefaults.cardColors(containerColor = SurfaceDark),
        border = androidx.compose.foundation.BorderStroke(1.dp, OutlineDim),
    ) {
        Row(
            modifier = Modifier.padding(10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            PkgCover(
                iconPath = info?.iconPath,
                modifier = Modifier
                    .size(64.dp)
                    .clip(RoundedCornerShape(10.dp)),
            )
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(start = 12.dp)
            ) {
                Text(
                    text = info?.title ?: item.name.removeSuffix(".pkg"),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = listOfNotNull(
                        formatSize(item.size),
                        categoryLabel(info),
                        info?.appVersion?.let { "v$it" },
                        info?.titleId,
                    ).joinToString(" • "),
                    style = MaterialTheme.typography.labelSmall,
                    color = TextSecondary,
                    modifier = Modifier.padding(top = 4.dp),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
private fun PkgDetailDialog(item: FileItem, info: PkgInfo?, onDismiss: () -> Unit, onSend: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = SurfaceHigh,
        title = {
            Text(
                text = info?.title ?: item.name.removeSuffix(".pkg"),
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                PkgCover(
                    iconPath = info?.iconPath,
                    modifier = Modifier
                        .size(120.dp)
                        .clip(RoundedCornerShape(14.dp)),
                )
                DetailLine("Arquivo", item.name)
                DetailLine("Tamanho", "${formatSize(item.size)} (${item.size} bytes)")
                DetailLine("Tipo", categoryLabel(info))
                if (info == null) {
                    DetailLine("Metadados", "Lendo…")
                } else if (!info.recognized) {
                    DetailLine("Metadados", "Formato não reconhecido (pode não ser um PKG de PS4)")
                } else {
                    info.titleId?.let { DetailLine("ID do título", it) }
                    info.contentId?.let { DetailLine("Content ID", it) }
                    info.appVersion?.let { DetailLine("Versão", it) }
                }
                DetailLine("Modificado", formatDate(item.modified))
            }
        },
        confirmButton = {
            Button(onClick = onSend) {
                Icon(Icons.Filled.CloudUpload, contentDescription = null, modifier = Modifier.size(18.dp))
                Text("  Enviar ao PS4")
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Fechar") } },
    )
}

@Composable
private fun DetailLine(label: String, value: String) {
    Row(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = TextSecondary,
            modifier = Modifier.width(96.dp),
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodySmall.copy(fontSize = 13.sp),
            modifier = Modifier.weight(1f),
        )
    }
}
