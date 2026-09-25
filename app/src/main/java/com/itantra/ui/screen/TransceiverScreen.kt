package com.itantra.ui.screen

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
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
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MicOff
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.SignalWifi4Bar
import androidx.compose.material.icons.filled.Translate
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.outlined.PersonAdd
import androidx.compose.material.icons.outlined.Radio
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import android.widget.Toast
import com.itantra.core.service.ITantraForegroundService
import com.itantra.domain.model.ConnectionType
import com.itantra.domain.model.Direction
import com.itantra.domain.model.DownloadState
import com.itantra.domain.model.IndicLanguage
import com.itantra.domain.model.MessageType
import com.itantra.domain.model.ModelPack
import com.itantra.domain.model.PeerDevice
import com.itantra.domain.model.TransceiverMessage
import com.itantra.ui.MainViewModel
import com.itantra.ui.component.ModelDownloadGate
import com.itantra.ui.component.STT_LANGUAGES
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
    val isHosting by viewModel.isHostingEffective.collectAsState()
    val isDiscovering by viewModel.isDiscovering.collectAsState()
    val peers by viewModel.knownPeers.collectAsState()
    val networkState by viewModel.networkState.collectAsState()
    val pipelineStage by viewModel.pipelineStage.collectAsState()
    val selectedLanguage by viewModel.selectedLanguage.collectAsState()
    val isPhoneMode by viewModel.isPhoneMode.collectAsState()
    val alertArmed by viewModel.alertArmed.collectAsState()
    val messages by viewModel.messageLog.collectAsState()

    // ── Paired Bluetooth devices (BluetoothRFCOMMManager.connectToDevice needs a real
    // BluetoothDevice, which only bonded-device enumeration can supply without a scan) ──
    val context = LocalContext.current
    var bondedBtDevices by remember { mutableStateOf<List<PeerDevice>>(emptyList()) }
    fun refreshBondedBtDevices() { bondedBtDevices = viewModel.bondedBluetoothDevices() }
    val btPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted -> if (granted) refreshBondedBtDevices() }
    LaunchedEffect(Unit) {
        val hasBtConnect = Build.VERSION.SDK_INT < Build.VERSION_CODES.S ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED
        if (hasBtConnect) {
            refreshBondedBtDevices()
        } else {
            btPermissionLauncher.launch(Manifest.permission.BLUETOOTH_CONNECT)
        }
    }

    val corePacks = ModelPack.coreTransceiverPacks(selectedLanguage)
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
            .background(iTantraBackground)
    ) {
        // ── Top Header Bar (Fixed at top, statusBarsPadding) ─────────
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Radio Transceiver",
                    style = MaterialTheme.typography.titleLarge.copy(
                        fontWeight = FontWeight.Bold,
                        fontSize = 20.sp
                    ),
                    color = iTantraBlack
                )
                Text(
                    text = when {
                        pipelineStage == ITantraForegroundService.PipelineStage.LISTENING -> "Listening…"
                        pipelineStage == ITantraForegroundService.PipelineStage.TRANSCRIBING -> "Transcribing speech…"
                        pipelineStage == ITantraForegroundService.PipelineStage.TRANSMITTING -> "Transmitting frame…"
                        pipelineStage == ITantraForegroundService.PipelineStage.RECEIVING -> "Receiving transmission…"
                        pipelineStage == ITantraForegroundService.PipelineStage.SPEAKING -> "Synthesizing voice…"
                        isHosting && isDiscovering -> "Beacon Active · Scanning"
                        isHosting -> "Beacon Active (Broadcasting)"
                        isDiscovering -> "Scanning for nearby peers…"
                        else -> "Radio Standby"
                    },
                    style = MaterialTheme.typography.labelSmall,
                    color = when {
                        pipelineStage != ITantraForegroundService.PipelineStage.IDLE -> iTantraError
                        isHosting || isDiscovering -> iTantraSuccessLight
                        else -> iTantraBlack60
                    }
                )
            }

            // Connection Badge Pill
            val (badgeText, badgeBg, badgeFg) = when (networkState) {
                "CONNECTED_WIFI" -> Triple("● Connected · Wi-Fi", Color(0xFFDCFCE7), iTantraSuccess)
                "CONNECTED_BLUETOOTH" -> Triple("● Connected · BT", Color(0xFFDCFCE7), iTantraSuccess)
                "CONNECTING" -> Triple("◌ Connecting…", Color(0xFFFEF3C7), Color(0xFFD97706))
                "DISCOVERING" -> Triple("◌ Scanning…", Color(0xFFE0F2FE), Color(0xFF0284C7))
                else -> Triple("○ Not Connected", iTantraCardAlt, iTantraBlack60)
            }
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(16.dp))
                    .background(badgeBg)
                    .border(1.dp, badgeFg.copy(alpha = 0.25f), RoundedCornerShape(16.dp))
                    .padding(horizontal = 10.dp, vertical = 6.dp)
            ) {
                Text(
                    text = badgeText,
                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp, fontWeight = FontWeight.SemiBold),
                    color = badgeFg
                )
            }
        }

        if (!coreReady) {
            // Model Gate
            ModelDownloadGate(
                requiredPacks = corePacks,
                downloadStates = downloadStates,
                onDownloadAll = onNavigateToDownloads,
                modifier = Modifier.padding(horizontal = 16.dp)
            )
        } else {
            // ── Scrollable Body: Content never gets squished or cut off! ──
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp, vertical = 4.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                // ── Row 1: Mode & Emergency Toggles (T37 & T66) ────────
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    // Phone Mode Card (T37)
                    Row(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(16.dp))
                            .background(if (isPhoneMode) Color(0xFFF0FDF4) else iTantraWhite)
                            .border(
                                1.dp,
                                if (isPhoneMode) iTantraSuccess else iTantraBorder,
                                RoundedCornerShape(16.dp)
                            )
                            .padding(horizontal = 10.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Phone Mode",
                                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                                color = if (isPhoneMode) iTantraSuccess else iTantraBlack
                            )
                            Text(
                                text = if (isPhoneMode) "Continuous" else "Push-to-Talk",
                                style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp),
                                color = iTantraBlack60
                            )
                        }
                        Switch(
                            checked = isPhoneMode,
                            onCheckedChange = { viewModel.setPhoneMode(it) },
                            modifier = Modifier.scale(0.75f).height(20.dp),
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = iTantraWhite,
                                checkedTrackColor = iTantraSuccess,
                                uncheckedThumbColor = iTantraBlack60,
                                uncheckedTrackColor = iTantraBorder
                            )
                        )
                    }

                    // Alert Mode Card (T66)
                    Row(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(16.dp))
                            .background(if (alertArmed) Color(0xFFFEF2F2) else iTantraWhite)
                            .border(
                                1.dp,
                                if (alertArmed) iTantraError else iTantraBorder,
                                RoundedCornerShape(16.dp)
                            )
                            .padding(horizontal = 10.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Next as ALERT",
                                style = MaterialTheme.typography.labelMedium.copy(
                                    fontWeight = if (alertArmed) FontWeight.Bold else FontWeight.SemiBold
                                ),
                                color = if (alertArmed) iTantraError else iTantraBlack
                            )
                            Text(
                                text = if (alertArmed) "Max alarm volume" else "Normal audio",
                                style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp),
                                color = if (alertArmed) iTantraError else iTantraBlack60
                            )
                        }
                        Switch(
                            checked = alertArmed,
                            onCheckedChange = { viewModel.setAlertArmed(it) },
                            modifier = Modifier.scale(0.75f).height(20.dp),
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = iTantraWhite,
                                checkedTrackColor = iTantraError,
                                uncheckedThumbColor = iTantraBlack60,
                                uncheckedTrackColor = iTantraBorder
                            )
                        )
                    }
                }

                // ── Row 2: Mesh Network Action Cards (Host & Search) ─────
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    TransceiverToggleCard(
                        label = "Host Beacon",
                        sublabel = if (isHosting) "Broadcasting group" else "Off · Tap to host",
                        checked = isHosting,
                        onCheckedChange = { viewModel.setHosting(it) },
                        modifier = Modifier.weight(1f)
                    )
                    TransceiverToggleCard(
                        label = "Search Peers",
                        sublabel = if (isDiscovering) "Scanning nearby" else "Off · Tap to scan",
                        checked = isDiscovering,
                        onCheckedChange = { viewModel.setDiscovering(it) },
                        modifier = Modifier.weight(1f)
                    )
                }

                // ── Row 3: Language Chips Bar (T72) ──────────────────────
                Column(modifier = Modifier.fillMaxWidth()) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(bottom = 6.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Filled.Translate, contentDescription = null, tint = iTantraBlack, modifier = Modifier.size(13.dp))
                            Spacer(Modifier.width(6.dp))
                            Text(
                                text = "Language (STT & TTS)",
                                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                color = iTantraBlack
                            )
                        }
                        Text(
                            text = IndicLanguage.fromCode(selectedLanguage).nativeName,
                            style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp, fontWeight = FontWeight.Bold),
                            color = iTantraSuccess
                        )
                    }
                    LazyRow(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        items(STT_LANGUAGES, key = { it.first }) { (code, label) ->
                            val isSelected = code == selectedLanguage
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(
                                        if (isSelected) (if (isPhoneMode) iTantraBlack40 else iTantraBlack)
                                        else iTantraWhite
                                    )
                                    .border(
                                        1.dp,
                                        if (isSelected) (if (isPhoneMode) iTantraBlack40 else iTantraBlack)
                                        else iTantraBorder,
                                        RoundedCornerShape(12.dp)
                                    )
                                    .clickable(enabled = !isPttActive && !isPhoneMode) { viewModel.setManualLanguage(code) }
                                    .padding(horizontal = 10.dp, vertical = 5.dp)
                            ) {
                                Text(
                                    text = label,
                                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp),
                                    color = if (isSelected) iTantraWhite else if (isPhoneMode) iTantraBlack40 else iTantraBlack60
                                )
                            }
                        }
                    }
                }

                // ── Hero Push-To-Talk / Phone Mode Centerpiece ───────────
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 8.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        // Ambient ring
                        Box(
                            modifier = Modifier
                                .size(190.dp)
                                .scale(if (isPttActive) 1.06f else if (isPhoneMode) 1f else pulseScale)
                                .clip(CircleShape)
                                .background(
                                    if (isPttActive || alertArmed) Color(0x1FDC2626)
                                    else if (isPhoneMode) Color(0xFFF0FDF4)
                                    else Color(0xFFF3F4F6)
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            // Middle tactile ring
                            Box(
                                modifier = Modifier
                                    .size(155.dp)
                                    .clip(CircleShape)
                                    .background(
                                        if (isPttActive) Color(0x33DC2626)
                                        else if (alertArmed) Color(0x1FDC2626)
                                        else if (isPhoneMode) Color(0xFFDCFCE7)
                                        else iTantraWhite
                                    )
                                    .border(
                                        2.dp,
                                        if (isPttActive || alertArmed) iTantraError
                                        else if (isPhoneMode) iTantraSuccess.copy(alpha = 0.5f)
                                        else iTantraBorder,
                                        CircleShape
                                    ),
                                contentAlignment = Alignment.Center
                            ) {
                                // Core action button with robust pointerInput (awaitEachGesture)
                                Box(
                                    modifier = Modifier
                                        .size(125.dp)
                                        .shadow(if (isPhoneMode) 0.dp else 6.dp, CircleShape)
                                        .clip(CircleShape)
                                        .background(
                                            if (isPttActive || alertArmed) iTantraError
                                            else if (isPhoneMode) Color(0xFFF9FAFB)
                                            else iTantraBlack
                                        )
                                        .border(
                                            2.dp,
                                            if (isPttActive || alertArmed) Color(0xFFF87171)
                                            else if (isPhoneMode) iTantraBorder
                                            else iTantraBlack,
                                            CircleShape
                                        )
                                        .pointerInput(isPhoneMode) {
                                            if (!isPhoneMode) {
                                                awaitEachGesture {
                                                    awaitFirstDown(requireUnconsumed = false)
                                                    isPttActive = true
                                                    viewModel.startTransceiverPtt()
                                                    waitForUpOrCancellation()
                                                    isPttActive = false
                                                    viewModel.stopTransceiverPtt()
                                                }
                                            }
                                        },
                                    contentAlignment = Alignment.Center
                                ) {
                                    Column(
                                        horizontalAlignment = Alignment.CenterHorizontally,
                                        verticalArrangement = Arrangement.Center
                                    ) {
                                        Icon(
                                            imageVector = if (isPhoneMode) Icons.Filled.Phone
                                                else if (isPttActive || alertArmed) Icons.Filled.Mic
                                                else Icons.Filled.GraphicEq,
                                            contentDescription = if (isPhoneMode) "Phone mode active" else if (alertArmed) "Send Alert" else "Push to Talk",
                                            tint = if (isPhoneMode) iTantraSuccess else iTantraWhite,
                                            modifier = Modifier.size(38.dp)
                                        )
                                        Spacer(Modifier.height(4.dp))
                                        Text(
                                            text = if (isPttActive) "RELEASE"
                                                else if (isPhoneMode) "PHONE MODE"
                                                else if (alertArmed) "SEND ALERT"
                                                else "HOLD PTT",
                                            style = MaterialTheme.typography.labelSmall.copy(
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 11.sp,
                                                letterSpacing = 1.2.sp
                                            ),
                                            color = if (isPhoneMode) iTantraSuccess else iTantraWhite
                                        )
                                    }
                                }
                            }
                        }

                        Spacer(Modifier.height(10.dp))

                        Text(
                            text = if (isPttActive) "Transmitting Audio Data…"
                                else if (alertArmed) "Emergency Alert Mode (Alarm Volume)"
                                else if (isPhoneMode) "Continuous Hands-Free (Phone Mode)"
                                else "Push to Talk (Walkie-Talkie)",
                            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                            color = if (isPttActive || alertArmed) iTantraError else iTantraBlack
                        )
                        Text(
                            text = if (isPhoneMode) "Continuous VAD-gated speech capture (PTT disabled)"
                                else if (alertArmed) "Spoken message broadcasts at highest volume non-interruptible"
                                else "Hold button to record & transmit · Release to send",
                            style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                            color = iTantraBlack60
                        )
                    }
                }

                // ── Transmissions Section (Voice Notes - T67) ────────────
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(18.dp))
                        .background(iTantraWhite)
                        .border(1.dp, iTantraBorder, RoundedCornerShape(18.dp))
                        .padding(14.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Transmissions (${messages.size})",
                            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                            color = iTantraBlack
                        )
                        if (messages.isNotEmpty()) {
                            Text(
                                text = "tap ▶ to replay",
                                style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                                color = iTantraBlack60
                            )
                        }
                    }

                    Spacer(Modifier.height(8.dp))

                    if (messages.isEmpty()) {
                        Text(
                            text = "No voice notes yet. Transmitted speech will appear here with instant audio replay.",
                            style = MaterialTheme.typography.bodySmall,
                            color = iTantraBlack40,
                            modifier = Modifier.padding(vertical = 12.dp)
                        )
                    } else {
                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            messages.forEach { msg ->
                                MessageBubbleItem(
                                    message = msg,
                                    onReplay = {
                                        if (!viewModel.replayVoiceNote(msg)) {
                                            Toast.makeText(context, "Voice note not ready yet", Toast.LENGTH_SHORT).show()
                                        }
                                    }
                                )
                            }
                        }
                    }
                }

                // ── Nearby & Connected Devices Section ───────────────────
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(18.dp))
                        .background(iTantraWhite)
                        .border(1.dp, iTantraBorder, RoundedCornerShape(18.dp))
                        .padding(14.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
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
                                text = if (peers.isEmpty()) "Nearby Devices" else "Nearby Devices (${peers.size})",
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

                    Spacer(Modifier.height(8.dp))

                    if (peers.isEmpty()) {
                        Column(modifier = Modifier.padding(vertical = 8.dp)) {
                            Text(
                                text = if (isDiscovering) "Scanning for nearby Wi-Fi Direct & Bluetooth devices…"
                                    else "No devices detected. Tap 'Search Peers' above or 'Host Beacon' on the other phone.",
                                style = MaterialTheme.typography.bodySmall,
                                color = iTantraBlack40
                            )
                            Spacer(Modifier.height(4.dp))
                            Text(
                                text = "Tip: Ensure Wi-Fi, Bluetooth, and Location are ON on both phones.",
                                style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                                color = iTantraBlack60
                            )
                        }
                    } else {
                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            peers.forEach { peer ->
                                PeerRowItemWhite(
                                    peer = peer,
                                    onClick = {
                                        when {
                                            peer.isConnected -> onPeerSelected(peer.deviceId)
                                            peer.connectionType == ConnectionType.BLUETOOTH ->
                                                viewModel.pairAndConnectBluetoothPeer(peer.deviceId)
                                            else -> viewModel.connectToPeer(peer.deviceId)
                                        }
                                    },
                                    onAuthorize = { viewModel.authorizePeer(peer.deviceId) },
                                    onRevoke = { viewModel.revokePeer(peer.deviceId) }
                                )
                            }
                        }
                    }

                    // ── Paired Bluetooth Devices ──
                    if (bondedBtDevices.isNotEmpty()) {
                        Spacer(Modifier.height(14.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Paired Bluetooth (${bondedBtDevices.size})",
                                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                                color = iTantraBlack
                            )
                            Text(
                                text = "tap to dial",
                                style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                                color = iTantraBlack60
                            )
                        }
                        Spacer(Modifier.height(8.dp))
                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            bondedBtDevices.forEach { peer ->
                                PeerRowItemWhite(
                                    peer = peer,
                                    onClick = { viewModel.connectToBluetoothPeer(peer.deviceId) },
                                    onAuthorize = { viewModel.authorizePeer(peer.deviceId) },
                                    onRevoke = { viewModel.revokePeer(peer.deviceId) }
                                )
                            }
                        }
                    }
                }

                Spacer(Modifier.height(16.dp))
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
            // Previously `enabled = peer.isConnected` disabled tapping entirely for an
            // unconnected peer — meaning "tap to connect" never actually fired a connect
            // attempt. Always clickable now; onClick itself branches on isConnected.
            .clickable(onClick = onClick)
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

@Composable
private fun MessageBubbleItem(
    message: TransceiverMessage,
    onReplay: () -> Unit
) {
    val isReceived = message.direction == Direction.RECEIVED
    val timeStr = remember(message.timestamp) {
        java.text.SimpleDateFormat("HH:mm", java.util.Locale.getDefault()).format(java.util.Date(message.timestamp))
    }

    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = if (isReceived) Alignment.Start else Alignment.End
    ) {
        Box(
            modifier = Modifier
                .clip(
                    RoundedCornerShape(
                        topStart = 16.dp,
                        topEnd = 16.dp,
                        bottomStart = if (isReceived) 4.dp else 16.dp,
                        bottomEnd = if (isReceived) 16.dp else 4.dp
                    )
                )
                .background(if (isReceived) iTantraWhite else iTantraBlack)
                .border(
                    1.dp,
                    if (message.type == MessageType.ALERT) iTantraError
                    else if (isReceived) iTantraBorder
                    else iTantraBlack,
                    RoundedCornerShape(16.dp)
                )
                .padding(horizontal = 12.dp, vertical = 8.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                if (isReceived) {
                    // Small play icon button on RECEIVED message bubbles (T67)
                    IconButton(
                        onClick = onReplay,
                        modifier = Modifier.size(28.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Filled.PlayArrow,
                            contentDescription = "Replay voice note",
                            tint = if (message.type == MessageType.ALERT) iTantraError else iTantraBlack,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
                Column {
                    if (message.type == MessageType.ALERT) {
                        Text(
                            text = "ALERT",
                            style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp, fontWeight = FontWeight.Bold),
                            color = iTantraError
                        )
                    }
                    Text(
                        text = message.text,
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (isReceived) iTantraBlack else iTantraWhite
                    )
                    Spacer(Modifier.height(2.dp))
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "[${message.srcLang.uppercase()}]",
                            style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp, fontWeight = FontWeight.Bold),
                            color = if (isReceived) iTantraBlack60 else Color(0xFFD4D4D4)
                        )
                        Text(
                            text = timeStr,
                            style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp),
                            color = if (isReceived) iTantraBlack40 else Color(0xFF999999)
                        )
                    }
                }
            }
        }
    }
}
