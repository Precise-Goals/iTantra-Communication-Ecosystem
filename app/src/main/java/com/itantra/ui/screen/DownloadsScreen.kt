package com.itantra.ui.screen

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.RecordVoiceOver
import androidx.compose.material.icons.filled.SmartToy
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.filled.Translate
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.unit.sp
import com.itantra.core.download.ModelRegistry
import com.itantra.domain.model.DownloadState
import com.itantra.domain.model.ModelPack
import com.itantra.ui.MainViewModel
import com.itantra.ui.theme.iTantraBackground
import com.itantra.ui.theme.iTantraBlack
import com.itantra.ui.theme.iTantraBlack40
import com.itantra.ui.theme.iTantraBlack60
import com.itantra.ui.theme.iTantraBorder
import com.itantra.ui.theme.iTantraCard
import com.itantra.ui.theme.iTantraCardAlt
import com.itantra.ui.theme.iTantraSuccess
import com.itantra.ui.theme.iTantraWhite

@Composable
fun DownloadsScreen(viewModel: MainViewModel) {
    val downloadStates by viewModel.downloadStates.collectAsState()
    var showOptionalAssistant by remember { mutableStateOf(false) }

    val corePacks = ModelPack.coreTransceiverPacks()
    val allCoreDownloaded = corePacks.all { downloadStates[it] is DownloadState.Downloaded }
    val anyCoreDownloading = corePacks.any { downloadStates[it] is DownloadState.Downloading }

    val downloadedCount = downloadStates.values.count { it is DownloadState.Downloaded }
    val totalSizeMb = downloadStates.entries
        .filter { it.value is DownloadState.Downloaded }
        .sumOf { (pack, _) -> ModelRegistry.getInfo(pack)?.sizeBytes ?: 0L } / (1024 * 1024)

    val coreTotalBytes = ModelRegistry.totalSizeBytes(corePacks)
    val coreTotalMb = coreTotalBytes / (1024 * 1024)

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(iTantraBackground),
        contentPadding = PaddingValues(start = 20.dp, top = 20.dp, end = 20.dp, bottom = 120.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // ── Header ─────────────────────────────────────────────────
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "Neural Models",
                        style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold),
                        color = iTantraBlack
                    )
                    Text(
                        text = "$downloadedCount of ${ModelPack.entries.size} installed · ${totalSizeMb} MB cached on disk",
                        style = MaterialTheme.typography.bodySmall,
                        color = iTantraBlack60
                    )
                }
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(20.dp))
                        .background(iTantraCardAlt)
                        .border(1.dp, iTantraBorder, RoundedCornerShape(20.dp))
                        .padding(horizontal = 12.dp, vertical = 6.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Filled.Storage, contentDescription = null, tint = iTantraBlack, modifier = Modifier.size(14.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(
                            text = "Offline Storage",
                            style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp),
                            color = iTantraBlack
                        )
                    }
                }
            }
        }

        // ── HERO: "Download the Pack" (Compulsory Core & 10 Languages) ─
        item {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(26.dp))
                    .background(if (allCoreDownloaded) iTantraCardAlt else iTantraBlack)
                    .border(
                        width = 1.5.dp,
                        color = if (allCoreDownloaded) iTantraBorder else iTantraBlack,
                        shape = RoundedCornerShape(26.dp)
                    )
                    .padding(22.dp)
            ) {
                Column {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(36.dp)
                                    .clip(CircleShape)
                                    .background(if (allCoreDownloaded) iTantraBlack else iTantraWhite),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = if (allCoreDownloaded) Icons.Filled.CheckCircle else Icons.Filled.Download,
                                    contentDescription = null,
                                    tint = if (allCoreDownloaded) iTantraWhite else iTantraBlack,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                            Spacer(Modifier.width(10.dp))
                            Column {
                                Text(
                                    text = "Download the Pack",
                                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                    color = if (allCoreDownloaded) iTantraBlack else iTantraWhite
                                )
                                Text(
                                    text = if (allCoreDownloaded) "All 10 Language Models Installed" else "Compulsory · All 10 Indian Languages & STT",
                                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                                    color = if (allCoreDownloaded) iTantraSuccess else Color(0xFFD4D4D4)
                                )
                            }
                        }

                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(12.dp))
                                .background(if (allCoreDownloaded) iTantraWhite else Color(0x33FFFFFF))
                                .padding(horizontal = 10.dp, vertical = 5.dp)
                        ) {
                            Text(
                                text = if (allCoreDownloaded) "INSTALLED" else "COMPULSORY",
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 10.sp
                                ),
                                color = if (allCoreDownloaded) iTantraBlack else iTantraWhite
                            )
                        }
                    }

                    Spacer(Modifier.height(14.dp))

                    Text(
                        text = "Installs complete bundle: Silero VAD (2.3MB), AI4Bharat IndicConformer STT (197MB), FastText Language Auto-Detector (0.9MB), and real espeak-ng-phonemized Voice Packs for Hindi, Gujarati, Malayalam, Bengali and English — the only languages with a verified free offline TTS source today. Kannada, Tamil, Telugu, Marathi and Odia have no known source yet and aren't offered.",
                        style = MaterialTheme.typography.bodySmall,
                        color = if (allCoreDownloaded) iTantraBlack60 else Color(0xFFCCCCCC)
                    )

                    Spacer(Modifier.height(18.dp))

                    if (allCoreDownloaded) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(16.dp))
                                .background(iTantraWhite)
                                .border(1.dp, iTantraBorder, RoundedCornerShape(16.dp))
                                .padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Filled.CheckCircle, contentDescription = null, tint = iTantraSuccess, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(8.dp))
                            Text(
                                text = "All 10 language models and neural transceiver are fully active.",
                                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                                color = iTantraBlack
                            )
                        }
                    } else {
                        Button(
                            onClick = { viewModel.downloadAll(corePacks) },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(50.dp),
                            shape = RoundedCornerShape(25.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = iTantraWhite,
                                contentColor = iTantraBlack
                            )
                        ) {
                            Icon(Icons.Filled.Download, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(8.dp))
                            Text(
                                text = if (anyCoreDownloading) "Downloading Core Pack…" else "Download the Pack ($coreTotalMb MB)",
                                style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold)
                            )
                        }
                    }
                }
            }
        }

        // ── Compulsory Models List ──────────────────────────────────
        item {
            Text(
                text = "Compulsory Language & Transceiver Packs (${corePacks.size})",
                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                color = iTantraBlack,
                modifier = Modifier.padding(top = 8.dp)
            )
        }

        items(corePacks, key = { it.name }) { pack ->
            ModelPackRowItem(
                pack = pack,
                state = downloadStates[pack] ?: DownloadState.NotDownloaded,
                onDownload = { viewModel.downloadModel(pack) },
                onDelete = { viewModel.deleteModel(pack) }
            )
        }

        // ── Optional Section: AI Assistant (Hidden / Collapsible) ─────
        item {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(20.dp))
                    .background(iTantraCardAlt)
                    .border(1.dp, iTantraBorder, RoundedCornerShape(20.dp))
                    .clickable { showOptionalAssistant = !showOptionalAssistant }
                    .padding(16.dp)
            ) {
                Column {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Filled.SmartToy, contentDescription = null, tint = iTantraBlack60, modifier = Modifier.size(20.dp))
                            Spacer(Modifier.width(10.dp))
                            Column {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        text = "Optional: Phi-3 Mini GGUF",
                                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                                        color = iTantraBlack
                                    )
                                    Spacer(Modifier.width(8.dp))
                                    Box(
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(6.dp))
                                            .background(iTantraBorder)
                                            .padding(horizontal = 6.dp, vertical = 2.dp)
                                    ) {
                                        Text(
                                            text = "OPTIONAL",
                                            style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp),
                                            color = iTantraBlack60
                                        )
                                    }
                                }
                                Text(
                                    text = "2.39 GB · Heavy LLM weights (Assistant already works without this)",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = iTantraBlack60
                                )
                            }
                        }

                        Icon(
                            imageVector = if (showOptionalAssistant) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
                            contentDescription = null,
                            tint = iTantraBlack
                        )
                    }

                    AnimatedVisibility(visible = showOptionalAssistant) {
                        Column(modifier = Modifier.padding(top = 16.dp)) {
                            ModelPackRowItem(
                                pack = ModelPack.AI_ASSISTANT,
                                state = downloadStates[ModelPack.AI_ASSISTANT] ?: DownloadState.NotDownloaded,
                                onDownload = { viewModel.downloadModel(ModelPack.AI_ASSISTANT) },
                                onDelete = { viewModel.deleteModel(ModelPack.AI_ASSISTANT) }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ModelPackRowItem(
    pack: ModelPack,
    state: DownloadState,
    onDownload: () -> Unit,
    onDelete: () -> Unit
) {
    val info = ModelRegistry.getInfo(pack)
    val sizeMb = (info?.sizeBytes ?: 0L) / (1024 * 1024)

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(iTantraCard)
            .border(1.dp, iTantraBorder, RoundedCornerShape(20.dp))
            .padding(16.dp)
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    modifier = Modifier.weight(1f),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(iTantraCardAlt),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = when {
                                pack.name.contains("VAD") -> Icons.Filled.Memory
                                pack.name.contains("STT") -> Icons.Filled.Translate
                                pack.name.contains("TTS") -> Icons.Filled.RecordVoiceOver
                                else -> Icons.Filled.Storage
                            },
                            contentDescription = null,
                            tint = iTantraBlack,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                    Spacer(Modifier.width(12.dp))
                    Column {
                        Text(
                            text = pack.displayName,
                            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
                            color = iTantraBlack
                        )
                        Text(
                            text = "${sizeMb} MB · ${info?.fileName ?: ""}",
                            style = MaterialTheme.typography.bodySmall,
                            color = iTantraBlack60
                        )
                    }
                }

                // Action button
                when (state) {
                    is DownloadState.Downloaded -> {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                Icons.Filled.CheckCircle,
                                contentDescription = "Downloaded",
                                tint = iTantraSuccess,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(Modifier.width(6.dp))
                            IconButton(
                                onClick = onDelete,
                                modifier = Modifier.size(32.dp)
                            ) {
                                Icon(
                                    Icons.Filled.Delete,
                                    contentDescription = "Delete",
                                    tint = iTantraBlack40,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                    }
                    is DownloadState.Downloading -> {
                        Text(
                            text = "${state.progressPercent.toInt()}%",
                            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                            color = iTantraBlack
                        )
                    }
                    is DownloadState.Queued -> {
                        Text(
                            text = "Queued…",
                            style = MaterialTheme.typography.labelSmall,
                            color = iTantraBlack60
                        )
                    }
                    else -> {
                        Button(
                            onClick = onDownload,
                            shape = RoundedCornerShape(16.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = iTantraBlack,
                                contentColor = iTantraWhite
                            ),
                            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp)
                        ) {
                            Icon(Icons.Filled.Download, contentDescription = null, modifier = Modifier.size(14.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Get", style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold))
                        }
                    }
                }
            }

            // Progress bar when downloading
            if (state is DownloadState.Downloading) {
                val animatedProgress by animateFloatAsState(
                    targetValue = state.progressPercent / 100f,
                    animationSpec = tween(150),
                    label = "dl_progress"
                )
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    LinearProgressIndicator(
                        progress = { animatedProgress },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(6.dp)
                            .clip(RoundedCornerShape(3.dp)),
                        color = iTantraBlack,
                        trackColor = iTantraBorder
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "${state.downloadedBytes / (1024 * 1024)} MB of ${state.totalBytes / (1024 * 1024)} MB",
                            style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                            color = iTantraBlack60
                        )
                        Text(
                            text = "${state.progressPercent.toInt()}%",
                            style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp, fontWeight = FontWeight.Bold),
                            color = iTantraBlack
                        )
                    }
                }
            }
        }
    }
}
