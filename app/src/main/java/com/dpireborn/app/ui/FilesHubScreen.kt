package com.dpireborn.app.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.dpireborn.app.AppViewModel
import com.dpireborn.app.ui.theme.DangerRed
import com.dpireborn.app.ui.theme.TextSecondary

/** Arquivos: biblioteca de PKG (SAF + todo o celular), importar PKG avulso e o gerenciador, sem mudar o envio por payload. */
@Composable
fun FilesHubScreen(vm: AppViewModel, onGoTransfers: () -> Unit) {
    var sub by rememberSaveable { mutableIntStateOf(0) }
    val scanning by vm.scanning.collectAsStateWithLifecycle()
    val problems by vm.folderProblems.collectAsStateWithLifecycle()
    val importer = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) vm.importPkg(uri)
    }
    val chipColors = FilterChipDefaults.filterChipColors(
        selectedContainerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.35f),
        selectedLabelColor = Color.White,
        labelColor = TextSecondary,
    )
    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 8.dp),
        ) {
            FilterChip(selected = sub == 0, onClick = { sub = 0 }, label = { Text("PKGs") }, colors = chipColors)
            FilterChip(selected = sub == 1, onClick = { sub = 1 }, label = { Text("Gerenciador") }, colors = chipColors)
            Box(modifier = Modifier.weight(1f))
            FilledTonalButton(onClick = { importer.launch(arrayOf("*/*")) }) {
                Icon(Icons.Filled.FileUpload, contentDescription = null, modifier = Modifier.size(18.dp))
                Text("  Importar PKG")
            }
        }
        if (scanning) LinearProgressIndicator(modifier = Modifier.fillMaxWidth().padding(top = 6.dp))
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
        Box(modifier = Modifier.weight(1f)) {
            if (sub == 0) LibraryScreen(vm, onGoTransfers) else ManagerScreen(vm, onGoTransfers)
        }
    }
}
