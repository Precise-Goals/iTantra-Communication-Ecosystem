package com.itantra.ui.screen

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Radar
import androidx.compose.material.icons.outlined.PersonAdd
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.itantra.domain.model.PeerDevice
import com.itantra.ui.MainViewModel
import com.itantra.ui.theme.iTantraBackground
import com.itantra.ui.theme.iTantraBlack
import com.itantra.ui.theme.iTantraBlack40
import com.itantra.ui.theme.iTantraBlack60
import com.itantra.ui.theme.iTantraBlack80
import com.itantra.ui.theme.iTantraBorder
import com.itantra.ui.theme.iTantraCard
import com.itantra.ui.theme.iTantraCardAlt
import com.itantra.ui.theme.iTantraSuccess
import com.itantra.ui.theme.iTantraWhite
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

@Composable
fun RadarScreen(viewModel: MainViewModel) {
    val peers by viewModel.knownPeers.collectAsState()
    var selectedPeer by remember { mutableStateOf<PeerDevice?>(null) }

    val infiniteTransition = rememberInfiniteTransition(label = "radar_sweep")
    val sweepAngle by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(tween(3500, easing = LinearEasing), RepeatMode.Restart),
        label = "sweep_angle"
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(iTantraBackground)
            .padding(20.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // ── Header ─────────────────────────────────────────────────
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = "Mesh Radar",
                    style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold),
                    color = iTantraBlack
                )
                Text(
                    text = "${peers.size} nodes mapped by RSSI signal strength",
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
                    Icon(Icons.Filled.Radar, contentDescription = null, tint = iTantraBlack, modifier = Modifier.size(14.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(
                        text = "360° Scan",
                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp, fontWeight = FontWeight.SemiBold),
                        color = iTantraBlack
                    )
                }
            }
        }

        Spacer(Modifier.height(20.dp))

        // ── RADAR CANVAS (Monochromatic High-Contrast Sweep) ─────────
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            contentAlignment = Alignment.Center
        ) {
            Canvas(
                modifier = Modifier
                    .size(310.dp)
                    .clip(CircleShape)
                    .background(iTantraCardAlt)
                    .border(1.5.dp, iTantraBlack, CircleShape)
                    .pointerInput(peers) {
                        detectTapGestures { offset ->
                            val center = Offset(size.width / 2f, size.height / 2f)
                            val maxR = size.width / 2f * 0.85f

                            selectedPeer = peers.minByOrNull { peer ->
                                val normDist = ((peer.rssi + 100).coerceIn(0, 70) / 70f).coerceIn(0.15f, 0.95f)
                                val r = (1f - normDist) * maxR
                                val angleDeg = (peer.deviceId.hashCode() % 360).toDouble()
                                val rad = angleDeg * PI / 180.0
                                val x = center.x + (r * cos(rad)).toFloat()
                                val y = center.y + (r * sin(rad)).toFloat()
                                val dx = offset.x - x
                                val dy = offset.y - y
                                dx * dx + dy * dy
                            }
                        }
                    }
            ) {
                val center = Offset(size.width / 2f, size.height / 2f)
                val maxRadius = size.width / 2f * 0.85f

                // Concentric Range Rings (25m, 50m, 75m, 100m equivalent)
                listOf(0.25f, 0.50f, 0.75f, 1.0f).forEach { fraction ->
                    drawCircle(
                        color = Color(0x1A000000),
                        radius = maxRadius * fraction,
                        center = center,
                        style = Stroke(width = 1.dp.toPx())
                    )
                }

                // Crosshairs
                drawLine(
                    color = Color(0x12000000),
                    start = Offset(center.x, center.y - maxRadius),
                    end = Offset(center.x, center.y + maxRadius),
                    strokeWidth = 1.dp.toPx()
                )
                drawLine(
                    color = Color(0x12000000),
                    start = Offset(center.x - maxRadius, center.y),
                    end = Offset(center.x + maxRadius, center.y),
                    strokeWidth = 1.dp.toPx()
                )

                // Rotating Radar Sweep Line
                rotate(sweepAngle, pivot = center) {
                    drawLine(
                        color = Color(0xFF000000),
                        start = center,
                        end = Offset(center.x, center.y - maxRadius),
                        strokeWidth = 2.dp.toPx()
                    )
                }

                // Center Node: This Device
                drawCircle(
                    color = Color(0xFF0A0A0A),
                    radius = 7.dp.toPx(),
                    center = center
                )
                drawCircle(
                    color = Color(0xFFFFFFFF),
                    radius = 3.dp.toPx(),
                    center = center
                )

                // Plot Peer Nodes as Radial Blips
                peers.forEach { peer ->
                    val normDist = ((peer.rssi + 100).coerceIn(0, 70) / 70f).coerceIn(0.15f, 0.95f)
                    val r = (1f - normDist) * maxRadius
                    val angleDeg = (peer.deviceId.hashCode() % 360).toDouble()
                    val rad = angleDeg * PI / 180.0
                    val x = center.x + (r * cos(rad)).toFloat()
                    val y = center.y + (r * sin(rad)).toFloat()

                    // Glow ring around blip
                    drawCircle(
                        color = if (peer.isAuthorized) Color(0x2215803D) else Color(0x22000000),
                        radius = 12.dp.toPx(),
                        center = Offset(x, y)
                    )
                    // Solid blip dot
                    drawCircle(
                        color = if (peer.isAuthorized) Color(0xFF15803D) else Color(0xFF0A0A0A),
                        radius = 6.dp.toPx(),
                        center = Offset(x, y)
                    )
                }
            }
        }

        Spacer(Modifier.height(16.dp))

        // ── Selected Node Detail Card ──────────────────────────────
        selectedPeer?.let { peer ->
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(22.dp))
                    .background(iTantraCardAlt)
                    .border(1.dp, iTantraBorder, RoundedCornerShape(22.dp))
                    .padding(18.dp)
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                text = peer.deviceName,
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                color = iTantraBlack
                            )
                            Text(
                                text = "Build ID: ${peer.deviceId} · RSSI: ${peer.rssi} dBm",
                                style = MaterialTheme.typography.bodySmall,
                                color = iTantraBlack60
                            )
                        }
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(10.dp))
                                .background(if (peer.isAuthorized) Color(0x1F15803D) else Color(0x1A000000))
                                .padding(horizontal = 8.dp, vertical = 4.dp)
                        ) {
                            Text(
                                text = if (peer.isAuthorized) "AUTHORIZED" else "DISCOVERED",
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 10.sp
                                ),
                                color = if (peer.isAuthorized) iTantraSuccess else iTantraBlack
                            )
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End
                    ) {
                        Button(
                            onClick = {
                                if (peer.isAuthorized) viewModel.revokePeer(peer.deviceId)
                                else viewModel.authorizePeer(peer.deviceId)
                            },
                            shape = RoundedCornerShape(16.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = iTantraBlack,
                                contentColor = iTantraWhite
                            ),
                            contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 14.dp, vertical = 6.dp)
                        ) {
                            Icon(
                                imageVector = if (peer.isAuthorized) Icons.Filled.CheckCircle else Icons.Outlined.PersonAdd,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(Modifier.width(6.dp))
                            Text(
                                text = if (peer.isAuthorized) "Revoke Access" else "Authorize Node",
                                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold)
                            )
                        }
                    }
                }
            }
        } ?: run {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(20.dp))
                    .background(iTantraCardAlt)
                    .border(1.dp, iTantraBorder, RoundedCornerShape(20.dp))
                    .padding(16.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "Tap on any radar blip to inspect peer signal & authorize",
                    style = MaterialTheme.typography.bodySmall,
                    color = iTantraBlack60,
                    textAlign = TextAlign.Center
                )
            }
        }
    }
}
