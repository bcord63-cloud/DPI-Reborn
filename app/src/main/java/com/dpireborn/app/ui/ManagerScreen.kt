@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)

package com.dpireborn.app.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.SelectAll
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.VideogameAsset
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.dpireborn.app.AppViewModel
import com.dpireborn.app.data.FileItem
import com.dpireborn.app.ui.theme.DangerRed
import com.dpireborn.app.ui.theme.OutlineDim
import com.dpireborn.app.ui.theme.SurfaceDark
import com.dpireborn.app.ui.theme.SurfaceHigh
import com.dpireborn.app.ui.theme.TextSecondary

@Composable
fun ManagerScreen(vm: AppViewModel, onGoTransfers: () -> Unit) {
    val files by vm.files.collectAsStateWithLifecycle()
    val selection by vm.selection.collectAsStateWithLifecycle()
    val scanning by vm.scanning.collectAsStateWithLifecycle()
    val folders by vm.folders.collectAsStateWithLifecycle()

    var query by rememberSaveable { mutableStateOf("") }
    var filter by rememberSaveable { mutableIntStateOf(0) }
    var sort by rememberSaveable { mutableIntStateOf(0) }
    var confirmDelete by remember { mutableStateOf(false) }
    var moveDialog by remember { mutableStateOf(false) }
    var folderName by rememberSaveable { mutableStateOf("") }

    val shown = remember(files, query, filter, sort) {
        val q = query.trim().lowercase()
        val list = files.filter { f ->
            (q.isEmpty() || f.name.lowercase().contains(q)) &&
                when (filter) {
                    1 -> f.isPkg
                    2 -> !f.isPkg
                    else -> true
                }
        }
        when (sort) {
            1 -> list.sortedByDescending { it.size }
            2 -> list.sortedByDescending { it.modified }
            else -> list.sortedBy { it.name.lowercase() }
        }
    }
    val selectedItems = remember(files, selection) { files.filter { it.uri in selection } }
    val selectedSize = remember(selectedItems) { selectedItems.sumOf { it.size } }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp)
    ) {
        ScreenHeader(
            title = "Gerenciador",
            subtitle = if (folders.isEmpty()) "Adicione uma pasta na Biblioteca"
            else "${files.size} arquivo(s) nas pastas da biblioteca",
        )

        if (folders.isEmpty()) {
            EmptyState(
                icon = Icons.Filled.FolderOpen,
                title = "Nenhuma pasta selecionada",
                text = "Os arquivos aparecem aqui depois que você escolher uma pasta na aba Biblioteca.",
            )
            return@Column
        }

        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            singleLine = true,
            placeholder = { Text("Pesquisar arquivos") },
            leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
            trailingIcon = {
                if (query.isNotEmpty()) {
                    IconButton(onClick = { query = "" }) { Icon(Icons.Filled.Close, contentDescription = "Limpar") }
                }
            },
            shape = RoundedCornerShape(14.dp),
            colors = fieldColors(),
            modifier = Modifier.fillMaxWidth(),
        )

        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.padding(vertical = 8.dp),
        ) {
            val filterLabels = listOf("Todos", "PKG", "Outros")
            items(filterLabels.size) { i ->
                FilterChip(
                    selected = filter == i,
                    onClick = { filter = i },
                    label = { Text(filterLabels[i]) },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.35f),
                        selectedLabelColor = MaterialTheme.colorScheme.onBackground,
                    ),
                )
            }
            val sortLabels = listOf("Nome", "Tamanho", "Data")
            items(sortLabels.size) { i ->
                FilterChip(
                    selected = sort == i,
                    onClick = { sort = i },
                    label = { Text("Ordem: ${sortLabels[i]}") },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.35f),
                        selectedLabelColor = MaterialTheme.colorScheme.onBackground,
                    ),
                )
            }
        }

        AnimatedVisibility(
            visible = selection.isNotEmpty(),
            enter = expandVertically() + fadeIn(),
            exit = shrinkVertically() + fadeOut(),
        ) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 8.dp),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = SurfaceHigh),
                border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.primary),
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = "${selection.size} selecionado(s) • ${formatSize(selectedSize)}",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.weight(1f),
                        )
                        IconButton(onClick = { vm.selectAll(shown.map { it.uri }) }) {
                            Icon(Icons.Filled.SelectAll, contentDescription = "Selecionar todos")
                        }
                        IconButton(onClick = { vm.clearSelection() }) {
                            Icon(Icons.Filled.Close, contentDescription = "Limpar seleção")
                        }
                    }
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.padding(top = 4.dp),
                    ) {
                        if (selectedItems.any { it.isPkg }) {
                            Button(onClick = {
                                vm.sendFiles(selectedItems)
                                vm.clearSelection()
                                onGoTransfers()
                            }) {
                                Icon(Icons.Filled.CloudUpload, contentDescription = null, modifier = Modifier.size(18.dp))
                                Text("  Enviar")
                            }
                        }
                        Button(
                            onClick = { moveDialog = true },
                            colors = ButtonDefaults.buttonColors(containerColor = OutlineDim),
                        ) {
                            Icon(Icons.Filled.Folder, contentDescription = null, modifier = Modifier.size(18.dp))
                            Text("  Mover")
                        }
                        Button(
                            onClick = { confirmDelete = true },
                            colors = ButtonDefaults.buttonColors(containerColor = DangerRed.copy(alpha = 0.85f)),
                        ) {
                            Icon(Icons.Filled.Delete, contentDescription = null, modifier = Modifier.size(18.dp))
                            Text("  Excluir")
                        }
                    }
                }
            }
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

        if (shown.isEmpty() && !scanning) {
            EmptyState(
                icon = Icons.Filled.Search,
                title = "Nenhum arquivo",
                text = "Nada corresponde à busca ou ao filtro atual.",
            )
        } else {
            LazyColumn(
                contentPadding = PaddingValues(bottom = 16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxSize(),
            ) {
                items(shown, key = { it.uri }) { item ->
                    FileRow(item, item.uri in selection) { vm.toggleSelection(item.uri) }
                }
            }
        }
    }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            containerColor = SurfaceHigh,
            title = { Text("Excluir ${selectedItems.size} arquivo(s)?") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        text = "Esta ação apaga os arquivos do armazenamento (${formatSize(selectedSize)}) e " +
                            "não pode ser desfeita.",
                        color = DangerRed,
                    )
                    selectedItems.take(5).forEach {
                        Text(
                            text = "• ${it.name}",
                            style = MaterialTheme.typography.bodySmall,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    if (selectedItems.size > 5) {
                        Text("… e mais ${selectedItems.size - 5}", style = MaterialTheme.typography.bodySmall)
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        confirmDelete = false
                        vm.deleteSelected()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = DangerRed),
                ) { Text("Excluir") }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Cancelar") } },
        )
    }

    if (moveDialog) {
        AlertDialog(
            onDismissRequest = { moveDialog = false },
            containerColor = SurfaceHigh,
            title = { Text("Mover ${selectedItems.size} arquivo(s)") },
            text = {
                Column {
                    Text(
                        text = "Informe o nome da subpasta (será criada na pasta raiz da biblioteca, se não existir).",
                        style = MaterialTheme.typography.bodySmall,
                        color = TextSecondary,
                    )
                    OutlinedTextField(
                        value = folderName,
                        onValueChange = { folderName = it.take(40) },
                        label = { Text("Nome da pasta") },
                        singleLine = true,
                        shape = RoundedCornerShape(14.dp),
                        colors = fieldColors(),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 10.dp),
                    )
                }
            },
            confirmButton = {
                Button(onClick = {
                    moveDialog = false
                    vm.moveSelected(folderName)
                }) { Text("Mover") }
            },
            dismissButton = { TextButton(onClick = { moveDialog = false }) { Text("Cancelar") } },
        )
    }
}

@Composable
private fun FileRow(item: FileItem, selected: Boolean, onToggle: () -> Unit) {
    val shape = RoundedCornerShape(12.dp)
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .clickable { onToggle() },
        shape = shape,
        colors = CardDefaults.cardColors(
            containerColor = if (selected) MaterialTheme.colorScheme.primary.copy(alpha = 0.18f) else SurfaceDark
        ),
        border = androidx.compose.foundation.BorderStroke(
            1.dp,
            if (selected) MaterialTheme.colorScheme.primary else OutlineDim,
        ),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Checkbox(
                checked = selected,
                onCheckedChange = { onToggle() },
                colors = CheckboxDefaults.colors(checkedColor = MaterialTheme.colorScheme.primary),
            )
            Icon(
                imageVector = if (item.isPkg) Icons.Filled.VideogameAsset else Icons.Filled.Folder,
                contentDescription = null,
                tint = if (item.isPkg) MaterialTheme.colorScheme.primary else TextSecondary,
                modifier = Modifier.padding(end = 10.dp),
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = item.name,
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = "${formatSize(item.size)} • ${formatDate(item.modified)}",
                    style = MaterialTheme.typography.labelSmall,
                    color = TextSecondary,
                )
            }
        }
    }
}
