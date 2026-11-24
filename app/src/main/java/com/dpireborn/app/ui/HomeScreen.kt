package com.dpireborn.app.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
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
import androidx.compose.material.icons.filled.PhoneIphone
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dpireborn.app.AppViewModel
import com.dpireborn.app.R
import com.dpireborn.app.ui.theme.SurfaceDark
import com.dpireborn.app.ui.theme.TextSecondary

/** Início: lista de PKG (Biblioteca) + as telas Console e Envios em abas internas. */
@Composable
fun HomeScreen(vm: AppViewModel, sub: Int, onSub: (Int) -> Unit) {
    val chipColors = FilterChipDefaults.filterChipColors(
        selectedContainerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.35f),
        selectedLabelColor = Color.White,
        labelColor = TextSecondary,
    )
    Column(modifier = Modifier.fillMaxSize()) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 8.dp)) {
            listOf("PKGs", "Console", "Envios").forEachIndexed { i, label ->
                FilterChip(selected = sub == i, onClick = { onSub(i) }, label = { Text(label) }, colors = chipColors)
            }
        }
        Box(modifier = Modifier.weight(1f)) {
            when (sub) {
                1 -> ConsoleScreen(vm)
                2 -> TransfersScreen(vm)
                else -> Column(modifier = Modifier.fillMaxSize()) {
                    Box(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) { IosNoticeCard() }
                    Box(modifier = Modifier.weight(1f)) { LibraryScreen(vm) { onSub(2) } }
                }
            }
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
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(42.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(Brush.linearGradient(listOf(accent, Color(0xFF2E1065)))),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Filled.PhoneIphone, contentDescription = null, tint = Color.White)
            }
            Column(modifier = Modifier.padding(start = 12.dp)) {
                Text("NOVIDADE", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = accent, letterSpacing = 1.2.sp)
                Text(stringResource(R.string.ios_notice), style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 2.dp))
            }
        }
    }
}
