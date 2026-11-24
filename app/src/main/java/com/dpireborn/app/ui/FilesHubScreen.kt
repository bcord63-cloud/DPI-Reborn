package com.dpireborn.app.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FileUpload
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.dpireborn.app.AppViewModel
import com.dpireborn.app.ui.theme.DangerRed

/** Arquivos: selecionar pasta, importar PKG e atualizar (seletor do próprio app) + gerenciador de arquivos. */
@Composable
fun FilesHubScreen(vm: AppViewModel, onGoTransfers: () -> Unit) {
    val scanning by vm.scanning.collectAsStateWithLifecycle()
    val problems by vm.folderProblems.collectAsStateWithLifecycle()
    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.padding(start = 16.dp, end = 8.dp, top = 8.dp),
        ) {
            FilledTonalButton(onClick = { vm.showPicker.value = true }) {
                Icon(Icons.Filled.FolderOpen, contentDescription = null, modifier = Modifier.size(18.dp))
                Text("  Selecionar pasta")
            }
            FilledTonalButton(onClick = { vm.showPicker.value = true }) {
                Icon(Icons.Filled.FileUpload, contentDescription = null, modifier = Modifier.size(18.dp))
                Text("  Importar PKG")
            }
            Box(modifier = Modifier.weight(1f))
            IconButton(onClick = { vm.rescan() }) {
                Icon(Icons.Filled.Refresh, contentDescription = "Atualizar", tint = MaterialTheme.colorScheme.primary)
            }
        }
        if (scanning) LinearProgressIndicator(modifier = Modifier.fillMaxWidth().padding(top = 4.dp))
        problems.values.forEach { msg ->
            Card(
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = DangerRed.copy(alpha = 0.08f)),
                border = BorderStroke(1.dp, DangerRed.copy(alpha = 0.4f)),
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
            ) {
                Row(modifier = Modifier.padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.Warning, contentDescription = null, tint = DangerRed, modifier = Modifier.size(18.dp))
                    Text("  $msg", style = MaterialTheme.typography.bodySmall)
                }
            }
        }
        Box(modifier = Modifier.weight(1f)) { ManagerScreen(vm, onGoTransfers) }
    }
}
