@file:OptIn(ExperimentalMaterial3Api::class)

package com.dpireborn.app.ui

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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Router
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.dpireborn.app.AppViewModel
import com.dpireborn.app.data.PsConsole
import com.dpireborn.app.data.TestResult
import com.dpireborn.app.net.NetUtil
import com.dpireborn.app.ui.theme.DangerRed
import com.dpireborn.app.ui.theme.SuccessGreen
import com.dpireborn.app.ui.theme.SurfaceHigh
import com.dpireborn.app.ui.theme.TextSecondary

@Composable
fun ConsoleScreen(vm: AppViewModel) {
    val consoles by vm.consoles.collectAsStateWithLifecycle()
    val selectedId by vm.selectedConsoleId.collectAsStateWithLifecycle()
    val results by vm.testResults.collectAsStateWithLifecycle()
    val testing by vm.testing.collectAsStateWithLifecycle()

    var name by rememberSaveable { mutableStateOf("PS4") }
    var ip by rememberSaveable { mutableStateOf("") }
    var port by rememberSaveable { mutableStateOf("12800") }
    var method by rememberSaveable { mutableStateOf("auto") }
    var error by remember { mutableStateOf<String?>(null) }
    var deleting by remember { mutableStateOf<PsConsole?>(null) }
    val phoneIp = remember { NetUtil.localIpv4() }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        contentPadding = PaddingValues(bottom = 24.dp),
    ) {
        item {
            ScreenHeader(
                title = "Console",
                subtitle = "IP deste celular: ${phoneIp ?: "sem rede local"}",
            )
        }

        item {
            SectionCard {
                Text("Novo console", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it.take(24) },
                    label = { Text("Nome") },
                    singleLine = true,
                    shape = RoundedCornerShape(14.dp),
                    colors = fieldColors(),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 10.dp),
                )
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    OutlinedTextField(
                        value = ip,
                        onValueChange = { ip = it.filter { c -> c.isDigit() || c == '.' }.take(15) },
                        label = { Text("Endereço IP") },
                        placeholder = { Text("192.168.0.25") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                        shape = RoundedCornerShape(14.dp),
                        colors = fieldColors(),
                        modifier = Modifier.weight(2f),
                    )
                    OutlinedTextField(
                        value = port,
                        onValueChange = { port = it.filter { c -> c.isDigit() }.take(5) },
                        label = { Text("Porta") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        shape = RoundedCornerShape(14.dp),
                        colors = fieldColors(),
                        modifier = Modifier.weight(1f),
                    )
                }
                Text(
                    text = "Método de envio",
                    style = MaterialTheme.typography.labelMedium,
                    color = TextSecondary,
                    modifier = Modifier.padding(top = 12.dp, bottom = 4.dp),
                )
                MethodChips(selected = method, onSelect = { method = it })
                if (error != null) {
                    Text(
                        text = error ?: "",
                        color = DangerRed,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(top = 8.dp),
                    )
                }
                Button(
                    onClick = {
                        val err = vm.saveConsole(name, ip, port.toIntOrNull() ?: 0, method)
                        error = err
                        if (err == null) ip = ""
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 12.dp),
                ) {
                    Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                    Text("  Salvar console")
                }
            }
        }

        if (consoles.isEmpty()) {
            item {
                Text(
                    text = "Nenhum console salvo ainda.",
                    color = TextSecondary,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(vertical = 8.dp),
                )
            }
        } else {
            items(consoles, key = { it.id }) { c ->
                ConsoleCard(
                    console = c,
                    selected = c.id == selectedId,
                    isTesting = c.id in testing,
                    result = results[c.id],
                    onSelect = { vm.selectConsole(c.id) },
                    onTest = { vm.testConsole(c) },
                    onDelete = { deleting = c },
                    onMethod = { vm.setConsoleMethod(c, it) },
                )
            }
        }

        item {
            SectionCard {
                Text("Como funciona", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Text(
                    text = "O DPI Reborn serve o arquivo PKG pelo seu Wi-Fi e pede ao PS4 que o baixe. Há dois " +
                        "jeitos, os mesmos do DirectPackageInstaller:\n\n" +
                        "• API remota (RPI): precisa do app Remote Package Installer aberto na tela do PS4 " +
                        "(porta 12800).\n\n" +
                        "• Payload GoldHEN: não precisa de app. Com o servidor de payloads do GoldHEN ativo " +
                        "(porta 9090), o celular envia um payload ao PS4 que registra o download.\n\n" +
                        "No modo Automático, o app usa a API remota se ela responder e, se não, o payload. " +
                        "Use \"Testar conexão\" para ver o que está disponível.",
                    style = MaterialTheme.typography.bodySmall,
                    color = TextSecondary,
                    modifier = Modifier.padding(top = 8.dp),
                )
            }
        }
    }

    val target = deleting
    if (target != null) {
        AlertDialog(
            onDismissRequest = { deleting = null },
            containerColor = SurfaceHigh,
            title = { Text("Remover console?") },
            text = { Text("\"${target.name}\" (${target.ip}) será removido da lista.") },
            confirmButton = {
                Button(onClick = {
                    vm.deleteConsole(target)
                    deleting = null
                }) { Text("Remover") }
            },
            dismissButton = { TextButton(onClick = { deleting = null }) { Text("Cancelar") } },
        )
    }
}

@Composable
private fun MethodChips(selected: String, onSelect: (String) -> Unit) {
    val options = listOf("auto" to "Auto", "rpi" to "RPI", "payload" to "GoldHEN")
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        for ((key, label) in options) {
            FilterChip(
                selected = selected == key,
                onClick = { onSelect(key) },
                label = { Text(label, maxLines = 1) },
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.35f),
                    selectedLabelColor = MaterialTheme.colorScheme.onBackground,
                ),
            )
        }
    }
}

@Composable
private fun ConsoleCard(
    console: PsConsole,
    selected: Boolean,
    isTesting: Boolean,
    result: TestResult?,
    onSelect: () -> Unit,
    onTest: () -> Unit,
    onDelete: () -> Unit,
    onMethod: (String) -> Unit,
) {
    SectionCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            RadioButton(
                selected = selected,
                onClick = onSelect,
                colors = RadioButtonDefaults.colors(selectedColor = MaterialTheme.colorScheme.primary),
            )
            Icon(
                Icons.Filled.Router,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(end = 10.dp),
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(console.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Text(
                    text = "${console.ip}:${console.port}" + if (selected) "  •  usado nos envios" else "",
                    style = MaterialTheme.typography.bodySmall,
                    color = TextSecondary,
                )
            }
        }
        Box(modifier = Modifier.padding(top = 4.dp)) {
            MethodChips(selected = console.method, onSelect = onMethod)
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            OutlinedButton(onClick = onTest, enabled = !isTesting) {
                if (isTesting) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(16.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.primary,
                    )
                    Text("  Testando…")
                } else {
                    Text("Testar conexão")
                }
            }
            TextButton(onClick = onDelete) { Text("Remover", color = DangerRed) }
        }
        if (result != null) {
            val tint = if (result.ok) SuccessGreen else DangerRed
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = tint.copy(alpha = 0.12f),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp),
            ) {
                Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    result.lines.forEach { line ->
                        Text(text = line, style = MaterialTheme.typography.bodySmall, color = tint)
                    }
                    Text(
                        text = "Testado em ${formatDate(result.timestamp)}",
                        style = MaterialTheme.typography.labelSmall,
                        color = TextSecondary,
                    )
                }
            }
        }
    }
}
