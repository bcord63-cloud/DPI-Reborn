@file:OptIn(ExperimentalMaterial3Api::class)

package com.dpireborn.app.ui

import android.os.Build
import android.os.Environment
import android.os.StatFs
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.dpireborn.app.AppViewModel
import com.dpireborn.app.ui.theme.AccentOptions
import com.dpireborn.app.ui.theme.OutlineDim
import com.dpireborn.app.ui.theme.TextSecondary
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

@Composable
fun SettingsScreen(vm: AppViewModel) {
    val ctx = LocalContext.current
    val accent by vm.accent.collectAsStateWithLifecycle()
    val grid by vm.gridView.collectAsStateWithLifecycle()
    val port by vm.serverPort.collectAsStateWithLifecycle()
    val files by vm.files.collectAsStateWithLifecycle()
    val pkgInfo by vm.pkgInfo.collectAsStateWithLifecycle()

    var portText by remember(port) { mutableStateOf(port.toString()) }
    var cacheSize by remember { mutableLongStateOf(0L) }

    LaunchedEffect(pkgInfo) {
        cacheSize = withContext(Dispatchers.IO) {
            try {
                ctx.cacheDir.walkTopDown().filter { it.isFile }.sumOf { it.length() }
            } catch (e: Exception) {
                0L
            }
        }
    }

    val stat = remember { StatFs(Environment.getDataDirectory().path) }
    val total = stat.totalBytes
    val free = stat.availableBytes
    val used = total - free
    val pkgTotal = remember(files) { files.filter { it.isPkg }.sumOf { it.size } }
    val versionName = remember {
        try {
            ctx.packageManager.getPackageInfo(ctx.packageName, 0).versionName ?: "1.0.0"
        } catch (e: Exception) {
            "1.0.0"
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        ScreenHeader(title = "Ajustes", subtitle = "Personalização, armazenamento e informações")

        SectionCard {
            Text("Aparência", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Text(
                text = "Cor de destaque",
                style = MaterialTheme.typography.labelMedium,
                color = TextSecondary,
                modifier = Modifier.padding(top = 10.dp, bottom = 8.dp),
            )
            Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                AccentOptions.forEach { opt ->
                    val chosen = opt.key == accent
                    Box(
                        modifier = Modifier
                            .size(44.dp)
                            .clip(CircleShape)
                            .background(opt.color)
                            .border(
                                BorderStroke(if (chosen) 3.dp else 1.dp, if (chosen) Color.White else OutlineDim),
                                CircleShape,
                            )
                            .clickable { vm.setAccent(opt.key) },
                        contentAlignment = Alignment.Center,
                    ) {
                        if (chosen) Icon(Icons.Filled.Check, contentDescription = opt.label, tint = Color.White)
                    }
                }
            }
            HorizontalDivider(color = OutlineDim, modifier = Modifier.padding(vertical = 14.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("Biblioteca em grade", style = MaterialTheme.typography.bodyLarge)
                    Text(
                        text = if (grid) "Capas grandes em grade" else "Lista compacta",
                        style = MaterialTheme.typography.labelSmall,
                        color = TextSecondary,
                    )
                }
                Switch(
                    checked = grid,
                    onCheckedChange = { vm.setGridView(it) },
                    colors = SwitchDefaults.colors(checkedTrackColor = MaterialTheme.colorScheme.primary),
                )
            }
        }

        SectionCard {
            Text("Rede", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Text(
                text = "Porta do servidor local que entrega o PKG ao PS4. Se estiver ocupada, o app usa outra porta livre automaticamente.",
                style = MaterialTheme.typography.bodySmall,
                color = TextSecondary,
                modifier = Modifier.padding(top = 4.dp),
            )
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 10.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                OutlinedTextField(
                    value = portText,
                    onValueChange = { portText = it.filter { c -> c.isDigit() }.take(5) },
                    label = { Text("Porta") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    shape = RoundedCornerShape(14.dp),
                    colors = fieldColors(),
                    modifier = Modifier.weight(1f),
                )
                Button(onClick = { vm.setServerPort(portText.toIntOrNull() ?: 0) }) { Text("Salvar") }
            }
        }

        SectionCard {
            Text("Armazenamento", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Text(
                text = "Memória interna: ${formatSize(used)} usados de ${formatSize(total)}",
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(top = 8.dp),
            )
            LinearProgressIndicator(
                progress = { if (total > 0L) (used.toDouble() / total.toDouble()).toFloat() else 0f },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp)
                    .height(8.dp)
                    .clip(RoundedCornerShape(4.dp)),
                color = MaterialTheme.colorScheme.primary,
                trackColor = OutlineDim,
            )
            Text(
                text = "Livre: ${formatSize(free)}",
                style = MaterialTheme.typography.labelSmall,
                color = TextSecondary,
                modifier = Modifier.padding(top = 4.dp),
            )
            HorizontalDivider(color = OutlineDim, modifier = Modifier.padding(vertical = 12.dp))
            InfoLine("PKG na biblioteca", "${files.count { it.isPkg }} • ${formatSize(pkgTotal)}")
            InfoLine("Cache de capas e metadados", formatSize(cacheSize))
            OutlinedButton(
                onClick = { vm.clearCache() },
                modifier = Modifier.padding(top = 8.dp),
            ) { Text("Limpar cache") }
        }

        SectionCard {
            Text("Sobre", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Column(modifier = Modifier.padding(top = 8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                InfoLine("Aplicativo", "DPI Reborn $versionName")
                InfoLine("Pacote", ctx.packageName)
                InfoLine("Android", "${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})")
                InfoLine("Aparelho", "${Build.MANUFACTURER} ${Build.MODEL}")
            }
            Text(
                text = "O DPI Reborn é um instalador de pacotes por rede local para uso com arquivos que você " +
                    "possui. Ele não baixa nem distribui jogos. O envio depende de um app instalador remoto " +
                    "(Remote Package Installer / DPI v2) rodando no seu PS4.",
                style = MaterialTheme.typography.bodySmall,
                color = TextSecondary,
                modifier = Modifier.padding(top = 12.dp, bottom = 8.dp),
            )
        }

        Box(modifier = Modifier.height(16.dp))
    }
}

@Composable
private fun InfoLine(label: String, value: String) {
    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = TextSecondary,
            modifier = Modifier.weight(1f),
        )
        Text(text = value, style = MaterialTheme.typography.bodyMedium)
    }
}
