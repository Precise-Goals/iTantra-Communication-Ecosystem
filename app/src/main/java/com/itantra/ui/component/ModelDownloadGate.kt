package com.itantra.ui.component

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.outlined.CloudDownload
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.itantra.core.download.ModelRegistry
import com.itantra.domain.model.DownloadState
import com.itantra.domain.model.ModelPack
import com.itantra.ui.theme.iTantraBlack
import com.itantra.ui.theme.iTantraBlack40
import com.itantra.ui.theme.iTantraBlack60
import com.itantra.ui.theme.iTantraBorder
import com.itantra.ui.theme.iTantraCardAlt
import com.itantra.ui.theme.iTantraSuccess
import com.itantra.ui.theme.iTantraWhite

/**
 * Reusable gate card shown when a screen requires models not yet downloaded.
 * Shows per-pack status and a single "Download the Pack" action.
 */
@Composable
fun ModelDownloadGate(
    requiredPacks: List<ModelPack>,
    downloadStates: Map<ModelPack, DownloadState>,
    onDownloadAll: () -> Unit,
    onSkip: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(24.dp))
            .background(iTantraCardAlt)
            .border(1.5.dp, iTantraBorder, RoundedCornerShape(24.dp))
            .padding(22.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = Icons.Outlined.CloudDownload,
                contentDescription = null,
                tint = iTantraBlack,
                modifier = Modifier.size(24.dp)
            )
            Spacer(Modifier.width(10.dp))
            Column {
                Text(
                    text = "Core Models Required",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                    color = iTantraBlack
                )
                Text(
                    text = "Compulsion · Required for P2P speech communication",
                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                    color = iTantraBlack60
                )
            }
        }

        Spacer(Modifier.height(8.dp))

        Text(
            text = "To enable 100% offline speech transcription and local synthesis, download the core neural bundle (~169 MB).",
            style = MaterialTheme.typography.bodySmall,
            color = iTantraBlack60
        )

        Spacer(Modifier.height(16.dp))

        requiredPacks.forEach { pack ->
            val state = downloadStates[pack] ?: DownloadState.NotDownloaded
            val info = ModelRegistry.getInfo(pack)
            ModelPackRow(pack = pack, state = state, sizeLabel = info?.let { "${it.sizeBytes / 1_000_000}MB" } ?: "")
            Spacer(Modifier.height(8.dp))
        }

        Spacer(Modifier.height(14.dp))

        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Button(
                onClick = onDownloadAll,
                modifier = Modifier
                    .weight(1f)
                    .height(48.dp),
                shape = RoundedCornerShape(24.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = iTantraBlack,
                    contentColor = iTantraWhite
                )
            ) {
                Text(
                    text = "Download the Pack",
                    style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold)
                )
            }
            if (onSkip != null) {
                TextButton(onClick = onSkip) {
                    Text("Skip for Now", color = iTantraBlack60)
                }
            }
        }
    }
}

@Composable
private fun ModelPackRow(pack: ModelPack, state: DownloadState, sizeLabel: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = pack.displayName,
                style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Medium),
                color = iTantraBlack
            )
            when (state) {
                is DownloadState.Downloading -> {
                    LinearProgressIndicator(
                        progress = { state.progressPercent / 100f },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(4.dp)
                            .clip(RoundedCornerShape(2.dp)),
                        color = iTantraBlack,
                        trackColor = iTantraBorder
                    )
                }
                is DownloadState.Downloaded -> {
                    Text("Installed ✓", style = MaterialTheme.typography.labelSmall, color = iTantraSuccess)
                }
                else -> {
                    Text(sizeLabel, style = MaterialTheme.typography.labelSmall, color = iTantraBlack40)
                }
            }
        }
        Spacer(Modifier.width(12.dp))
        when (state) {
            is DownloadState.Downloaded -> {
                Icon(Icons.Filled.CheckCircle, contentDescription = "Downloaded", tint = iTantraSuccess, modifier = Modifier.size(18.dp))
            }
            is DownloadState.Downloading -> {
                Text(
                    text = "${state.progressPercent.toInt()}%",
                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                    color = iTantraBlack
                )
            }
            else -> {}
        }
    }
}
