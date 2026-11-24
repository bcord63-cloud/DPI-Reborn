package com.dpireborn.app.ui

import android.app.DownloadManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Extension
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.SportsEsports
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material.icons.filled.VideogameAsset
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.dpireborn.app.AppViewModel
import com.dpireborn.app.catalog.CatalogApp
import com.dpireborn.app.catalog.CatalogEntry
import com.dpireborn.app.catalog.CatalogFilters
import com.dpireborn.app.catalog.CatalogSorts
import com.dpireborn.app.catalog.CatalogViewModel
import com.dpireborn.app.catalog.DlStatus
import com.dpireborn.app.catalog.DownloadRecord
import com.dpireborn.app.catalog.SourceStatus
import com.dpireborn.app.catalog.UrlGuard
import com.dpireborn.app.ui.theme.DangerRed
import com.dpireborn.app.ui.theme.OutlineDim
import com.dpireborn.app.ui.theme.SuccessGreen
import com.dpireborn.app.ui.theme.SurfaceDark
import com.dpireborn.app.ui.theme.SurfaceHigh
import com.dpireborn.app.ui.theme.TextSecondary
import com.dpireborn.app.ui.theme.WarnAmber

private val categoryOrder = listOf(
    "Ferramentas para PS4", "Homebrew", "Emuladores", "Streaming", "Utilitários",
    "Multimídia", "Navegadores", "Aplicativos diversos",
)

fun openUrl(ctx: Context, url: String): Boolean {
    if (!UrlGuard.isSafeHttps(url)) return false
    return try {
        ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        true
    } catch (e: Exception) {
        false
    }
}

private fun categoryIcon(c: String): ImageVector = when (c) {
    "Streaming" -> Icons.Filled.PlayCircle
    "Emuladores" -> Icons.Filled.SportsEsports
    "Homebrew" -> Icons.Filled.Extension
    "Utilitários" -> Icons.Filled.Build
    "Multimídia" -> Icons.Filled.MusicNote
    "Navegadores" -> Icons.Filled.Language
    "Ferramentas para PS4" -> Icons.Filled.VideogameAsset
    else -> Icons.Filled.Apps
}

@Composable
private fun chipColors() = FilterChipDefaults.filterChipColors(
    selectedContainerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.35f),
    selectedLabelColor = Color.White,
    labelColor = TextSecondary,
)

@Composable
fun AppsScreen(cvm: CatalogViewModel, vm: AppViewModel) {
    val selected by cvm.selectedId.collectAsStateWithLifecycle()
    val showDl by cvm.showDownloads.collectAsStateWithLifecycle()
    val entries by cvm.entries.collectAsStateWithLifecycle()
    LaunchedEffect(Unit) { cvm.ensureFresh() }
    val sel = entries.firstOrNull { it.app.id == selected }
    if (showDl) {
        BackHandler { cvm.showDownloads.value = false }
        DownloadsScreen(cvm, vm)
    } else if (sel != null) {
        BackHandler { cvm.close() }
        AppDetailScreen(sel, cvm, vm)
    } else {
        CatalogList(cvm)
    }
}

@Composable
private fun CatalogList(cvm: CatalogViewModel) {
    val visible by cvm.visible.collectAsStateWithLifecycle()
    val entries by cvm.entries.collectAsStateWithLifecycle()
    val loading by cvm.loading.collectAsStateWithLifecycle()
    val query by cvm.query.collectAsStateWithLifecycle()
    val filter by cvm.filter.collectAsStateWithLifecycle()
    val sort by cvm.sort.collectAsStateWithLifecycle()
    val favorites by cvm.favorites.collectAsStateWithLifecycle()
    val recents by cvm.recents.collectAsStateWithLifecycle()
    val note by cvm.sourceNote.collectAsStateWithLifecycle()
    val problem by cvm.problem.collectAsStateWithLifecycle()
    val downloads by cvm.downloads.collectAsStateWithLifecycle()
    val running = downloads.count { it.status == DlStatus.RUNNING }

    Column(modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
        ScreenHeader(title = "Apps", subtitle = "${entries.size} itens • fontes verificadas ao vivo") {
            IconButton(onClick = { cvm.showDownloads.value = true }) {
                BadgedBox(badge = { if (running > 0) Badge { Text(running.toString()) } }) {
                    Icon(Icons.Filled.Download, contentDescription = "Downloads", tint = MaterialTheme.colorScheme.primary)
                }
            }
            IconButton(onClick = { cvm.refresh() }, enabled = !loading) {
                if (loading) {
                    CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                } else {
                    Icon(Icons.Filled.Refresh, contentDescription = "Atualizar catálogo", tint = MaterialTheme.colorScheme.primary)
                }
            }
        }
        OutlinedTextField(
            value = query,
            onValueChange = { cvm.query.value = it },
            singleLine = true,
            placeholder = { Text("Nome, emulador, plataforma, categoria ou desenvolvedor") },
            leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
            trailingIcon = {
                if (query.isNotEmpty()) {
                    IconButton(onClick = { cvm.query.value = "" }) { Icon(Icons.Filled.Close, contentDescription = "Limpar") }
                }
            },
            colors = fieldColors(),
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier.fillMaxWidth(),
        )
        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 8.dp)) {
            items(CatalogFilters) { f ->
                FilterChip(selected = filter == f, onClick = { cvm.filter.value = f }, label = { Text(f) }, colors = chipColors())
            }
        }
        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(vertical = 4.dp)) {
            items(CatalogSorts) { (key, label) ->
                FilterChip(selected = sort == key, onClick = { cvm.sort.value = key }, label = { Text(label, fontSize = 12.sp) }, colors = chipColors())
            }
        }

        LazyColumn(
            verticalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier.fillMaxSize(),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(bottom = 24.dp, top = 4.dp),
        ) {
            item {
                Column {
                    Text(note, style = MaterialTheme.typography.labelSmall, color = TextSecondary)
                    problem?.let { Text(it, style = MaterialTheme.typography.labelSmall, color = WarnAmber) }
                }
            }
            if (query.isBlank() && recents.isNotEmpty()) {
                val recentEntries = recents.mapNotNull { id -> entries.firstOrNull { it.app.id == id } }
                if (recentEntries.isNotEmpty()) {
                    item {
                        Column {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text("Vistos recentemente", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                                TextButton(onClick = { cvm.clearRecents() }) { Text("Limpar") }
                            }
                            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                items(recentEntries, key = { it.app.id }) { e ->
                                    FilterChip(selected = false, onClick = { cvm.open(e.app.id) }, label = { Text(e.app.name) }, colors = chipColors())
                                }
                            }
                        }
                    }
                }
            }
            if (visible.isEmpty()) {
                item {
                    Box(modifier = Modifier.fillMaxWidth().height(220.dp)) {
                        EmptyState(
                            icon = Icons.Filled.Search,
                            title = if (filter == "Favoritos") "Nenhum favorito ainda" else "Nada encontrado",
                            text = if (filter == "Favoritos") "Toque na estrela de um app para guardá-lo aqui." else "Tente outro termo ou filtro.",
                        )
                    }
                }
            } else if (query.isBlank()) {
                val groups = visible.groupBy { it.app.category }
                val ordered = categoryOrder.filter { it in groups } + groups.keys.filter { it !in categoryOrder }
                for (cat in ordered) {
                    item(key = "h-$cat") {
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 6.dp)) {
                            Icon(categoryIcon(cat), contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
                            Text("  $cat", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                        }
                    }
                    items(groups[cat].orEmpty(), key = { it.app.id }) { e ->
                        AppCard(e, e.app.id in favorites, { cvm.toggleFavorite(e.app.id) }, { cvm.open(e.app.id) })
                    }
                }
            } else {
                items(visible, key = { it.app.id }) { e ->
                    AppCard(e, e.app.id in favorites, { cvm.toggleFavorite(e.app.id) }, { cvm.open(e.app.id) })
                }
            }
        }
    }
}

@Composable
fun AppIcon(app: CatalogApp, size: androidx.compose.ui.unit.Dp) {
    Box(
        modifier = Modifier
            .size(size)
            .clip(RoundedCornerShape(size / 3.5f))
            .background(Brush.linearGradient(listOf(MaterialTheme.colorScheme.primary, Color(0xFF2E1065)))),
        contentAlignment = Alignment.Center,
    ) {
        Icon(categoryIcon(app.category), contentDescription = null, tint = Color.White.copy(alpha = 0.9f), modifier = Modifier.size(size / 2))
    }
}

@Composable
fun StatusChip(e: CatalogEntry) {
    val (text, color) = when (e.status) {
        SourceStatus.DOWNLOAD -> "Arquivo verificado" to SuccessGreen
        SourceStatus.LINK -> "Fonte online" to SuccessGreen
        SourceStatus.UNCHECKED -> "Não verificado" to TextSecondary
        SourceStatus.UNAVAILABLE -> "Fonte indisponível" to DangerRed
    }
    Text(
        text, color = color, fontSize = 11.sp, fontWeight = FontWeight.Medium,
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .background(color.copy(alpha = 0.14f))
            .padding(horizontal = 8.dp, vertical = 2.dp),
    )
}

@Composable
private fun AppCard(e: CatalogEntry, fav: Boolean, onFav: () -> Unit, onClick: () -> Unit) {
    Card(
        onClick = onClick,
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = SurfaceDark),
        border = BorderStroke(1.dp, OutlineDim),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(modifier = Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            AppIcon(e.app, 52.dp)
            Column(modifier = Modifier.weight(1f).padding(horizontal = 12.dp)) {
                Text(e.app.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(
                    "${e.app.developer} • ${e.app.platforms.joinToString(", ")}",
                    style = MaterialTheme.typography.bodySmall, color = TextSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis,
                )
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 6.dp)) {
                    StatusChip(e)
                    e.release?.let {
                        Text("  ${it.version}", style = MaterialTheme.typography.labelSmall, color = TextSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
            }
            IconButton(onClick = onFav) {
                Icon(
                    if (fav) Icons.Filled.Star else Icons.Filled.StarBorder,
                    contentDescription = if (fav) "Remover dos favoritos" else "Favoritar",
                    tint = if (fav) WarnAmber else TextSecondary,
                )
            }
        }
    }
}

@Composable
private fun InfoRow(label: String, value: String) {
    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        Text(label, style = MaterialTheme.typography.bodySmall, color = TextSecondary, modifier = Modifier.width(110.dp))
        Text(value, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
    }
}

@Composable
fun ExternalSourceWarning() {
    Card(
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = WarnAmber.copy(alpha = 0.08f)),
        border = BorderStroke(1.dp, WarnAmber.copy(alpha = 0.4f)),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.Top) {
            Icon(Icons.Filled.Warning, contentDescription = null, tint = WarnAmber, modifier = Modifier.size(20.dp))
            Text(
                "  Fonte externa: o DPI Reborn não hospeda nem altera este arquivo. Baixe só o que você tem direito de usar. " +
                    "O arquivo nunca é aberto ou instalado automaticamente.",
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }
}

@Composable
private fun AppDetailScreen(e: CatalogEntry, cvm: CatalogViewModel, vm: AppViewModel) {
    val ctx = LocalContext.current
    val favorites by cvm.favorites.collectAsStateWithLifecycle()
    val downloads by cvm.downloads.collectAsStateWithLifecycle()
    val speeds by cvm.speeds.collectAsStateWithLifecycle()
    val rec = downloads.firstOrNull { it.appId == e.app.id }
    val r = e.release
    val fav = e.app.id in favorites

    Column(modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 8.dp)) {
            IconButton(onClick = { cvm.close() }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Voltar") }
            Spacer(Modifier.weight(1f))
            IconButton(onClick = { cvm.toggleFavorite(e.app.id) }) {
                Icon(
                    if (fav) Icons.Filled.Star else Icons.Filled.StarBorder,
                    contentDescription = if (fav) "Remover dos favoritos" else "Favoritar",
                    tint = if (fav) WarnAmber else TextSecondary,
                )
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            AppIcon(e.app, 72.dp)
            Column(modifier = Modifier.padding(start = 14.dp)) {
                Text(e.app.name, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                Text(e.app.developer, style = MaterialTheme.typography.bodyMedium, color = TextSecondary)
                Box(modifier = Modifier.padding(top = 6.dp)) { StatusChip(e) }
            }
        }
        if (e.app.description.isNotBlank()) {
            Text(e.app.description, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(top = 14.dp))
        }
        e.note?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = if (e.status == SourceStatus.UNAVAILABLE) DangerRed else WarnAmber, modifier = Modifier.padding(top = 8.dp)) }

        Spacer(Modifier.height(14.dp))
        SectionCard {
            InfoRow("Categoria", e.app.category)
            InfoRow("Plataforma", e.app.platforms.joinToString(", ").ifBlank { "Não informada" })
            InfoRow("Versão", r?.let { it.version + if (it.prerelease) " (pré-lançamento)" else "" } ?: "Veja na página oficial")
            InfoRow("Desenvolvedor", e.app.developer)
            if (r?.publisher != null) InfoRow("Publicado por", r.publisher)
            InfoRow("Fonte original", UrlGuard.host(e.app.sourceUrl))
            InfoRow("Atualização", if (r != null && r.publishedAt > 0) formatDate(r.publishedAt) else "Não disponível")
            InfoRow("Tamanho", if (r != null && r.size > 0) formatSize(r.size) else "Não disponível")
            if (r?.assetName != null) InfoRow("Arquivo", r.assetName)
            if (e.app.license != null) InfoRow("Licença", e.app.license)
            if (r?.downloadUrl != null) {
                InfoRow("SHA-256", r.sha256 ?: "A fonte não publicou hash; não é possível conferir")
                InfoRow("Link", r.downloadUrl)
            }
            InfoRow("Verificado em", if (e.checkedAt > 0) formatDate(e.checkedAt) else "Ainda não verificado")
        }

        Spacer(Modifier.height(12.dp))
        DownloadPanel(e, rec, speeds[rec?.id] ?: 0L, cvm, vm)

        Spacer(Modifier.height(10.dp))
        OutlinedButton(onClick = { if (!openUrl(ctx, e.app.sourceUrl)) cvm.messages.tryEmit("Não foi possível abrir o link") }, modifier = Modifier.fillMaxWidth()) {
            Icon(Icons.AutoMirrored.Filled.OpenInNew, contentDescription = null, modifier = Modifier.size(18.dp))
            Text("  Abrir página oficial")
        }
        Spacer(Modifier.height(10.dp))
        ExternalSourceWarning()
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun DownloadPanel(e: CatalogEntry, rec: DownloadRecord?, speed: Long, cvm: CatalogViewModel, vm: AppViewModel) {
    val ctx = LocalContext.current
    SectionCard {
        when {
            rec != null && rec.status == DlStatus.RUNNING -> {
                Text("Baixando…", fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(8.dp))
                if (rec.size > 0) {
                    LinearProgressIndicator(progress = { rec.progress }, modifier = Modifier.fillMaxWidth())
                } else {
                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                }
                Text(
                    "${formatSize(rec.received)} de ${if (rec.size > 0) formatSize(rec.size) else "tamanho desconhecido"}" +
                        " • ${formatSpeed(speed)} • ${rec.detail}",
                    style = MaterialTheme.typography.bodySmall, color = TextSecondary, modifier = Modifier.padding(top = 6.dp),
                )
                OutlinedButton(onClick = { cvm.cancelDownload(rec.id) }, modifier = Modifier.padding(top = 8.dp)) { Text("Cancelar download") }
            }
            rec != null && rec.status == DlStatus.DONE -> {
                Text("Download concluído", color = SuccessGreen, fontWeight = FontWeight.SemiBold)
                Text(rec.detail, style = MaterialTheme.typography.bodySmall, color = TextSecondary)
                Text("Salvo em: ${rec.path}", style = MaterialTheme.typography.bodySmall, color = TextSecondary, modifier = Modifier.padding(top = 4.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 8.dp)) {
                    if (rec.isPkg) {
                        Button(onClick = { vm.sendDownloaded(rec.path) }) {
                            Icon(Icons.Filled.Send, contentDescription = null, modifier = Modifier.size(18.dp))
                            Text("  Enviar ao PS4")
                        }
                    }
                    FilledTonalButton(onClick = { cvm.showDownloads.value = true }) { Text("Meus downloads") }
                }
            }
            else -> {
                if (rec != null) {
                    Text(
                        if (rec.status == DlStatus.CANCELLED) "Download cancelado" else "Falha: ${rec.detail}",
                        color = if (rec.status == DlStatus.CANCELLED) TextSecondary else DangerRed,
                        style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(bottom = 8.dp),
                    )
                }
                if (e.canDownload) {
                    Button(onClick = { cvm.startDownload(e) }, modifier = Modifier.fillMaxWidth()) {
                        Icon(Icons.Filled.Download, contentDescription = null, modifier = Modifier.size(18.dp))
                        Text("  Baixar ${e.release?.size?.takeIf { it > 0 }?.let { formatSize(it) } ?: ""}".trimEnd())
                    }
                } else {
                    Text(
                        if (e.status == SourceStatus.UNAVAILABLE) "A fonte não respondeu, então não há download agora."
                        else "Este item não tem um arquivo verificado para baixar dentro do app. Use a página oficial.",
                        style = MaterialTheme.typography.bodyMedium, color = TextSecondary,
                    )
                }
            }
        }
    }
}

@Composable
private fun DownloadsScreen(cvm: CatalogViewModel, vm: AppViewModel) {
    val ctx = LocalContext.current
    val downloads by cvm.downloads.collectAsStateWithLifecycle()
    val speeds by cvm.speeds.collectAsStateWithLifecycle()
    Column(modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 8.dp)) {
            IconButton(onClick = { cvm.showDownloads.value = false }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Voltar") }
            Text("Downloads", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
            if (downloads.any { it.status != DlStatus.RUNNING }) TextButton(onClick = { cvm.clearFinished() }) { Text("Limpar histórico") }
        }
        if (downloads.isEmpty()) {
            EmptyState(Icons.Filled.Download, "Sem downloads", "Os arquivos que você baixar pelo catálogo aparecem aqui.")
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp), contentPadding = androidx.compose.foundation.layout.PaddingValues(vertical = 8.dp)) {
                items(downloads, key = { it.id }) { d ->
                    SectionCard {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(d.appName, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Text(d.fileName, style = MaterialTheme.typography.bodySmall, color = TextSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                            IconButton(onClick = { cvm.removeRecord(d.id, false) }) { Icon(Icons.Filled.Close, contentDescription = "Remover do histórico") }
                        }
                        if (d.status == DlStatus.RUNNING) {
                            if (d.size > 0) LinearProgressIndicator(progress = { d.progress }, modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp))
                            else LinearProgressIndicator(modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp))
                            Text(
                                "${formatSize(d.received)} / ${if (d.size > 0) formatSize(d.size) else "?"} • ${formatSpeed(speeds[d.id] ?: 0L)} • ${d.detail}",
                                style = MaterialTheme.typography.bodySmall, color = TextSecondary,
                            )
                            OutlinedButton(onClick = { cvm.cancelDownload(d.id) }, modifier = Modifier.padding(top = 6.dp)) { Text("Cancelar") }
                        } else {
                            val c = when (d.status) { DlStatus.DONE -> SuccessGreen; DlStatus.FAILED -> DangerRed; else -> TextSecondary }
                            Text("${d.detail} • ${formatDate(d.endedAt)}", style = MaterialTheme.typography.bodySmall, color = c)
                            if (d.status == DlStatus.DONE) {
                                Text(formatSize(d.size), style = MaterialTheme.typography.bodySmall, color = TextSecondary)
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                                    if (d.isPkg) {
                                        Button(onClick = { vm.sendDownloaded(d.path) }) { Text("Enviar ao PS4") }
                                    }
                                    TextButton(onClick = { cvm.removeRecord(d.id, true) }) {
                                        Icon(Icons.Filled.Delete, contentDescription = null, modifier = Modifier.size(18.dp))
                                        Text(" Apagar arquivo")
                                    }
                                }
                            }
                        }
                    }
                }
                item {
                    TextButton(onClick = {
                        try { ctx.startActivity(Intent(DownloadManager.ACTION_VIEW_DOWNLOADS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) } catch (e: Exception) { cvm.messages.tryEmit("Não foi possível abrir a lista do sistema") }
                    }) { Text("Abrir lista de downloads do Android") }
                }
            }
        }
    }
}
