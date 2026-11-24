package com.dpireborn.app.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.PhoneIphone
import androidx.compose.material.icons.filled.SportsEsports
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.dpireborn.app.AppViewModel
import com.dpireborn.app.R
import com.dpireborn.app.catalog.CatalogViewModel
import com.dpireborn.app.data.methodLabel
import com.dpireborn.app.ui.theme.OutlineDim
import com.dpireborn.app.ui.theme.SurfaceDark
import com.dpireborn.app.ui.theme.SurfaceHigh
import com.dpireborn.app.ui.theme.TextSecondary

/** Início: painel + (Console e Envios, que continuam funcionando como antes) em abas internas. */
@Composable
fun HomeScreen(
    vm: AppViewModel,
    cvm: CatalogViewModel,
    sub: Int,
    onSub: (Int) -> Unit,
    goApps: () -> Unit,
    goFiles: () -> Unit,
) {
    val chipColors = FilterChipDefaults.filterChipColors(
        selectedContainerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.35f),
        selectedLabelColor = Color.White,
        labelColor = TextSecondary,
    )
    Column(modifier = Modifier.fillMaxSize()) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 8.dp)) {
            listOf("Painel", "Console", "Envios").forEachIndexed { i, label ->
                FilterChip(selected = sub == i, onClick = { onSub(i) }, label = { Text(label) }, colors = chipColors)
            }
        }
        Box(modifier = Modifier.weight(1f)) {
            when (sub) {
                1 -> ConsoleScreen(vm)
                2 -> TransfersScreen(vm)
                else -> Dashboard(vm, cvm, onSub, goApps, goFiles)
            }
        }
    }
}

@Composable
private fun Dashboard(vm: AppViewModel, cvm: CatalogViewModel, onSub: (Int) -> Unit, goApps: () -> Unit, goFiles: () -> Unit) {
    val files by vm.files.collectAsStateWithLifecycle()
    val active by vm.active.collectAsStateWithLifecycle()
    val consoles by vm.consoles.collectAsStateWithLifecycle()
    val selId by vm.selectedConsoleId.collectAsStateWithLifecycle()
    val entries by cvm.entries.collectAsStateWithLifecycle()
    val favorites by cvm.favorites.collectAsStateWithLifecycle()
    val recents by cvm.recents.collectAsStateWithLifecycle()
    val console = consoles.firstOrNull { it.id == selId }
    val favEntries = entries.filter { it.app.id in favorites }
    val recentEntries = recents.mapNotNull { id -> entries.firstOrNull { it.app.id == id } }.take(5)

    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        contentPadding = PaddingValues(bottom = 24.dp),
    ) {
        item { ScreenHeader(title = "DPI Reborn", subtitle = "Organize PKG, envie ao PS4 e descubra apps") }
        item { IosNoticeCard() }
        item {
            SectionCard {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.SportsEsports, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    Column(modifier = Modifier.weight(1f).padding(start = 12.dp)) {
                        Text(console?.name ?: "Nenhum console selecionado", fontWeight = FontWeight.SemiBold)
                        Text(
                            if (console != null) "${console.ip} • ${methodLabel(console.method)}" else "Cadastre o IP do seu PS4 para enviar PKG",
                            style = MaterialTheme.typography.bodySmall, color = TextSecondary,
                        )
                    }
                    FilledTonalButton(onClick = { onSub(1) }) { Text(if (console == null) "Cadastrar" else "Console") }
                }
            }
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                StatCard("PKG no celular", files.count { it.isPkg }.toString(), Modifier.weight(1f)) { goFiles() }
                StatCard("Envios ativos", active.size.toString(), Modifier.weight(1f)) { onSub(2) }
                StatCard("Favoritos", favEntries.size.toString(), Modifier.weight(1f)) {
                    cvm.filter.value = "Favoritos"
                    goApps()
                }
            }
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                Button(onClick = goFiles, modifier = Modifier.weight(1f)) {
                    Icon(Icons.Filled.FolderOpen, contentDescription = null, modifier = Modifier.size(18.dp))
                    Text("  Meus PKG")
                }
                OutlinedButton(onClick = goApps, modifier = Modifier.weight(1f)) {
                    Icon(Icons.Filled.Apps, contentDescription = null, modifier = Modifier.size(18.dp))
                    Text("  Explorar apps")
                }
            }
        }
        if (favEntries.isNotEmpty()) {
            item { Text("Favoritos", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold) }
            items(favEntries.take(5), key = { "f-" + it.app.id }) { e ->
                MiniAppRow(e) { cvm.open(e.app.id); goApps() }
            }
        }
        if (recentEntries.isNotEmpty()) {
            item { Text("Vistos recentemente", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold) }
            items(recentEntries, key = { "r-" + it.app.id }) { e ->
                MiniAppRow(e) { cvm.open(e.app.id); goApps() }
            }
        }
    }
}

@Composable
private fun MiniAppRow(e: com.dpireborn.app.catalog.CatalogEntry, onClick: () -> Unit) {
    Card(
        onClick = onClick,
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = SurfaceDark),
        border = BorderStroke(1.dp, OutlineDim),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            AppIcon(e.app, 40.dp)
            Column(modifier = Modifier.weight(1f).padding(start = 12.dp)) {
                Text(e.app.name, fontWeight = FontWeight.SemiBold, maxLines = 1)
                Text(e.app.category, style = MaterialTheme.typography.bodySmall, color = TextSecondary, maxLines = 1)
            }
            StatusChip(e)
        }
    }
}

@Composable
private fun StatCard(label: String, value: String, modifier: Modifier, onClick: () -> Unit) {
    Card(
        onClick = onClick,
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = SurfaceHigh),
        border = BorderStroke(1.dp, OutlineDim),
        modifier = modifier,
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(value, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
            Text(label, style = MaterialTheme.typography.labelSmall, color = TextSecondary, maxLines = 1)
        }
    }
}

/** Aviso de novidade sobre a futura versão para iPhone. Texto exato pedido, vindo de strings.xml. */
@Composable
fun IosNoticeCard() {
    val accent = MaterialTheme.colorScheme.primary
    Card(
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = SurfaceDark),
        border = BorderStroke(1.dp, Brush.linearGradient(listOf(accent, Color(0xFF4C1D95)))),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier
                .background(Brush.horizontalGradient(listOf(accent.copy(alpha = 0.20f), Color.Transparent)))
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(46.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(Brush.linearGradient(listOf(accent, Color(0xFF2E1065)))),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Filled.PhoneIphone, contentDescription = null, tint = Color.White)
            }
            Column(modifier = Modifier.padding(start = 14.dp)) {
                Text("NOVIDADE", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = accent, letterSpacing = 1.2.sp)
                Text(stringResource(R.string.ios_notice), style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(top = 2.dp))
            }
        }
    }
}
