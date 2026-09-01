package com.itantra.ui.screen

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MicOff
import androidx.compose.material.icons.filled.Send
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Divider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.itantra.domain.model.ConnectionMode
import com.itantra.domain.model.Direction
import com.itantra.domain.model.MessageType
import com.itantra.domain.model.TransceiverMessage
import com.itantra.ui.MainViewModel
import com.itantra.ui.theme.ActiveGreen400
import com.itantra.ui.theme.ActiveGreenGlow
import com.itantra.ui.theme.DistressRed500
import com.itantra.ui.theme.OnSurface
import com.itantra.ui.theme.OnSurfaceDim
import com.itantra.ui.theme.SignalOrange500
import com.itantra.ui.theme.SignalOrangeGlow
import com.itantra.ui.theme.SpaceBlue700
import com.itantra.ui.theme.SpaceBlue800
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

@Composable
fun TransceiverScreen(viewModel: MainViewModel) {
    val messages by viewModel.messageLogFlow.collectAsState()
    val connectionMode by viewModel.connectionMode.collectAsState()
    val isPTTActive by viewModel.isPTTActive.collectAsState()
    val vadProbability by viewModel.vadProbabilityFlow.collectAsState()
    val sttLanguage by viewModel.sttLanguage.collectAsState()
    val ttsLanguage by viewModel.ttsLanguage.collectAsState()
    val haptic = LocalHapticFeedback.current

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Header row
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("Transceiver", style = MaterialTheme.typography.headlineSmall.copy(color = OnSurface, fontWeight = FontWeight.Bold))
            // Language chips
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                LanguageChip(label = sttLanguage.nativeName, isSTT = true)
                Text("→", color = OnSurfaceDim)
                LanguageChip(label = ttsLanguage.nativeName, isSTT = false)
            }
        }

        // Mode toggle
        Card(colors = CardDefaults.cardColors(containerColor = SpaceBlue800), shape = RoundedCornerShape(12.dp)) {
            Row(
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp).fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    if (connectionMode == ConnectionMode.PUSH_TO_TALK) "Push-to-Talk" else "Phone Mode",
                    style = MaterialTheme.typography.titleSmall.copy(color = OnSurface)
                )
                Switch(
                    checked = connectionMode == ConnectionMode.PHONE_MODE,
                    onCheckedChange = { isPhone ->
                        viewModel.setConnectionMode(if (isPhone) ConnectionMode.PHONE_MODE else ConnectionMode.PUSH_TO_TALK)
                    },
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = ActiveGreen400,
                        checkedTrackColor = ActiveGreenGlow
                    )
                )
            }
        }

        // PTT Button (center)
        Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
            if (connectionMode == ConnectionMode.PUSH_TO_TALK) {
                PTTButton(
                    isActive = isPTTActive,
                    onPress = {
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        viewModel.startPTT()
                    },
                    onRelease = { viewModel.stopPTT() }
                )
            } else {
                // Phone mode: show live VAD waveform
                LiveWaveformIndicator(vadProbability = vadProbability)
            }
        }

        // Transcription log
        Text("Live Transcription", style = MaterialTheme.typography.titleSmall.copy(color = OnSurfaceDim))
        LazyColumn(
            modifier = Modifier.weight(1f),
            reverseLayout = false,
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            items(messages.take(50)) { message ->
                TranscriptionLogItem(message = message)
            }
        }
    }
}

@Composable
fun PTTButton(isActive: Boolean, onPress: () -> Unit, onRelease: () -> Unit) {
    val infiniteTransition = rememberInfiniteTransition(label = "ptt_pulse")
    val scale by infiniteTransition.animateFloat(
        initialValue = 1f, targetValue = if (isActive) 1.08f else 1f,
        animationSpec = infiniteRepeatable(
            tween(600, easing = FastOutSlowInEasing), RepeatMode.Reverse
        ),
        label = "ptt_scale"
    )

    val buttonColor by animateColorAsState(
        targetValue = if (isActive) SignalOrange500 else SpaceBlue700,
        animationSpec = tween(200),
        label = "ptt_color"
    )
    val glowColor by animateColorAsState(
        targetValue = if (isActive) SignalOrangeGlow else Color.Transparent,
        animationSpec = tween(200),
        label = "ptt_glow"
    )
    val borderColor by animateColorAsState(
        targetValue = if (isActive) SignalOrange500 else OnSurfaceDim,
        label = "ptt_border"
    )

    Box(contentAlignment = Alignment.Center) {
        // Outer glow ring
        Box(
            modifier = Modifier
                .size(160.dp)
                .scale(scale)
                .background(glowColor, CircleShape)
        )
        // Main button
        Box(
            modifier = Modifier
                .size(140.dp)
                .scale(if (isActive) scale else 1f)
                .background(buttonColor, CircleShape)
                .border(2.dp, borderColor, CircleShape)
                .pointerInput(Unit) {
                    detectTapGestures(
                        onPress = {
                            onPress()
                            tryAwaitRelease()
                            onRelease()
                        }
                    )
                },
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(
                    imageVector = if (isActive) Icons.Filled.Mic else Icons.Filled.MicOff,
                    contentDescription = "PTT",
                    tint = if (isActive) Color.White else OnSurfaceDim,
                    modifier = Modifier.size(40.dp)
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    if (isActive) "HOLD" else "HOLD TO TALK",
                    style = MaterialTheme.typography.labelSmall.copy(
                        color = if (isActive) Color.White else OnSurfaceDim,
                        fontWeight = FontWeight.Bold
                    )
                )
            }
        }
    }
}

@Composable
fun LiveWaveformIndicator(vadProbability: Float) {
    val infiniteTransition = rememberInfiniteTransition(label = "waveform")
    val phase by infiniteTransition.animateFloat(
        initialValue = 0f, targetValue = (2 * PI).toFloat(),
        animationSpec = infiniteRepeatable(tween(1500)),
        label = "phase"
    )

    Canvas(
        modifier = Modifier
            .fillMaxWidth()
            .height(80.dp)
    ) {
        val width = size.width
        val height = size.height
        val centerY = height / 2
        val amplitude = (height / 2 - 8.dp.toPx()) * vadProbability.coerceIn(0.1f, 1f)
        val waveCount = 3

        for (wave in 0 until waveCount) {
            val offset = wave * (2 * PI / waveCount).toFloat()
            val alpha = 1f - wave * 0.25f
            for (x in 0 until width.toInt() step 2) {
                val angle = (x / width.toFloat()) * 4 * PI + phase + offset
                val y = centerY + (sin(angle) * amplitude).toFloat()
                val nextX = (x + 2).coerceAtMost(width.toInt())
                val nextAngle = (nextX / width.toFloat()) * 4 * PI + phase + offset
                val nextY = centerY + (sin(nextAngle) * amplitude).toFloat()
                drawLine(
                    color = ActiveGreen400.copy(alpha = alpha),
                    start = Offset(x.toFloat(), y),
                    end = Offset(nextX.toFloat(), nextY),
                    strokeWidth = 2.dp.toPx(),
                    cap = StrokeCap.Round
                )
            }
        }
    }
}

@Composable
private fun LanguageChip(label: String, isSTT: Boolean) {
    Box(
        modifier = Modifier
            .background(
                if (isSTT) SignalOrange500.copy(alpha = 0.15f) else ActiveGreen400.copy(alpha = 0.15f),
                RoundedCornerShape(20.dp)
            )
            .border(
                1.dp,
                if (isSTT) SignalOrange500.copy(alpha = 0.5f) else ActiveGreen400.copy(alpha = 0.5f),
                RoundedCornerShape(20.dp)
            )
            .padding(horizontal = 10.dp, vertical = 4.dp)
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall.copy(
                color = if (isSTT) SignalOrange500 else ActiveGreen400,
                fontWeight = FontWeight.SemiBold
            )
        )
    }
}

@Composable
private fun TranscriptionLogItem(message: TransceiverMessage) {
    val isSent = message.direction == Direction.SENT
    val isAlert = message.type == MessageType.ALERT

    val bgColor = when {
        isAlert -> DistressRed500.copy(alpha = 0.15f)
        isSent -> SignalOrange500.copy(alpha = 0.1f)
        else -> ActiveGreen400.copy(alpha = 0.08f)
    }
    val borderColor = when {
        isAlert -> DistressRed500.copy(alpha = 0.5f)
        isSent -> SignalOrange500.copy(alpha = 0.4f)
        else -> ActiveGreen400.copy(alpha = 0.4f)
    }
    val labelColor = when {
        isAlert -> DistressRed500
        isSent -> SignalOrange500
        else -> ActiveGreen400
    }
    val label = when {
        isAlert -> "⚠️ ALERT"
        isSent -> "▲ SENT"
        else -> "▼ RECV"
    }

    Card(
        colors = CardDefaults.cardColors(containerColor = bgColor),
        modifier = Modifier.fillMaxWidth().border(1.dp, borderColor, RoundedCornerShape(8.dp)),
        shape = RoundedCornerShape(8.dp)
    ) {
        Column(modifier = Modifier.padding(10.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(label, style = MaterialTheme.typography.labelSmall.copy(color = labelColor, fontWeight = FontWeight.Bold))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (message.confidence > 0f) {
                        ConfidenceDot(message.confidence)
                        Spacer(Modifier.width(6.dp))
                    }
                    Text(
                        formatTimestamp(message.timestamp),
                        style = MaterialTheme.typography.labelSmall.copy(color = OnSurfaceDim)
                    )
                }
            }
            Spacer(Modifier.height(4.dp))
            Text(message.text, style = MaterialTheme.typography.bodyMedium.copy(color = OnSurface))
            if (message.srcLang != message.dstLang) {
                Text(
                    "${message.srcLang} → ${message.dstLang}",
                    style = MaterialTheme.typography.labelSmall.copy(color = OnSurfaceDim)
                )
            }
        }
    }
}

@Composable
private fun ConfidenceDot(confidence: Float) {
    Box(
        modifier = Modifier
            .size(8.dp)
            .background(
                when {
                    confidence > 0.7f -> ActiveGreen400
                    confidence > 0.4f -> SignalOrange500
                    else -> DistressRed500
                },
                CircleShape
            )
    )
}

private fun formatTimestamp(epochMs: Long): String {
    return SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date(epochMs))
}
