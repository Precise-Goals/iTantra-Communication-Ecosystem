package com.itantra.ui.screen

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.itantra.domain.model.AlertEvent
import com.itantra.domain.model.AlertTemplate
import com.itantra.ui.MainViewModel
import com.itantra.ui.theme.DistressRed400
import com.itantra.ui.theme.DistressRed500
import com.itantra.ui.theme.DistressRed600
import com.itantra.ui.theme.DistressRedGlow
import com.itantra.ui.theme.OnSurface
import com.itantra.ui.theme.OnSurfaceDim
import com.itantra.ui.theme.SpaceBlue800
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun SOSScreen(viewModel: MainViewModel) {
    val peers by viewModel.peersFlow.collectAsState()
    val connectedPeers = peers.filter { it.isConnected }
    var customText by remember { mutableStateOf("") }
    var isBroadcasting by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Header
        Text(
            "Emergency Broadcast",
            style = MaterialTheme.typography.headlineSmall.copy(
                color = DistressRed400,
                fontWeight = FontWeight.Bold
            )
        )
        Text(
            "Alert will play at maximum volume on all connected devices regardless of silent mode.",
            style = MaterialTheme.typography.bodySmall.copy(color = OnSurfaceDim)
        )

        // Connected peers count
        Card(colors = CardDefaults.cardColors(containerColor = SpaceBlue800), shape = RoundedCornerShape(12.dp)) {
            Row(
                modifier = Modifier.padding(12.dp).fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Target Devices", style = MaterialTheme.typography.bodyMedium.copy(color = OnSurfaceDim))
                Text(
                    "${connectedPeers.size} connected",
                    style = MaterialTheme.typography.titleSmall.copy(
                        color = if (connectedPeers.isNotEmpty()) DistressRed400 else OnSurfaceDim,
                        fontWeight = FontWeight.Bold
                    )
                )
            }
        }

        // Alert templates
        Text("Quick Templates", style = MaterialTheme.typography.titleSmall.copy(color = OnSurfaceDim))
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            AlertTemplate.entries.forEach { template ->
                Button(
                    onClick = {
                        scope.launch {
                            isBroadcasting = true
                            val text = template.templateText["en"] ?: template.displayName
                            viewModel.broadcastAlert(text)
                            delay(2000)
                            isBroadcasting = false
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = DistressRed600.copy(alpha = 0.2f),
                        contentColor = DistressRed400
                    ),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(template.displayName, style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium))
                        Icon(Icons.Filled.Warning, null, modifier = Modifier.size(16.dp))
                    }
                }
            }
        }

        // Custom alert
        Text("Custom Alert", style = MaterialTheme.typography.titleSmall.copy(color = OnSurfaceDim))
        OutlinedTextField(
            value = customText,
            onValueChange = { customText = it },
            modifier = Modifier.fillMaxWidth(),
            label = { Text("Type your alert message...") },
            colors = TextFieldDefaults.colors(
                focusedBorderColor = DistressRed400,
                unfocusedBorderColor = OnSurfaceDim,
                focusedLabelColor = DistressRed400,
                cursorColor = DistressRed400
            ),
            shape = RoundedCornerShape(12.dp)
        )

        // SOS Hold Button
        Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
            SOSHoldButton(
                isEnabled = connectedPeers.isNotEmpty(),
                onSOSTrigger = {
                    val alertText = customText.ifBlank {
                        "EMERGENCY SOS — Immediate assistance required."
                    }
                    scope.launch {
                        isBroadcasting = true
                        viewModel.broadcastAlert(alertText)
                        delay(3000)
                        isBroadcasting = false
                    }
                }
            )
        }

        // Broadcasting status
        AnimatedVisibility(visible = isBroadcasting, enter = fadeIn(), exit = fadeOut()) {
            Card(
                colors = CardDefaults.cardColors(containerColor = DistressRedGlow),
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp)
            ) {
                Box(modifier = Modifier.padding(16.dp).fillMaxWidth(), contentAlignment = Alignment.Center) {
                    Text(
                        "⚡ Broadcasting to ${connectedPeers.size} device${if (connectedPeers.size != 1) "s" else ""}...",
                        style = MaterialTheme.typography.bodyMedium.copy(
                            color = DistressRed400, fontWeight = FontWeight.Bold
                        )
                    )
                }
            }
        }
    }
}

@Composable
private fun SOSHoldButton(isEnabled: Boolean, onSOSTrigger: () -> Unit) {
    var holdProgress by remember { mutableLongStateOf(0L) }
    var isHolding by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val haptic = LocalHapticFeedback.current

    val infiniteTransition = rememberInfiniteTransition(label = "sos_pulse")
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 1f, targetValue = 1.05f,
        animationSpec = infiniteRepeatable(tween(800), RepeatMode.Reverse),
        label = "sos_scale"
    )

    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            modifier = Modifier
                .size(120.dp)
                .scale(if (isHolding) pulseScale else 1f)
                .background(
                    if (isEnabled) DistressRed600 else DistressRed600.copy(alpha = 0.3f),
                    CircleShape
                )
                .border(3.dp, if (isEnabled) DistressRed400 else OnSurfaceDim, CircleShape)
                .pointerInput(isEnabled) {
                    if (!isEnabled) return@pointerInput
                    detectTapGestures(
                        onPress = {
                            isHolding = true
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            var elapsed = 0L
                            val job = scope.launch {
                                while (elapsed < 3000L) {
                                    delay(50)
                                    elapsed += 50
                                    holdProgress = elapsed
                                }
                                onSOSTrigger()
                            }
                            tryAwaitRelease()
                            job.cancel()
                            isHolding = false
                            holdProgress = 0L
                        }
                    )
                },
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("SOS", style = MaterialTheme.typography.headlineMedium.copy(color = Color.White, fontWeight = FontWeight.Bold))
                if (isHolding) {
                    Text("${((3000L - holdProgress) / 1000f).let { "%.1f".format(it) }}s", style = MaterialTheme.typography.labelSmall.copy(color = Color.White.copy(alpha = 0.8f)))
                }
            }
        }
        Spacer(Modifier.height(8.dp))
        Text(
            if (isEnabled) "Hold 3 seconds to broadcast" else "No devices connected",
            style = MaterialTheme.typography.bodySmall.copy(
                color = if (isEnabled) OnSurfaceDim else DistressRed500.copy(alpha = 0.5f)
            )
        )
    }
}

/**
 * Full-screen non-dismissible overlay shown when an ALERT message is received.
 * Displayed from MainActivity and overlays all screens.
 */
@Composable
fun AlertOverlay(alertEvent: AlertEvent, onDismissed: () -> Unit) {
    val infiniteTransition = rememberInfiniteTransition(label = "alert_pulse")
    val alpha by infiniteTransition.animateFloat(
        initialValue = 0.7f, targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(600), RepeatMode.Reverse),
        label = "alert_alpha"
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(DistressRed600.copy(alpha = 0.95f)),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier.padding(32.dp)
        ) {
            Icon(
                Icons.Filled.Warning,
                contentDescription = "Alert",
                tint = Color.White.copy(alpha = alpha),
                modifier = Modifier.size(80.dp)
            )
            Spacer(Modifier.height(24.dp))
            Text(
                "⚠️ EMERGENCY ALERT",
                style = MaterialTheme.typography.headlineSmall.copy(
                    color = Color.White,
                    fontWeight = FontWeight.Bold
                ),
                textAlign = TextAlign.Center
            )
            Spacer(Modifier.height(16.dp))
            Text(
                alertEvent.message.text,
                style = MaterialTheme.typography.bodyLarge.copy(
                    color = Color.White,
                    fontSize = 20.sp
                ),
                textAlign = TextAlign.Center
            )
            Spacer(Modifier.height(8.dp))
            Text(
                "From: ${alertEvent.message.senderId.take(8)}...",
                style = MaterialTheme.typography.bodySmall.copy(color = Color.White.copy(alpha = 0.7f))
            )
            Spacer(Modifier.height(32.dp))
            Text(
                "Playing at maximum volume...",
                style = MaterialTheme.typography.bodySmall.copy(
                    color = Color.White.copy(alpha = 0.8f * alpha)
                )
            )
        }
    }

    // Auto-dismiss after TTS completion (approximated by delay)
    LaunchedEffect(alertEvent) {
        delay(8000) // Will be replaced by onTTSSynthesisComplete signal
        onDismissed()
    }
}
