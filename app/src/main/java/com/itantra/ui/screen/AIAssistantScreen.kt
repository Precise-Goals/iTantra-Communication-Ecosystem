package com.itantra.ui.screen

import androidx.compose.animation.core.FastOutSlowInEasing
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
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.automirrored.filled.VolumeOff
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
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
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.itantra.ui.AiMessage
import com.itantra.ui.MainViewModel
import com.itantra.ui.theme.iTantraBackground
import com.itantra.ui.theme.iTantraBlack
import com.itantra.ui.theme.iTantraBlack40
import com.itantra.ui.theme.iTantraBlack60
import com.itantra.ui.theme.iTantraBorder
import com.itantra.ui.theme.iTantraCardAlt
import com.itantra.ui.theme.iTantraSuccess
import com.itantra.ui.theme.iTantraWhite
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** STT input language options — must match the BCP-47 codes ModelRegistry.sttInfo() downloads for. */
private val STT_LANGUAGES = listOf(
    "hi" to "हिंदी",
    "en" to "English",
    "gu" to "ગુજરાતી",
    "mr" to "मराठी",
    "kn" to "ಕನ್ನಡ",
    "ml" to "മലയാളം",
    "ta" to "தமிழ்",
    "te" to "తెలుగు",
    "bn" to "বাংলা"
)

/**
 * 100% Offline AI Assistant Conversational Interface.
 *
 * Requirements:
 * 1. Zero predefined buttons, chips, fallback UI menus, or generic error states.
 * 2. Transcribed STT strings go directly into local NLP generation.
 * 3. NLP output is piped immediately into local neural TTS and AudioTrack.
 */
@Composable
fun AIAssistantScreen(
    viewModel: MainViewModel,
    onNavigateToDownloads: () -> Unit
) {
    val aiMessages by viewModel.aiMessages.collectAsState()
    val isThinking by viewModel.isAiThinking.collectAsState()
    val isSpeaking by viewModel.isSpeaking.collectAsState()
    val isVoiceMuted by viewModel.isVoiceMuted.collectAsState()
    val isRecordingVoice by viewModel.isRecordingVoice.collectAsState()
    val isUsingRealLlm by viewModel.isUsingRealLlm.collectAsState()
    val selectedLanguage by viewModel.selectedLanguage.collectAsState()
    val voiceUnavailableNotice by viewModel.voiceUnavailableNotice.collectAsState()
    val listState = rememberLazyListState()
    var textInput by remember { mutableStateOf("") }

    LaunchedEffect(aiMessages.size, aiMessages.lastOrNull()?.text) {
        if (aiMessages.isNotEmpty()) {
            listState.scrollToItem(aiMessages.lastIndex)
        }
    }

    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val micPulse by infiniteTransition.animateFloat(
        initialValue = 1.0f,
        targetValue = 1.18f,
        animationSpec = infiniteRepeatable(
            animation = tween(500, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "micPulse"
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(iTantraBackground)
            .imePadding()
    ) {
        // ── Header ───────────────────────────────────────────────────
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "AI Tactical Assistant",
                    style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                    color = iTantraBlack
                )
                Text(
                    text = when {
                        isSpeaking -> "Vocalizing via local neural TTS…"
                        isRecordingVoice -> "Listening (Local IndicConformer STT)…"
                        isUsingRealLlm -> "100% Offline · On-device Phi-3 (real generation)"
                        else -> "100% Offline · Quick-reference assistant (Phi-3 not loaded)"
                    },
                    style = MaterialTheme.typography.labelSmall,
                    color = if (isRecordingVoice) Color(0xFFDC2626) else if (isSpeaking) Color(0xFF2563EB) else iTantraSuccess
                )
                if (voiceUnavailableNotice != null) {
                    Text(
                        text = voiceUnavailableNotice ?: "",
                        style = MaterialTheme.typography.labelSmall,
                        color = Color(0xFFB45309)
                    )
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                // Local AudioTrack TTS Mute / Unmute
                IconButton(onClick = { viewModel.toggleVoiceMute() }) {
                    Icon(
                        imageVector = if (isVoiceMuted) Icons.AutoMirrored.Filled.VolumeOff else Icons.AutoMirrored.Filled.VolumeUp,
                        contentDescription = if (isVoiceMuted) "Unmute voice" else "Mute voice",
                        tint = if (isVoiceMuted) iTantraBlack40 else iTantraBlack
                    )
                }
                // Clear chat
                IconButton(onClick = { viewModel.clearAiChat() }) {
                    Icon(Icons.Filled.Clear, contentDescription = "Clear chat", tint = iTantraBlack60)
                }
            }
        }

        // ── STT input language (which on-device model transcribes the mic) ──
        LazyRow(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            contentPadding = PaddingValues(bottom = 8.dp)
        ) {
            items(STT_LANGUAGES, key = { it.first }) { (code, label) ->
                val isSelected = code == selectedLanguage
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(16.dp))
                        .background(if (isSelected) iTantraBlack else iTantraCardAlt)
                        .border(1.dp, if (isSelected) iTantraBlack else iTantraBorder, RoundedCornerShape(16.dp))
                        .clickable { viewModel.setManualLanguage(code) }
                        .padding(horizontal = 12.dp, vertical = 6.dp)
                ) {
                    Text(
                        text = label,
                        style = MaterialTheme.typography.labelSmall,
                        color = if (isSelected) iTantraWhite else iTantraBlack60
                    )
                }
            }
        }

        // ── Pure Conversational Message Feed (No Predefined Buttons) ──
        LazyColumn(
            state = listState,
            modifier = Modifier
                .weight(1f)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            contentPadding = PaddingValues(vertical = 8.dp)
        ) {
            items(aiMessages, key = { it.id }) { msg ->
                AiMessageBubbleWhite(
                    msg = msg,
                    isCurrentlySpeaking = isSpeaking && !msg.isUser,
                    onSpeak = { viewModel.speakAiResponse(msg.text) },
                    onStop = { viewModel.stopSpeaking() }
                )
            }
            if (isThinking) {
                item {
                    Row(
                        modifier = Modifier
                            .clip(RoundedCornerShape(16.dp))
                            .background(iTantraCardAlt)
                            .border(1.dp, iTantraBorder, RoundedCornerShape(16.dp))
                            .padding(horizontal = 14.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(14.dp),
                            strokeWidth = 2.dp,
                            color = iTantraBlack
                        )
                        Spacer(Modifier.width(8.dp))
                        Text("Local neural NLP inferring…", style = MaterialTheme.typography.labelSmall, color = iTantraBlack60)
                    }
                }
            }
        }

        // ── Direct Conversational Input Bar ──────────────────────────
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(iTantraWhite)
                .border(1.dp, iTantraBorder)
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Local Hardware Microphone STT Button (Hold to talk)
            Box(
                modifier = Modifier
                    .size(46.dp)
                    .scale(if (isRecordingVoice) micPulse else 1.0f)
                    .clip(CircleShape)
                    .background(if (isRecordingVoice) Color(0xFFDC2626) else iTantraBlack)
                    .pointerInput(Unit) {
                        detectTapGestures(
                            onPress = {
                                viewModel.startAssistantRecording()
                                tryAwaitRelease()
                                viewModel.stopAssistantRecording()
                            }
                        )
                    },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Filled.Mic,
                    contentDescription = "Hold to speak into offline STT",
                    tint = iTantraWhite,
                    modifier = Modifier.size(22.dp)
                )
            }
            Spacer(Modifier.width(8.dp))
            OutlinedTextField(
                value = textInput,
                onValueChange = { textInput = it },
                placeholder = { Text("Speak or type any emergency query…", color = iTantraBlack40, fontSize = 13.sp) },
                singleLine = true,
                shape = RoundedCornerShape(22.dp),
                modifier = Modifier.weight(1f),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                keyboardActions = KeyboardActions(
                    onSend = {
                        if (textInput.isNotBlank()) {
                            viewModel.sendAiQuery(textInput.trim())
                            textInput = ""
                        }
                    }
                ),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = iTantraBlack,
                    unfocusedBorderColor = iTantraBorder,
                    focusedTextColor = iTantraBlack,
                    unfocusedTextColor = iTantraBlack,
                    focusedContainerColor = iTantraCardAlt,
                    unfocusedContainerColor = iTantraCardAlt
                )
            )
            Spacer(Modifier.width(8.dp))
            IconButton(
                onClick = {
                    if (textInput.isNotBlank()) {
                        viewModel.sendAiQuery(textInput.trim())
                        textInput = ""
                    }
                },
                enabled = textInput.isNotBlank(),
                modifier = Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .background(if (textInput.isNotBlank()) iTantraBlack else iTantraBorder)
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.Send,
                    contentDescription = "Send",
                    tint = if (textInput.isNotBlank()) iTantraWhite else iTantraBlack40,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}

@Composable
private fun AiMessageBubbleWhite(
    msg: AiMessage,
    isCurrentlySpeaking: Boolean = false,
    onSpeak: () -> Unit = {},
    onStop: () -> Unit = {}
) {
    val isUser = msg.isUser
    val timeStr = SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(msg.timestamp))

    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = if (isUser) Alignment.End else Alignment.Start
    ) {
        Box(
            modifier = Modifier
                .clip(
                    RoundedCornerShape(
                        topStart = 18.dp,
                        topEnd = 18.dp,
                        bottomStart = if (isUser) 18.dp else 4.dp,
                        bottomEnd = if (isUser) 4.dp else 18.dp
                    )
                )
                .background(if (isUser) iTantraBlack else iTantraCardAlt)
                .border(
                    width = 1.dp,
                    color = if (isUser) iTantraBlack else iTantraBorder,
                    shape = RoundedCornerShape(18.dp)
                )
                .padding(horizontal = 14.dp, vertical = 10.dp)
        ) {
            Column {
                Text(
                    text = msg.text,
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (isUser) iTantraWhite else iTantraBlack
                )
                Spacer(Modifier.height(6.dp))
                Row(
                    modifier = Modifier.align(Alignment.End),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (!isUser && msg.text.isNotBlank()) {
                        Box(
                            modifier = Modifier
                                .clip(CircleShape)
                                .clickable {
                                    if (isCurrentlySpeaking) onStop() else onSpeak()
                                }
                                .padding(4.dp)
                        ) {
                            Icon(
                                imageVector = if (isCurrentlySpeaking) Icons.Filled.Stop else Icons.AutoMirrored.Filled.VolumeUp,
                                contentDescription = if (isCurrentlySpeaking) "Stop voice" else "Speak message",
                                tint = if (isCurrentlySpeaking) Color(0xFFDC2626) else iTantraBlack60,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                        Spacer(Modifier.width(6.dp))
                    }
                    Text(
                        text = timeStr,
                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp),
                        color = if (isUser) Color(0xFF999999) else iTantraBlack40
                    )
                }
            }
        }
    }
}
