package com.itantra.ui.screen

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.wifi.WifiManager
import android.os.Build
import android.provider.Settings
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.itantra.domain.model.ConnectionType
import com.itantra.domain.model.Direction
import com.itantra.domain.model.DownloadState
import com.itantra.domain.model.MessageType
import com.itantra.domain.model.ModelPack
import com.itantra.domain.model.PeerDevice
import com.itantra.domain.model.TransceiverMessage
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

private fun enableMeshHardwareAndHost(context: Context, viewModel: MainViewModel) {
    val wifiManager = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager
    if (wifiManager?.isWifiEnabled != true) {
        Toast.makeText(context, "Please turn ON Wi-Fi for Mesh Host Beacon", Toast.LENGTH_LONG).show()
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                context.startActivity(Intent(Settings.Panel.ACTION_WIFI).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            } else {
                context.startActivity(Intent(Settings.ACTION_WIFI_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            }
        } catch (e: Exception) {
            context.startActivity(Intent(Settings.ACTION_WIFI_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        }
    }
    viewModel.setHosting(true)
    Toast.makeText(context, "Mesh Beacon Started — Broadcasting on Wi-Fi Direct & Bluetooth", Toast.LENGTH_SHORT).show()
}

@Composable
fun TransceiverScreen(
    viewModel: MainViewModel,
    onPeerSelected: (String) -> Unit,
    onNavigateToDownloads: () -> Unit
) {
    val context = LocalContext.current
    val downloadStates by viewModel.downloadStates.collectAsState()
    val isHosting by viewModel.isHosting.collectAsState()
    val isDiscovering by viewModel.isDiscovering.collectAsState()
    val peers by viewModel.knownPeers.collectAsState()
    val detectedLanguage by viewModel.detectedLanguage.collectAsState()
    val isAutoDetect by viewModel.isAutoDetectEnabled.collectAsState()

    val corePacks = ModelPack.coreTransceiverPacks()
    val coreReady = corePacks.all { downloadStates[it] is DownloadState.Downloaded }

    var isPttActive by remember { mutableStateOf(false) }
    val isPttTransmitting by viewModel.isPttTransmitting.collectAsState()
    val isPhoneMode by viewModel.isPhoneMode.collectAsState()
    val pttMessages by viewModel.pttMessageLog.collectAsState()

    val meshPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val fineLocationGranted = permissions[Manifest.permission.ACCESS_FINE_LOCATION] ?: (
            ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
        )
        val nearbyGranted = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            permissions[Manifest.permission.NEARBY_WIFI_DEVICES] ?: (
                ContextCompat.checkSelfPermission(context, Manifest.permission.NEARBY_WIFI_DEVICES) == PackageManager.PERMISSION_GRANTED
            )
        } else true

        if (fineLocationGranted && nearbyGranted) {
            enableMeshHardwareAndHost(context, viewModel)
        } else {
            Toast.makeText(context, "Location & Nearby Devices permission required to host beacon", Toast.LENGTH_LONG).show()
        }
    }

    fun onHostBeaconClicked() {
        if (isHosting) {
            viewModel.setHosting(false)
            Toast.makeText(context, "Host Beacon Stopped", Toast.LENGTH_SHORT).show()
            return
        }

        val requiredPermissions = mutableListOf(
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION
        )
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            requiredPermissions.add(Manifest.permission.NEARBY_WIFI_DEVICES)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            requiredPermissions.add(Manifest.permission.BLUETOOTH_ADVERTISE)
            requiredPermissions.add(Manifest.permission.BLUETOOTH_CONNECT)
            requiredPermissions.add(Manifest.permission.BLUETOOTH_SCAN)
        }

        val missing = requiredPermissions.filter {
            ContextCompat.checkSelfPermission(context, it) != PackageManager.PERMISSION_GRANTED
        }

        if (missing.isNotEmpty()) {
            meshPermissionLauncher.launch(missing.toTypedArray())
        } else {
            enableMeshHardwareAndHost(context, viewModel)
        }
    }

    fun onSearchPeersClicked() {
        if (isDiscovering) {
            viewModel.setDiscovering(false)
            return
        }

        val requiredPermissions = mutableListOf(
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION
        )
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            requiredPermissions.add(Manifest.permission.NEARBY_WIFI_DEVICES)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            requiredPermissions.add(Manifest.permission.BLUETOOTH_SCAN)
            requiredPermissions.add(Manifest.permission.BLUETOOTH_CONNECT)
        }

        val missing = requiredPermissions.filter {
            ContextCompat.checkSelfPermission(context, it) != PackageManager.PERMISSION_GRANTED
        }

        if (missing.isNotEmpty()) {
            meshPermissionLauncher.launch(missing.toTypedArray())
        } else {
            val wifiManager = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager
            if (wifiManager?.isWifiEnabled != true) {
                Toast.makeText(context, "Turn ON Wi-Fi for peer scanning", Toast.LENGTH_SHORT).show()
            }
            viewModel.setDiscovering(true)
            Toast.makeText(context, "Scanning for mesh peers...", Toast.LENGTH_SHORT).show()
        }
    }

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
                    sublabel = if (isHosting) "Broadcasting Active" else "Discoverable to peers",
                    checked = isHosting,
                    onCheckedChange = { onHostBeaconClicked() },
                    modifier = Modifier.weight(1f)
                )
                TransceiverToggleCard(
                    label = "Search Peers",
                    sublabel = if (isDiscovering) "Scanning Nodes..." else "Scan for nodes",
                    checked = isDiscovering,
                    onCheckedChange = { onSearchPeersClicked() },
                    modifier = Modifier.weight(1f)
                )
            }

            // ── Mode Switcher & Emergency SOS Row ───────────────────────
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Mode Toggle Pill
                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(20.dp))
                        .background(if (isPhoneMode) Color(0x1515803D) else iTantraCardAlt)
                        .border(1.dp, if (isPhoneMode) iTantraSuccess else iTantraBorder, RoundedCornerShape(20.dp))
                        .clickable { viewModel.setPhoneMode(!isPhoneMode) }
                        .padding(horizontal = 14.dp, vertical = 7.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = if (isPhoneMode) Icons.Filled.Mic else Icons.Filled.GraphicEq,
                        contentDescription = null,
                        tint = if (isPhoneMode) iTantraSuccess else iTantraBlack,
                        modifier = Modifier.size(14.dp)
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(
                        text = if (isPhoneMode) "Phone Mode (Hands-Free VAD)" else "Walkie-Talkie (PTT)",
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold, fontSize = 11.sp),
                        color = if (isPhoneMode) iTantraSuccess else iTantraBlack
                    )
                }

                // Emergency Distress SOS Broadcast Button
                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(20.dp))
                        .background(Color(0xFFDC2626))
                        .clickable { viewModel.broadcastAlert("EMERGENCY SOS: Distress alert broadcasted via mesh!") }
                        .padding(horizontal = 12.dp, vertical = 7.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "SOS ALERT",
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold, fontSize = 11.sp),
                        color = iTantraWhite
                    )
                }
            }

            // ── MAIN HERO: Dynamic PTT / Phone Mode Button ───────────
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
                    val activeColor = if (isPhoneMode) iTantraSuccess else (if (isPttActive) iTantraError else iTantraBlack)
                    val activeBg = if (isPhoneMode) Color(0x1F15803D) else (if (isPttActive) Color(0x1FDC2626) else Color(0xFFF3F4F6))

                    // Outer ambient ring
                    Box(
                        modifier = Modifier
                            .size(230.dp)
                            .scale(if (isPttActive || isPhoneMode) 1.05f else pulseScale)
                            .clip(CircleShape)
                            .background(activeBg),
                        contentAlignment = Alignment.Center
                    ) {
                        // Middle ring
                        Box(
                            modifier = Modifier
                                .size(185.dp)
                                .clip(CircleShape)
                                .background(if (isPhoneMode) Color(0x2215803D) else (if (isPttActive) Color(0x33DC2626) else iTantraWhite))
                                .border(
                                    2.dp,
                                    if (isPhoneMode) iTantraSuccess else (if (isPttActive) iTantraError else iTantraBorder),
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
                                    .background(if (isPhoneMode) Color(0xFF15803D) else (if (isPttActive) iTantraError else iTantraBlack))
                                    .border(
                                        2.dp,
                                        if (isPhoneMode) Color(0xFF22C55E) else (if (isPttActive) Color(0xFFF87171) else iTantraBlack),
                                        CircleShape
                                    )
                                    .pointerInput(isPhoneMode) {
                                        if (!isPhoneMode) {
                                            detectTapGestures(
                                                onPress = {
                                                    isPttActive = true
                                                    viewModel.startPttTransmit()
                                                    tryAwaitRelease()
                                                    isPttActive = false
                                                    viewModel.stopPttTransmit()
                                                }
                                            )
                                        }
                                    },
                                contentAlignment = Alignment.Center
                            ) {
                                Column(
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.Center
                                ) {
                                    Icon(
                                        imageVector = if (isPhoneMode || isPttActive) Icons.Filled.Mic else Icons.Filled.GraphicEq,
                                        contentDescription = if (isPhoneMode) "Hands-free VAD Active" else "Push to Talk",
                                        tint = iTantraWhite,
                                        modifier = Modifier.size(46.dp)
                                    )
                                    Spacer(Modifier.height(4.dp))
                                    Text(
                                        text = if (isPhoneMode) "VAD ACTIVE" else (if (isPttActive) "RELEASE" else "HOLD PTT"),
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
                        text = if (isPhoneMode) "Hands-Free Phone Mode (VAD Listening)" else (if (isPttActive) "Transmitting Audio Data…" else "Push to Talk (Walkie-Talkie)"),
                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                        color = if (isPhoneMode) iTantraSuccess else (if (isPttActive) iTantraError else iTantraBlack)
                    )
                    Spacer(Modifier.height(3.dp))
                    Text(
                        text = if (isPhoneMode) "Automatic pause & stoppage detection (<800ms) streams text" else (if (isPttTransmitting) "VAD → STT → Broadcasting Protobuf Frame" else "Silero VAD → Whisper STT → ~200B Protobuf Frame"),
                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                        color = if (isPhoneMode || isPttTransmitting) iTantraSuccess else iTantraBlack60
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
                                    onClick = { if (peer.isConnected) onPeerSelected(peer.deviceId) },
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
