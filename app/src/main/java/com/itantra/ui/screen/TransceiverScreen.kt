package com.itantra.ui.screen

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
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
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MicOff
import androidx.compose.material.icons.filled.SignalWifi4Bar
import androidx.compose.material.icons.filled.Translate
import androidx.compose.material.icons.outlined.PersonAdd
import androidx.compose.material.icons.outlined.Radio
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
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
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.itantra.domain.model.ConnectionType
import com.itantra.domain.model.DownloadState
import com.itantra.domain.model.ModelPack
import com.itantra.domain.model.PeerDevice
import com.itantra.ui.MainViewModel
import com.itantra.ui.component.ModelDownloadGate
import com.itantra.ui.theme.iTantraBackground
import com.itantra.ui.theme.iTantraBlack
import com.itantra.ui.theme.iTantraBlack40
import com.itantra.ui.theme.iTantraBlack60
import com.itantra.ui.theme.iTantraBlack80
import com.itantra.ui.theme.iTantraBorder
import com.itantra.ui.theme.iTantraCard
import com.itantra.ui.theme.iTantraCardAlt
import com.itantra.ui.theme.iTantraDivider
import com.itantra.ui.theme.iTantraError
import com.itantra.ui.theme.iTantraSuccess
import com.itantra.ui.theme.iTantraSuccessLight
import com.itantra.ui.theme.iTantraSurfaceHover
import com.itantra.ui.theme.iTantraWhite

@Composable
fun TransceiverScreen(
    viewModel: MainViewModel,
    onPeerSelected: (String) -> Unit,
    onNavigateToDownloads: () -> Unit
) {
    val downloadStates by viewModel.downloadStates.collectAsState()
    val isHosting by viewModel.isHosting.collectAsState()
    val isDiscovering by viewModel.isDiscovering.collectAsState()
    val peers by viewModel.knownPeers.collectAsState()
    val detectedLanguage by viewModel.detectedLanguage.collectAsState()
    val isAutoDetect by viewModel.isAutoDetectEnabled.collectAsState()

    val corePacks = ModelPack.coreTransceiverPacks()
    val coreReady = corePacks.all { downloadStates[it] is DownloadState.Downloaded }

    var isPttActive by remember { mutableStateOf(false) }

    val infiniteTransition = rememberInfiniteTransition(label = "transceiver_pulse")
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 1.08f,
        animationSpec = infiniteRepeatable(tween(1600, easing = LinearEasing), RepeatMode.Reverse),
        label = "pulse"
    )
    val scanRotation by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(tween(3000, easing = LinearEasing), RepeatMode.Restart),
        label = "scan"
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(iTantraBackground),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // ── Header Bar ─────────────────────────────────────────────
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(horizontal = 20.dp, vertical = 14.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = "Radio Transceiver",
                    style = MaterialTheme.typography.titleLarge.copy(
                        fontWeight = FontWeight.Bold,
                        fontSize = 22.sp
                    ),
                    color = iTantraBlack
                )
                Text(
                    text = when {
                        isPttActive -> "Transmitting Voice…"
                        isHosting && isDiscovering -> "Mesh Beacon · Scanning Active"
                        isHosting -> "Beacon Active (Broadcasting)"
                        isDiscovering -> "Scanning for nearby peers…"
                        else -> "Radio Standby"
                    },
                    style = MaterialTheme.typography.labelSmall,
                    color = when {
                        isPttActive -> iTantraError
                        isHosting || isDiscovering -> iTantraSuccessLight
                        else -> iTantraBlack60
                    }
                )
            }

            // Language auto-detect pill
            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(20.dp))
                    .background(iTantraCardAlt)
                    .border(1.dp, iTantraBorder, RoundedCornerShape(20.dp))
                    .clickable { viewModel.setAutoDetect(!isAutoDetect) }
                    .padding(horizontal = 12.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Filled.Translate, contentDescription = null, tint = iTantraBlack, modifier = Modifier.size(13.dp))
                Spacer(Modifier.width(5.dp))
                Text(
                    text = if (isAutoDetect) "Auto · ${detectedLanguage?.uppercase() ?: "?"}" else "Manual",
                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp, fontWeight = FontWeight.SemiBold),
                    color = iTantraBlack
                )
            }
        }

        if (!coreReady) {
            // Model Gate
            ModelDownloadGate(
                requiredPacks = corePacks,
                downloadStates = downloadStates,
                onDownloadAll = onNavigateToDownloads,
                modifier = Modifier.padding(horizontal = 20.dp)
            )
        } else {
            // Divider
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(1.dp)
                    .background(iTantraDivider)
            )

            // ── Host & Search Compact Control Row ───────────────────
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 10.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                TransceiverToggleCard(
                    label = "Host Beacon",
                    sublabel = "Discoverable to peers",
                    checked = isHosting,
                    onCheckedChange = { viewModel.setHosting(it) },
                    modifier = Modifier.weight(1f)
                )
                TransceiverToggleCard(
                    label = "Search Peers",
                    sublabel = "Scan for nodes",
                    checked = isDiscovering,
                    onCheckedChange = { viewModel.setDiscovering(it) },
                    modifier = Modifier.weight(1f)
                )
            }

            // ── MAIN HERO: Giant Centered PTT Button ────────────────
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    // Outer ambient ring
                    Box(
                        modifier = Modifier
                            .size(230.dp)
                            .scale(if (isPttActive) 1.05f else pulseScale)
                            .clip(CircleShape)
                            .background(if (isPttActive) Color(0x1FDC2626) else Color(0xFFF3F4F6)),
                        contentAlignment = Alignment.Center
                    ) {
                        // Middle ring
                        Box(
                            modifier = Modifier
                                .size(185.dp)
                                .clip(CircleShape)
                                .background(if (isPttActive) Color(0x33DC2626) else iTantraWhite)
                                .border(
                                    2.dp,
                                    if (isPttActive) iTantraError else iTantraBorder,
                                    CircleShape
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            // Core tactile button
                            Box(
                                modifier = Modifier
                                    .size(145.dp)
                                    .shadow(8.dp, CircleShape)
                                    .clip(CircleShape)
                                    .background(if (isPttActive) iTantraError else iTantraBlack)
                                    .border(
                                        2.dp,
                                        if (isPttActive) Color(0xFFF87171) else iTantraBlack,
                                        CircleShape
                                    )
                                    .pointerInput(Unit) {
                                        detectTapGestures(
                                            onPress = {
                                                isPttActive = true
                                                viewModel.startTransceiverPtt()
                                                tryAwaitRelease()
                                                isPttActive = false
                                                viewModel.stopTransceiverPtt()
                                            }
                                        )
                                    },
                                contentAlignment = Alignment.Center
                            ) {
                                Column(
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.Center
                                ) {
                                    Icon(
                                        imageVector = if (isPttActive) Icons.Filled.Mic else Icons.Filled.GraphicEq,
                                        contentDescription = "Push to Talk",
                                        tint = iTantraWhite,
                                        modifier = Modifier.size(46.dp)
                                    )
                                    Spacer(Modifier.height(4.dp))
                                    Text(
                                        text = if (isPttActive) "RELEASE" else "HOLD PTT",
                                        style = MaterialTheme.typography.labelSmall.copy(
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 11.sp,
                                            letterSpacing = 1.5.sp
                                        ),
                                        color = iTantraWhite
                                    )
                                }
                            }
                        }
                    }

                    Spacer(Modifier.height(16.dp))

                    Text(
                        text = if (isPttActive) "Transmitting Audio Data…" else "Push to Talk (Walkie-Talkie)",
                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                        color = if (isPttActive) iTantraError else iTantraBlack
                    )
                    Spacer(Modifier.height(3.dp))
                    Text(
                        text = "Silero VAD → IndicConformer STT → ~200B Protobuf Frame",
                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                        color = iTantraBlack60
                    )
                }
            }

            // ── Nearby Peer Drawer (Bottom of screen) ───────────────
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp))
                    .background(iTantraCardAlt)
                    .border(1.dp, iTantraBorder, RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp))
            ) {
                Column {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            if (isDiscovering) {
                                Icon(
                                    Icons.Outlined.Radio,
                                    contentDescription = null,
                                    tint = iTantraBlack,
                                    modifier = Modifier.size(14.dp).rotate(scanRotation)
                                )
                                Spacer(Modifier.width(6.dp))
                            }
                            Text(
                                text = if (peers.isEmpty()) "No devices found nearby" else "Nearby Devices (${peers.size})",
                                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                                color = iTantraBlack
                            )
                        }
                        if (peers.isNotEmpty()) {
                            Text(
                                text = "tap to connect",
                                style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                                color = iTantraBlack60
                            )
                        }
                    }

                    if (peers.isNotEmpty()) {
                        LazyColumn(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(minOf(peers.size * 72, 216).dp),
                            contentPadding = PaddingValues(start = 12.dp, end = 12.dp, bottom = 8.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            items(peers, key = { it.deviceId }) { peer ->
                                PeerRowItemWhite(
                                    peer = peer,
                                    onClick = {
                                        if (peer.isConnected) onPeerSelected(peer.deviceId)
                                        else viewModel.connectToPeer(peer.deviceId)
                                    },
                                    onAuthorize = { viewModel.authorizePeer(peer.deviceId) },
                                    onRevoke = { viewModel.revokePeer(peer.deviceId) }
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun TransceiverToggleCard(
    label: String,
    sublabel: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(16.dp))
            .background(if (checked) Color(0xFFF3F4F6) else iTantraWhite)
            .border(
                1.dp,
                if (checked) iTantraBlack else iTantraBorder,
                RoundedCornerShape(16.dp)
            )
            .padding(horizontal = 12.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
                color = iTantraBlack
            )
            Text(
                text = sublabel,
                style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                color = iTantraBlack60
            )
        }
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            modifier = Modifier.height(24.dp),
            colors = SwitchDefaults.colors(
                checkedThumbColor = iTantraWhite,
                checkedTrackColor = iTantraBlack,
                uncheckedThumbColor = iTantraBlack60,
                uncheckedTrackColor = iTantraBorder
            )
        )
    }
}

@Composable
private fun PeerRowItemWhite(
    peer: PeerDevice,
    onClick: () -> Unit,
    onAuthorize: () -> Unit,
    onRevoke: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(iTantraWhite)
            .border(
                1.dp,
                if (peer.isConnected) iTantraSuccess.copy(alpha = 0.5f) else iTantraBorder,
                RoundedCornerShape(14.dp)
            )
            .clickable(enabled = peer.isConnected, onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(32.dp)
                .clip(CircleShape)
                .background(iTantraCardAlt),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = if (peer.connectionType == ConnectionType.WIFI_DIRECT)
                    Icons.Filled.SignalWifi4Bar else Icons.Filled.Bluetooth,
                contentDescription = null,
                tint = iTantraBlack,
                modifier = Modifier.size(16.dp)
            )
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(peer.deviceName, style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold), color = iTantraBlack)
            Text(
                text = "${peer.connectionType.name.replace("_", " ")} · ${peer.rssi}dBm · Build: ${peer.deviceId.take(8)}",
                style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                color = iTantraBlack60
            )
        }
        IconButton(
            onClick = if (peer.isAuthorized) onRevoke else onAuthorize,
            modifier = Modifier.size(32.dp)
        ) {
            Icon(
                imageVector = if (peer.isAuthorized) Icons.Filled.CheckCircle else Icons.Outlined.PersonAdd,
                contentDescription = if (peer.isAuthorized) "Authorized" else "Authorize",
                tint = if (peer.isAuthorized) iTantraSuccess else iTantraBlack60,
                modifier = Modifier.size(18.dp)
            )
        }
    }
}
