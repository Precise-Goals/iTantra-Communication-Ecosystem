package com.itantra.ui.screen

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.center
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.itantra.domain.model.ConnectionType
import com.itantra.domain.model.PeerDevice
import com.itantra.ui.MainViewModel
import com.itantra.ui.theme.ActiveGreen400
import com.itantra.ui.theme.OnSurface
import com.itantra.ui.theme.OnSurfaceDim
import com.itantra.ui.theme.PeerDotBluetooth
import com.itantra.ui.theme.PeerDotWifi
import com.itantra.ui.theme.RadarGrid
import com.itantra.ui.theme.RadarSweep
import com.itantra.ui.theme.SignalOrange500
import com.itantra.ui.theme.SpaceBlue800
import com.itantra.ui.theme.Surface900
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

@Composable
fun RadarScreen(viewModel: MainViewModel) {
    val peers by viewModel.peersFlow.collectAsState()
    var selectedPeer by remember { mutableStateOf<PeerDevice?>(null) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(16.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("Mesh Radar", style = MaterialTheme.typography.headlineSmall.copy(color = OnSurface, fontWeight = FontWeight.Bold))
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                LegendDot(color = PeerDotWifi, label = "Wi-Fi")
                LegendDot(color = PeerDotBluetooth, label = "BT")
            }
        }
        Spacer(Modifier.height(8.dp))

        // Radar canvas
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            contentAlignment = Alignment.Center
        ) {
            RadarCanvas(
                peers = peers.filter { it.isConnected },
                onPeerTapped = { peer -> selectedPeer = if (selectedPeer?.deviceId == peer.deviceId) null else peer }
            )
        }

        // Selected peer detail card
        selectedPeer?.let { peer ->
            PeerDetailCard(peer = peer)
        }

        // Peer count summary
        Spacer(Modifier.height(8.dp))
        Card(colors = CardDefaults.cardColors(containerColor = SpaceBlue800), shape = RoundedCornerShape(12.dp)) {
            Row(
                modifier = Modifier.padding(12.dp).fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                val wifiPeers = peers.count { it.isConnected && it.connectionType == ConnectionType.WIFI_DIRECT }
                val btPeers = peers.count { it.isConnected && it.connectionType == ConnectionType.BLUETOOTH }
                StatItem("Total Nodes", "${peers.count { it.isConnected }}", ActiveGreen400)
                StatItem("Wi-Fi Direct", "$wifiPeers", PeerDotWifi)
                StatItem("Bluetooth", "$btPeers", PeerDotBluetooth)
            }
        }
    }
}

@Composable
private fun RadarCanvas(peers: List<PeerDevice>, onPeerTapped: (PeerDevice) -> Unit) {
    val infiniteTransition = rememberInfiniteTransition(label = "radar_sweep")
    val sweepAngle by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(3000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "sweep_angle"
    )

    Canvas(
        modifier = Modifier
            .fillMaxSize()
            .pointerInput(peers) {
                detectTapGestures { tapOffset ->
                    val center = Offset(size.width / 2f, size.height / 2f)
                    val radius = minOf(size.width, size.height) / 2f - 24.dp.toPx()
                    peers.forEach { peer ->
                        val nodePos = peerToCanvasPosition(peer, center, radius)
                        val distance = sqrt(
                            (tapOffset.x - nodePos.x).let { it * it } +
                            (tapOffset.y - nodePos.y).let { it * it }
                        )
                        if (distance < 24.dp.toPx()) {
                            onPeerTapped(peer)
                        }
                    }
                }
            }
    ) {
        val center = Offset(size.width / 2f, size.height / 2f)
        val radius = minOf(size.width, size.height) / 2f - 24.dp.toPx()

        drawRadarGrid(center, radius)
        drawRadarSweep(center, radius, sweepAngle)
        drawCenterNode(center)

        // Draw peer nodes
        peers.forEach { peer ->
            val pos = peerToCanvasPosition(peer, center, radius)
            drawPeerNode(pos, peer)
        }
    }
}

private fun DrawScope.drawRadarGrid(center: Offset, radius: Float) {
    // Concentric circles (3 rings)
    for (ring in 1..3) {
        val r = radius * ring / 3f
        drawCircle(color = RadarGrid, radius = r, center = center, style = Stroke(1.dp.toPx()))
    }
    // Cross hairs
    drawLine(RadarGrid, Offset(center.x - radius, center.y), Offset(center.x + radius, center.y), 1.dp.toPx())
    drawLine(RadarGrid, Offset(center.x, center.y - radius), Offset(center.x, center.y + radius), 1.dp.toPx())
    // Diagonal lines
    val d = radius * 0.707f
    drawLine(RadarGrid, Offset(center.x - d, center.y - d), Offset(center.x + d, center.y + d), 1.dp.toPx())
    drawLine(RadarGrid, Offset(center.x + d, center.y - d), Offset(center.x - d, center.y + d), 1.dp.toPx())
}

private fun DrawScope.drawRadarSweep(center: Offset, radius: Float, angleDeg: Float) {
    val angleRad = Math.toRadians(angleDeg.toDouble() - 90.0)
    val sweepEndX = center.x + radius * cos(angleRad).toFloat()
    val sweepEndY = center.y + radius * sin(angleRad).toFloat()

    // Sweep trail (gradient effect via multiple lines with decreasing alpha)
    for (i in 0..30) {
        val trailAngleRad = Math.toRadians((angleDeg - i * 3.0) - 90.0)
        val alpha = (1f - i / 30f) * 0.6f
        val endX = center.x + radius * cos(trailAngleRad).toFloat()
        val endY = center.y + radius * sin(trailAngleRad).toFloat()
        drawLine(RadarSweep.copy(alpha = alpha), center, Offset(endX, endY), 2.dp.toPx())
    }
    // Main sweep line
    drawLine(ActiveGreen400.copy(alpha = 0.9f), center, Offset(sweepEndX, sweepEndY), 2.dp.toPx())
}

private fun DrawScope.drawCenterNode(center: Offset) {
    drawCircle(color = ActiveGreen400, radius = 8.dp.toPx(), center = center)
    drawCircle(color = ActiveGreen400.copy(alpha = 0.3f), radius = 14.dp.toPx(), center = center, style = Stroke(2.dp.toPx()))
}

private fun DrawScope.drawPeerNode(pos: Offset, peer: PeerDevice) {
    val color = if (peer.connectionType == ConnectionType.WIFI_DIRECT) PeerDotWifi else PeerDotBluetooth
    // Glow
    drawCircle(color.copy(alpha = 0.25f), radius = 14.dp.toPx(), center = pos)
    // Core dot
    drawCircle(color, radius = 8.dp.toPx(), center = pos)
}

/** Map peer RSSI to a position on the radar canvas. Stronger signal = closer to center. */
private fun peerToCanvasPosition(peer: PeerDevice, center: Offset, maxRadius: Float): Offset {
    // RSSI typically ranges from -30 (strong) to -90 (weak) dBm
    val normalizedRssi = ((peer.rssi + 90f) / 60f).coerceIn(0.1f, 1f)
    val distanceFromCenter = maxRadius * (1f - normalizedRssi * 0.8f) // Closer = stronger

    // Stable angular position based on device ID hash
    val angleRad = (peer.deviceId.hashCode() and 0xFFFFFF).toFloat() / 0xFFFFFF * 2 * PI.toFloat()
    return Offset(
        center.x + distanceFromCenter * cos(angleRad),
        center.y + distanceFromCenter * sin(angleRad)
    )
}

@Composable
private fun PeerDetailCard(peer: PeerDevice) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = if (peer.connectionType == ConnectionType.WIFI_DIRECT)
                PeerDotWifi.copy(alpha = 0.1f) else PeerDotBluetooth.copy(alpha = 0.1f)
        ),
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(peer.deviceName, style = MaterialTheme.typography.titleSmall.copy(color = OnSurface, fontWeight = FontWeight.Bold))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                Text("Type: ${if (peer.connectionType == ConnectionType.WIFI_DIRECT) "Wi-Fi Direct" else "Bluetooth"}", style = MaterialTheme.typography.bodySmall.copy(color = OnSurfaceDim))
                if (peer.latencyMs > 0) Text("RTT: ${peer.latencyMs}ms", style = MaterialTheme.typography.bodySmall.copy(color = OnSurfaceDim))
                Text("RSSI: ${peer.rssi}dBm", style = MaterialTheme.typography.bodySmall.copy(color = OnSurfaceDim))
            }
        }
    }
}

@Composable
private fun LegendDot(color: Color, label: String) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        Box(Modifier.size(10.dp).background(color, CircleShape))
        Text(label, style = MaterialTheme.typography.labelSmall.copy(color = OnSurfaceDim))
    }
}

@Composable
private fun StatItem(label: String, value: String, color: Color) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, style = MaterialTheme.typography.titleMedium.copy(color = color, fontWeight = FontWeight.Bold))
        Text(label, style = MaterialTheme.typography.labelSmall.copy(color = OnSurfaceDim))
    }
}
