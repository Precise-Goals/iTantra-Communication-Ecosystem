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

    val essentialPacks = ModelPack.allEssentialPacks()
    val allDownloaded = essentialPacks.all { downloadStates[it] is DownloadState.Downloaded }
    val anyDownloading = essentialPacks.any { downloadStates[it] is DownloadState.Downloading }

    val downloadedCount = essentialPacks.count { downloadStates[it] is DownloadState.Downloaded }
    val totalSizeMb = essentialPacks
        .filter { downloadStates[it] is DownloadState.Downloaded }
        .sumOf { pack -> ModelRegistry.getInfo(pack)?.sizeBytes ?: 0L } / (1024 * 1024)

    val coreTotalBytes = ModelRegistry.totalSizeBytes(essentialPacks)
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
                        text = "Neural Engines",
                        style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold),
                        color = iTantraBlack
                    )
                    Text(
                        text = "$downloadedCount of ${essentialPacks.size} engines verified · ${totalSizeMb} MB on disk",
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
                            text = "100% Offline",
                            style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp, fontWeight = FontWeight.Bold),
                            color = iTantraBlack
                        )
                    }
                }
            }
        }

        // ── HERO: "Download All Engines" ──────────────────────────
        item {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(26.dp))
                    .background(if (allDownloaded) iTantraCardAlt else iTantraBlack)
                    .border(
                        width = 1.5.dp,
                        color = if (allDownloaded) iTantraBorder else iTantraBlack,
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
                                    .background(if (allDownloaded) iTantraBlack else iTantraWhite),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = if (allDownloaded) Icons.Filled.CheckCircle else Icons.Filled.Download,
                                    contentDescription = null,
                                    tint = if (allDownloaded) iTantraWhite else iTantraBlack,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                            Spacer(Modifier.width(10.dp))
                            Column {
                                Text(
                                    text = if (allDownloaded) "All Engines Active" else "Download Neural Stack",
                                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                    color = if (allDownloaded) iTantraBlack else iTantraWhite
                                )
                                Text(
                                    text = if (allDownloaded) "VAD, Conformer STT, Parler TTS & NLP Verified" else "Compulsory for Radio & Assistant ($coreTotalMb MB)",
                                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                                    color = if (allDownloaded) iTantraSuccess else Color(0xFFD4D4D4)
                                )
                            }
                        }
                    }

                    Spacer(Modifier.height(16.dp))

                    if (allDownloaded) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(14.dp))
                                .background(Color(0x1A15803D))
                                .padding(12.dp)
                        ) {
                            Icon(Icons.Filled.CheckCircle, contentDescription = null, tint = iTantraSuccess, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(8.dp))
                            Text(
                                text = "All on-device models verified with SHA-256 integrity.",
                                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                                color = iTantraBlack
                            )
                        }
                    } else {
                        Button(
                            onClick = { viewModel.downloadAll(essentialPacks) },
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
                                text = if (anyDownloading) "Downloading Engines…" else "Download All Engines ($coreTotalMb MB)",
                                style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold)
                            )
                        }
                    }
                }
            }
        }

        // ── Core Engines List ─────────────────────────────────────
        item {
            Text(
                text = "Essential Neural Engines (${essentialPacks.size})",
                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                color = iTantraBlack,
                modifier = Modifier.padding(top = 8.dp)
            )
        }

        items(essentialPacks, key = { it.name }) { pack ->
            ModelPackRowItem(
                pack = pack,
                state = downloadStates[pack] ?: DownloadState.NotDownloaded,
                onDownload = { viewModel.downloadModel(pack) },
                onDelete = { viewModel.deleteModel(pack) }
            )
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
                            .size(40.dp)
                            .clip(CircleShape)
                            .background(iTantraCardAlt),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = when (pack) {
                                ModelPack.VAD_MODEL -> Icons.Filled.Memory
                                ModelPack.STT_INDIC_CONFORMER -> Icons.Filled.Translate
                                ModelPack.TTS_INDIC_MODEL -> Icons.Filled.RecordVoiceOver
                                ModelPack.AI_ASSISTANT -> Icons.Filled.SmartToy
                            },
                            contentDescription = null,
                            tint = iTantraBlack,
                            modifier = Modifier.size(20.dp)
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
                                contentDescription = "Verified & Active",
                                tint = iTantraSuccess,
                                modifier = Modifier.size(22.dp)
                            )
                            Spacer(Modifier.width(6.dp))
                            IconButton(
                                onClick = onDelete,
                                modifier = Modifier.size(32.dp)
                            ) {
                                Icon(
                                    Icons.Filled.Delete,
                                    contentDescription = "Delete Model",
                                    tint = iTantraBlack40,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }
                    }
                    is DownloadState.Downloading -> {
                        Text(
                            text = "${state.progressPercent.toInt()}%",
                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                            color = iTantraBlack
                        )
                    }
                    is DownloadState.Queued -> {
                        Text(
                            text = "Queued",
                            style = MaterialTheme.typography.labelSmall,
                            color = iTantraBlack60
                        )
                    }
                    is DownloadState.Failed -> {
                        Button(
                            onClick = onDownload,
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Color(0xFFFEE2E2),
                                contentColor = Color(0xFFDC2626)
                            ),
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                            modifier = Modifier.height(32.dp)
                        ) {
                            Text("Retry", style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold))
                        }
                    }
                    is DownloadState.NotDownloaded -> {
                        Button(
                            onClick = onDownload,
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = iTantraBlack,
                                contentColor = iTantraWhite
                            ),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                            modifier = Modifier.height(32.dp)
                        ) {
                            Icon(Icons.Filled.Download, contentDescription = null, modifier = Modifier.size(14.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("Get", style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold))
                        }
                    }
                }
            }

            // Progress bar if downloading
            if (state is DownloadState.Downloading) {
                val animatedProgress by animateFloatAsState(
                    targetValue = state.progressPercent / 100f,
                    animationSpec = tween(150),
                    label = "model_progress"
                )
                LinearProgressIndicator(
                    progress = { animatedProgress },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(4.dp)
                        .clip(RoundedCornerShape(2.dp)),
                    color = iTantraBlack,
                    trackColor = iTantraBorder,
                )
            }

            // Error notice
            if (state is DownloadState.Failed) {
                Text(
                    text = state.reason,
                    style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
                    color = Color(0xFFDC2626)
                )
            }
        }
    }
}
