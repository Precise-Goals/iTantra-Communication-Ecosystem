package com.itantra.ui.screen

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.clickable
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.RecordVoiceOver
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
import com.itantra.domain.model.IndicLanguage
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

    val selectedLanguage by viewModel.selectedLanguage.collectAsState()
    val currentLang = IndicLanguage.fromCode(selectedLanguage)
    val corePacks = ModelPack.coreTransceiverPacks(selectedLanguage)
    val allCoreDownloaded = corePacks.all { downloadStates[it] is DownloadState.Downloaded }
    val anyCoreDownloading = corePacks.any { downloadStates[it] is DownloadState.Downloading }

    val downloadedCount = downloadStates.values.count { it is DownloadState.Downloaded }
    val totalSizeMb = downloadStates.entries
        .filter { it.value is DownloadState.Downloaded }
        .sumOf { (pack, _) -> ModelRegistry.getInfo(pack)?.sizeBytes ?: 0L } / (1024 * 1024)

    val coreTotalBytes = ModelRegistry.totalSizeBytes(corePacks)
    val coreTotalMb = coreTotalBytes / (1024 * 1024)

    val currentStt = ModelPack.sttPackFor(selectedLanguage)
    val currentTts = ModelPack.ttsPackFor(selectedLanguage)

    val descComponents = buildList {
        add("Silero VAD (2.3 MB)")
        add("eSpeak-NG phonemizer data (6.6 MB)")
        if (currentStt != null) {
            val sttMb = (ModelRegistry.getInfo(currentStt)?.sizeBytes ?: 0L) / (1024 * 1024)
            if (currentStt == ModelPack.STT_SRAVAANI) {
                add("SraVaani INT8 TDT STT ($sttMb MB, shared across 9 languages)")
            } else {
                add("${currentStt.displayName} ($sttMb MB)")
            }
        } else {
            add("speech recognition not available yet")
        }
        if (currentTts != null) {
            val ttsMb = (ModelRegistry.getInfo(currentTts)?.sizeBytes ?: 0L) / (1024 * 1024)
            add("${currentTts.displayName} ($ttsMb MB)")
        } else {
            add("voice not available yet")
        }
    }
    val descriptionText = "Installs core transceiver bundle for ${currentLang.displayName} (${currentLang.nativeName}): ${descComponents.joinToString(", ")}."

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

        // ── Language Selector ──────────────────────────────────────
        item {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = "Select Language",
                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                    color = iTantraBlack
                )
                LazyRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(IndicLanguage.entries, key = { it.code }) { lang ->
                        val isSelected = lang.code == selectedLanguage
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(14.dp))
                                .background(if (isSelected) iTantraBlack else iTantraCardAlt)
                                .border(
                                    1.dp,
                                    if (isSelected) iTantraBlack else iTantraBorder,
                                    RoundedCornerShape(14.dp)
                                )
                                .clickable { viewModel.setManualLanguage(lang.code) }
                                .padding(horizontal = 14.dp, vertical = 8.dp)
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(
                                    text = lang.nativeName,
                                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                                    color = if (isSelected) iTantraWhite else iTantraBlack
                                )
                                Text(
                                    text = lang.displayName,
                                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                                    color = if (isSelected) Color(0xFFD4D4D4) else iTantraBlack60
                                )
                            }
                        }
                    }
                }
            }
        }

        // ── HERO: "Download the Pack" (Compulsory Core for selected language) ─
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
                                    text = "Download the Pack · ${currentLang.nativeName}",
                                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                    color = if (allCoreDownloaded) iTantraBlack else iTantraWhite
                                )
                                Text(
                                    text = if (allCoreDownloaded) "Core transceiver pack installed (${currentLang.nativeName})" else "Compulsory · ${currentLang.displayName} (${currentLang.nativeName}) speech & voice",
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
                        text = descriptionText,
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
                                text = "Core transceiver models for ${currentLang.displayName} (${currentLang.nativeName}) are active.",
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
                text = "Compulsory Packs for ${currentLang.displayName} · ${currentLang.nativeName} (${corePacks.size})",
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

        // ── Other Languages Section ─────────────────────────────────
        item {
            Column(modifier = Modifier.padding(top = 12.dp)) {
                Text(
                    text = "Other Languages",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                    color = iTantraBlack
                )
                Text(
                    text = "Download speech recognition or voices for other languages to receive and speak messages in other languages.",
                    style = MaterialTheme.typography.bodySmall,
                    color = iTantraBlack60
                )
            }
        }

        val otherLanguages = IndicLanguage.entries.filter { it.code != selectedLanguage }
        otherLanguages.forEach { lang ->
            val otherStt = ModelPack.sttPackFor(lang.code)
            val otherTts = ModelPack.ttsPackFor(lang.code)

            item(key = "other_header_${lang.code}") {
                Text(
                    text = "${lang.displayName} · ${lang.nativeName}",
                    style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
                    color = iTantraBlack,
                    modifier = Modifier.padding(top = 8.dp)
                )
            }

            if (otherStt != null) {
                item(key = "other_stt_${lang.code}_${otherStt.name}") {
                    ModelPackRowItem(
                        pack = otherStt,
                        state = downloadStates[otherStt] ?: DownloadState.NotDownloaded,
                        onDownload = { viewModel.downloadModel(otherStt) },
                        onDelete = { viewModel.deleteModel(otherStt) }
                    )
                }
            }

            if (otherTts != null) {
                item(key = "other_tts_${lang.code}_${otherTts.name}") {
                    ModelPackRowItem(
                        pack = otherTts,
                        state = downloadStates[otherTts] ?: DownloadState.NotDownloaded,
                        onDownload = { viewModel.downloadModel(otherTts) },
                        onDelete = { viewModel.deleteModel(otherTts) }
                    )
                }
            } else {
                item(key = "other_no_voice_${lang.code}") {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(20.dp))
                            .background(iTantraCard)
                            .border(1.dp, iTantraBorder, RoundedCornerShape(20.dp))
                            .padding(16.dp)
                    ) {
                        Text(
                            text = "Voice not available yet",
                            style = MaterialTheme.typography.bodySmall,
                            color = iTantraBlack40
                        )
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
    val subtitle = if (pack == ModelPack.STT_SRAVAANI) {
        "${sizeMb} MB · One shared download for 9 languages · ${info?.fileName ?: ""}"
    } else {
        "${sizeMb} MB · ${info?.fileName ?: ""}"
    }

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
                            text = subtitle,
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
