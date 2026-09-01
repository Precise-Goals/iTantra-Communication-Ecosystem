package com.itantra.ui.screen

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.itantra.domain.model.IndicLanguage
import com.itantra.ui.MainViewModel
import com.itantra.ui.theme.ActiveGreen400
import com.itantra.ui.theme.ActiveGreenGlow
import com.itantra.ui.theme.DistressRed500
import com.itantra.ui.theme.OnSurface
import com.itantra.ui.theme.OnSurfaceDim
import com.itantra.ui.theme.SignalOrange500
import com.itantra.ui.theme.SpaceBlue700
import com.itantra.ui.theme.SpaceBlue800

@Composable
fun SettingsScreen(viewModel: MainViewModel) {
    val sttLanguage by viewModel.sttLanguage.collectAsState()
    val ttsLanguage by viewModel.ttsLanguage.collectAsState()
    val ramUsage by viewModel.ramUsageMbFlow.collectAsState()
    val maxRam = 400f // max expected MB

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Text("Language & AI Settings", style = MaterialTheme.typography.headlineSmall.copy(color = OnSurface, fontWeight = FontWeight.Bold))
        }

        // STT Language
        item {
            LanguageSectionCard(
                title = "🎤 STT Input Language",
                subtitle = "Language you will speak in",
                selected = sttLanguage,
                onSelect = { viewModel.setSTTLanguage(it) }
            )
        }

        // TTS Language
        item {
            LanguageSectionCard(
                title = "🔊 TTS Output Language",
                subtitle = "Language receiver will hear",
                selected = ttsLanguage,
                onSelect = { viewModel.setTTSLanguage(it) }
            )
        }

        // RAM Usage
        item {
            Card(colors = CardDefaults.cardColors(containerColor = SpaceBlue800), shape = RoundedCornerShape(16.dp)) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Filled.Memory, null, tint = SignalOrange500, modifier = Modifier.size(20.dp))
                        Text(
                            "  Memory Usage",
                            style = MaterialTheme.typography.titleSmall.copy(color = OnSurface)
                        )
                    }
                    Spacer(Modifier.height(12.dp))
                    val ramProgress by animateFloatAsState(
                        targetValue = (ramUsage / maxRam).coerceIn(0f, 1f),
                        animationSpec = tween(500),
                        label = "ram_progress"
                    )
                    LinearProgressIndicator(
                        progress = { ramProgress },
                        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(4.dp)),
                        color = when {
                            ramUsage > 350 -> DistressRed500
                            ramUsage > 250 -> SignalOrange500
                            else -> ActiveGreen400
                        },
                        trackColor = SpaceBlue700
                    )
                    Spacer(Modifier.height(6.dp))
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("${ramUsage.toInt()} MB used", style = MaterialTheme.typography.bodySmall.copy(color = OnSurfaceDim))
                        Text("${maxRam.toInt()} MB target max", style = MaterialTheme.typography.bodySmall.copy(color = OnSurfaceDim))
                    }
                }
            }
        }

        // Inference Delegate Info
        item {
            Card(colors = CardDefaults.cardColors(containerColor = SpaceBlue800), shape = RoundedCornerShape(16.dp)) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Filled.Speed, null, tint = ActiveGreen400, modifier = Modifier.size(20.dp))
                        Text("  AI Inference", style = MaterialTheme.typography.titleSmall.copy(color = OnSurface))
                    }
                    Spacer(Modifier.height(12.dp))
                    DelegateInfoRow("STT Model", "IndicConformer Multilingual INT8", "~150 MB")
                    DelegateInfoRow("TTS Model", "IndicTTS VITS INT8 (per language)", "~15 MB each")
                    DelegateInfoRow("VAD Model", "Silero VAD v4", "~2 MB")
                    DelegateInfoRow("Runtime", "ONNX Runtime Mobile", "~8 MB")
                    DelegateInfoRow("Delegate", "NNAPI → GPU → XNNPACK", "Auto")
                }
            }
        }
    }
}

@Composable
private fun LanguageSectionCard(
    title: String,
    subtitle: String,
    selected: IndicLanguage,
    onSelect: (IndicLanguage) -> Unit
) {
    Card(colors = CardDefaults.cardColors(containerColor = SpaceBlue800), shape = RoundedCornerShape(16.dp)) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(title, style = MaterialTheme.typography.titleSmall.copy(color = OnSurface))
            Text(subtitle, style = MaterialTheme.typography.bodySmall.copy(color = OnSurfaceDim))
            Spacer(Modifier.height(12.dp))
            IndicLanguage.entries.chunked(2).forEach { row ->
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    row.forEach { lang ->
                        LanguageChipSelectable(
                            lang = lang,
                            isSelected = lang == selected,
                            onClick = { onSelect(lang) },
                            modifier = Modifier.weight(1f)
                        )
                    }
                    if (row.size == 1) Spacer(Modifier.weight(1f))
                }
                Spacer(Modifier.height(8.dp))
            }
        }
    }
}

@Composable
private fun LanguageChipSelectable(lang: IndicLanguage, isSelected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val bgColor by animateColorAsState(
        targetValue = if (isSelected) ActiveGreenGlow else SpaceBlue700,
        label = "lang_bg"
    )
    val borderColor by animateColorAsState(
        targetValue = if (isSelected) ActiveGreen400 else Color.Transparent,
        label = "lang_border"
    )

    Box(
        modifier = modifier
            .background(bgColor, RoundedCornerShape(8.dp))
            .border(1.dp, borderColor, RoundedCornerShape(8.dp))
            .clickable { onClick() }
            .padding(horizontal = 8.dp, vertical = 8.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(lang.nativeName, style = MaterialTheme.typography.bodySmall.copy(color = OnSurface, fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal))
            Text(lang.displayName, style = MaterialTheme.typography.labelSmall.copy(color = if (isSelected) ActiveGreen400 else OnSurfaceDim))
        }
    }
}

@Composable
private fun DelegateInfoRow(label: String, value: String, size: String) {
    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp), horizontalArrangement = Arrangement.SpaceBetween) {
        Column {
            Text(label, style = MaterialTheme.typography.bodySmall.copy(color = OnSurfaceDim))
            Text(value, style = MaterialTheme.typography.bodySmall.copy(color = OnSurface))
        }
        Text(size, style = MaterialTheme.typography.labelSmall.copy(color = SignalOrange500, fontWeight = FontWeight.SemiBold))
    }
}
