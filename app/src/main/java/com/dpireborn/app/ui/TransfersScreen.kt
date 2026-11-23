@file:OptIn(ExperimentalMaterial3Api::class)

package com.dpireborn.app.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.History
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.dpireborn.app.AppViewModel
import com.dpireborn.app.data.TransferRecord
import com.dpireborn.app.data.TransferStatus
import com.dpireborn.app.ui.theme.DangerRed
import com.dpireborn.app.ui.theme.OutlineDim
import com.dpireborn.app.ui.theme.SuccessGreen
import com.dpireborn.app.ui.theme.TextSecondary
import com.dpireborn.app.ui.theme.WarnAmber

private fun statusLabel(s: TransferStatus): String = when (s) {
    TransferStatus.QUEUED -> "Na fila"
    TransferStatus.SENDING -> "Enviando"
    TransferStatus.DONE -> "Concluído"
    TransferStatus.FAILED -> "Falhou"
    TransferStatus.CANCELLED -> "Cancelado"
}

private fun statusColor(s: TransferStatus): Color = when (s) {
    TransferStatus.QUEUED -> WarnAmber
    TransferStatus.SENDING -> Color(0xFFA78BFA)
    TransferStatus.DONE -> SuccessGreen
    TransferStatus.FAILED -> DangerRed
    TransferStatus.CANCELLED -> TextSecondary
}

@Composable
fun TransfersScreen(vm: AppViewModel) {
    val active by vm.active.collectAsStateWithLifecycle()
    val history by vm.history.collectAsStateWithLifecycle()
    val serverUrl by vm.serverUrl.collectAsStateWithLifecycle()
    val port by vm.serverPort.collectAsStateWithLifecycle()

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        contentPadding = PaddingValues(bottom = 24.dp),
    ) {
        item {
            ScreenHeader(
                title = "Transferências",
                subtitle = if (active.isEmpty()) "Nenhum envio em andamento" else "${active.size} em andamento",
            )
        }

        item {
            SectionCard {
                Text("Servidor local", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                Text(
                    text = serverUrl?.let { "Ativo em $it" }
                        ?: "Parado. Ele inicia sozinho no primeiro envio (porta preferida: $port).",
                    style = MaterialTheme.typography.bodySmall,
                    color = if (serverUrl != null) SuccessGreen else TextSecondary,
                    modifier = Modifier.padding(top = 4.dp),
                )
                Text(
                    text = "O PS4 baixa o arquivo direto do celular. Mantenha o celular no mesmo Wi-Fi do PS4.",
                    style = MaterialTheme.typography.labelSmall,
                    color = TextSecondary,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }
        }

        if (active.isEmpty() && history.isEmpty()) {
            item {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 32.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Icon(
                        Icons.Filled.CloudUpload,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(48.dp),
                    )
                    Text(
                        text = "Escolha um PKG na Biblioteca ou em Arquivos e toque em Enviar ao PS4.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = TextSecondary,
                        modifier = Modifier.padding(top = 12.dp),
                    )
                }
            }
        }

        if (active.isNotEmpty()) {
            item {
                Text("Em andamento", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            }
            items(active, key = { it.id }) { rec ->
                ActiveCard(rec) { vm.cancelTransfer(rec.id) }
            }
        }

        if (history.isNotEmpty()) {
            item {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Filled.History,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp),
                    )
                    Text(
                        text = "  Histórico",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.weight(1f),
                    )
                    TextButton(onClick = { vm.clearHistory() }) { Text("Limpar") }
                }
            }
            items(history, key = { "h" + it.id }) { rec -> HistoryCard(rec) }
        }
    }
}

@Composable
private fun ActiveCard(rec: TransferRecord, onCancel: () -> Unit) {
    val animated by animateFloatAsState(targetValue = rec.progress, label = "progress")
    val remaining = rec.size - rec.covered
    val eta = if (rec.speedBps > 0L && remaining > 0L) formatDuration(remaining / rec.speedBps) else "—"
    SectionCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = rec.fileName,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            Text(
                text = statusLabel(rec.status),
                style = MaterialTheme.typography.labelMedium,
                color = statusColor(rec.status),
                modifier = Modifier.padding(start = 8.dp),
            )
        }
        Text(
            text = "para ${rec.consoleName} (${rec.consoleIp})",
            style = MaterialTheme.typography.labelSmall,
            color = TextSecondary,
        )
        LinearProgressIndicator(
            progress = { animated },
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 12.dp)
                .height(8.dp)
                .clip(RoundedCornerShape(4.dp)),
            color = MaterialTheme.colorScheme.primary,
            trackColor = OutlineDim,
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                text = "${(rec.progress * 100).toInt()}% • ${formatSize(rec.covered)} / ${formatSize(rec.size)}",
                style = MaterialTheme.typography.labelMedium,
            )
            Text(
                text = "${formatSpeed(rec.speedBps)} • falta $eta",
                style = MaterialTheme.typography.labelMedium,
                color = TextSecondary,
            )
        }
        if (rec.detail.isNotBlank()) {
            Text(
                text = rec.detail,
                style = MaterialTheme.typography.labelSmall,
                color = TextSecondary,
                modifier = Modifier.padding(top = 6.dp),
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
            )
        }
        OutlinedButton(
            onClick = onCancel,
            modifier = Modifier.padding(top = 8.dp),
        ) { Text("Cancelar", color = DangerRed) }
    }
}

@Composable
private fun HistoryCard(rec: TransferRecord) {
    SectionCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = rec.fileName,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            Text(
                text = statusLabel(rec.status),
                style = MaterialTheme.typography.labelMedium,
                color = statusColor(rec.status),
                modifier = Modifier.padding(start = 8.dp),
            )
        }
        Text(
            text = "${formatSize(rec.size)} • ${rec.consoleName} • ${formatDate(rec.startedAt)}",
            style = MaterialTheme.typography.labelSmall,
            color = TextSecondary,
            modifier = Modifier.padding(top = 2.dp),
        )
        if (rec.status == TransferStatus.DONE && rec.avgSpeedBps > 0L) {
            Text(
                text = "Velocidade média: ${formatSpeed(rec.avgSpeedBps)}",
                style = MaterialTheme.typography.labelSmall,
                color = TextSecondary,
            )
        }
        if (rec.detail.isNotBlank() && rec.status != TransferStatus.DONE) {
            Text(
                text = rec.detail,
                style = MaterialTheme.typography.labelSmall,
                color = if (rec.status == TransferStatus.FAILED) DangerRed else TextSecondary,
                modifier = Modifier.padding(top = 4.dp),
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}
