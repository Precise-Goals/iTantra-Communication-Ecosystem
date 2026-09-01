package com.itantra.ui.screen

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.DeviceHub
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.LinkOff
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.itantra.domain.model.ConnectionMode
import com.itantra.domain.model.ConnectionType
import com.itantra.domain.model.PeerDevice
import com.itantra.ui.MainViewModel
import com.itantra.ui.theme.ActiveGreen400
import com.itantra.ui.theme.ActiveGreenGlow
import com.itantra.ui.theme.OnSurface
import com.itantra.ui.theme.OnSurfaceDim
import com.itantra.ui.theme.PeerDotBluetooth
import com.itantra.ui.theme.PeerDotWifi
import com.itantra.ui.theme.SignalOrange500
import com.itantra.ui.theme.SpaceBlue700
import com.itantra.ui.theme.SpaceBlue800
import com.itantra.ui.theme.Surface700
import com.itantra.ui.theme.SurfaceVariant
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun DashboardScreen(viewModel: MainViewModel) {
    val peers by viewModel.peersFlow.collectAsState()
    val networkState by viewModel.networkStateFlow.collectAsState()
    val connectionMode by viewModel.connectionMode.collectAsState()
    val ramUsage by viewModel.ramUsageMbFlow.collectAsState()

    val connectedPeers = peers.filter { it.isConnected }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Header
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = "iTantra",
                    style = MaterialTheme.typography.headlineMedium.copy(
                        color = SignalOrange500,
                        fontWeight = FontWeight.Bold
                    )
                )
                Text(
                    text = "Universal Mesh Communicator",
                    style = MaterialTheme.typography.bodySmall.copy(color = OnSurfaceDim)
                )
            }
            // Node count badge
            Box(
                modifier = Modifier
                    .background(
                        if (connectedPeers.isNotEmpty()) ActiveGreenGlow else Color.Transparent,
                        CircleShape
                    )
                    .border(1.dp, if (connectedPeers.isNotEmpty()) ActiveGreen400 else OnSurfaceDim, CircleShape)
                    .padding(horizontal = 12.dp, vertical = 6.dp)
            ) {
                Text(
                    text = "${connectedPeers.size} Node${if (connectedPeers.size != 1) "s" else ""}",
                    style = MaterialTheme.typography.labelMedium.copy(
                        color = if (connectedPeers.isNotEmpty()) ActiveGreen400 else OnSurfaceDim,
                        fontWeight = FontWeight.SemiBold
                    )
                )
            }
        }

        // Connection Status Card
        NetworkStatusCard(networkState = networkState, connectedCount = connectedPeers.size)

        // Mode Toggle
        ModeToggleCard(
            connectionMode = connectionMode,
            onModeChanged = { viewModel.setConnectionMode(it) }
        )

        // RAM Usage
        Card(
            colors = CardDefaults.cardColors(containerColor = SpaceBlue800),
            shape = RoundedCornerShape(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Memory Usage", style = MaterialTheme.typography.bodyMedium.copy(color = OnSurfaceDim))
                Text(
                    "${ramUsage.toInt()} MB",
                    style = MaterialTheme.typography.labelLarge.copy(
                        color = if (ramUsage > 350) MaterialTheme.colorScheme.error else ActiveGreen400,
                        fontWeight = FontWeight.SemiBold
                    )
                )
            }
        }

        // Peer List
        Text(
            text = "Discovered Peers (${peers.size})",
            style = MaterialTheme.typography.titleSmall.copy(color = OnSurfaceDim)
        )

        if (peers.isEmpty()) {
            EmptyPeersCard()
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(peers) { peer ->
                    PeerListItem(peer = peer, onConnectClick = { viewModel.connectToPeer(peer.deviceId) })
                }
            }
        }
    }
}

@Composable
private fun NetworkStatusCard(networkState: String, connectedCount: Int) {
    val isConnected = networkState.startsWith("CONNECTED")
    val isDiscovering = networkState == "DISCOVERING"

    val infiniteTransition = rememberInfiniteTransition(label = "status_pulse")
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 0.8f, targetValue = 1.2f,
        animationSpec = infiniteRepeatable(tween(1000), RepeatMode.Reverse),
        label = "pulse"
    )

    Card(
        colors = CardDefaults.cardColors(
            containerColor = if (isConnected) ActiveGreenGlow else SpaceBlue800
        ),
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp)
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Status dot
            Box(
                modifier = Modifier
                    .size(12.dp)
                    .scale(if (isDiscovering) pulseScale else 1f)
                    .background(
                        when {
                            isConnected -> ActiveGreen400
                            isDiscovering -> SignalOrange500
                            else -> OnSurfaceDim
                        },
                        CircleShape
                    )
            )
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = when (networkState) {
                        "CONNECTED_WIFI" -> "Wi-Fi Direct Active"
                        "CONNECTED_BLUETOOTH" -> "Bluetooth Active"
                        "DISCOVERING" -> "Searching for peers..."
                        "CONNECTING" -> "Connecting..."
                        "RECONNECTING" -> "Reconnecting..."
                        else -> "Not Connected"
                    },
                    style = MaterialTheme.typography.titleSmall.copy(color = OnSurface)
                )
                if (isConnected) {
                    Text(
                        "$connectedCount device${if (connectedCount != 1) "s" else ""} connected",
                        style = MaterialTheme.typography.bodySmall.copy(color = ActiveGreen400)
                    )
                }
            }
            Icon(
                imageVector = when {
                    networkState == "CONNECTED_BLUETOOTH" -> Icons.Filled.Bluetooth
                    isConnected -> Icons.Filled.Wifi
                    isConnected -> Icons.Filled.Link
                    else -> Icons.Filled.LinkOff
                },
                contentDescription = null,
                tint = if (isConnected) ActiveGreen400 else OnSurfaceDim,
                modifier = Modifier.size(24.dp)
            )
        }
    }
}

@Composable
private fun ModeToggleCard(connectionMode: ConnectionMode, onModeChanged: (ConnectionMode) -> Unit) {
    Card(
        colors = CardDefaults.cardColors(containerColor = SpaceBlue800),
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp)
    ) {
        Row(
            modifier = Modifier.padding(16.dp).fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text("Communication Mode", style = MaterialTheme.typography.titleSmall.copy(color = OnSurface))
                Text(
                    if (connectionMode == ConnectionMode.PUSH_TO_TALK) "Walkie-Talkie (PTT)" else "Phone Mode (Continuous)",
                    style = MaterialTheme.typography.bodySmall.copy(
                        color = if (connectionMode == ConnectionMode.PHONE_MODE) ActiveGreen400 else SignalOrange500
                    )
                )
            }
            Switch(
                checked = connectionMode == ConnectionMode.PHONE_MODE,
                onCheckedChange = { isPhone ->
                    onModeChanged(if (isPhone) ConnectionMode.PHONE_MODE else ConnectionMode.PUSH_TO_TALK)
                },
                colors = SwitchDefaults.colors(
                    checkedThumbColor = ActiveGreen400,
                    checkedTrackColor = ActiveGreenGlow,
                    uncheckedThumbColor = SignalOrange500,
                    uncheckedTrackColor = SignalOrange500.copy(alpha = 0.3f)
                )
            )
        }
    }
}

@Composable
private fun EmptyPeersCard() {
    Card(
        colors = CardDefaults.cardColors(containerColor = SpaceBlue800),
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp)
    ) {
        Box(
            modifier = Modifier.fillMaxWidth().padding(32.dp),
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(
                    Icons.Filled.DeviceHub, contentDescription = null,
                    tint = OnSurfaceDim, modifier = Modifier.size(40.dp)
                )
                Spacer(Modifier.height(8.dp))
                Text("Scanning for iTantra devices...", style = MaterialTheme.typography.bodyMedium.copy(color = OnSurfaceDim))
                Text("Ensure both devices have Wi-Fi Direct or Bluetooth enabled.", style = MaterialTheme.typography.bodySmall.copy(color = OnSurfaceDim.copy(alpha = 0.7f)))
            }
        }
    }
}

@Composable
private fun PeerListItem(peer: PeerDevice, onConnectClick: () -> Unit) {
    val bgColor by animateColorAsState(
        targetValue = if (peer.isConnected) ActiveGreenGlow else SpaceBlue700,
        animationSpec = tween(300),
        label = "peer_bg"
    )
    Card(
        colors = CardDefaults.cardColors(containerColor = bgColor),
        modifier = Modifier.fillMaxWidth().clickable(onClick = onConnectClick),
        shape = RoundedCornerShape(12.dp)
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(10.dp)
                    .background(
                        if (peer.connectionType == ConnectionType.WIFI_DIRECT) PeerDotWifi else PeerDotBluetooth,
                        CircleShape
                    )
            )
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(peer.deviceName, style = MaterialTheme.typography.bodyMedium.copy(color = OnSurface, fontWeight = FontWeight.Medium))
                Row {
                    Text(
                        if (peer.connectionType == ConnectionType.WIFI_DIRECT) "Wi-Fi Direct" else "Bluetooth",
                        style = MaterialTheme.typography.bodySmall.copy(color = OnSurfaceDim)
                    )
                    if (peer.latencyMs > 0) {
                        Text("  •  ${peer.latencyMs}ms", style = MaterialTheme.typography.bodySmall.copy(color = OnSurfaceDim))
                    }
                }
            }
            Text(
                if (peer.isConnected) "Connected" else "Tap to connect",
                style = MaterialTheme.typography.labelSmall.copy(
                    color = if (peer.isConnected) ActiveGreen400 else SignalOrange500
                )
            )
        }
    }
}
