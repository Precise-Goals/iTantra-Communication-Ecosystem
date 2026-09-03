package com.itantra.ui.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.ui.graphics.Color
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MicOff
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.itantra.ui.MainViewModel
import com.itantra.ui.theme.iTantraBackground
import com.itantra.ui.theme.iTantraBlack
import com.itantra.ui.theme.iTantraBlack40
import com.itantra.ui.theme.iTantraBlack60
import com.itantra.ui.theme.iTantraBlack80
import com.itantra.ui.theme.iTantraBorder
import com.itantra.ui.theme.iTantraCardAlt
import com.itantra.ui.theme.iTantraSuccess
import com.itantra.ui.theme.iTantraWhite
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class SpeechMessage(
    val text: String,
    val isLocal: Boolean,
    val language: String = "hi",
    val timestamp: Long = System.currentTimeMillis()
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PeerSessionScreen(
    peerId: String,
    viewModel: MainViewModel,
    onBack: () -> Unit
) {
    val peers by viewModel.knownPeers.collectAsState()
    val peer = peers.find { it.deviceId == peerId }

    val messages = remember {
        mutableStateListOf(
            SpeechMessage("P2P session initialized. Connected via ${peer?.connectionType?.name ?: "Wi-Fi Direct"}.", false, "en")
        )
    }

    var textInput by remember { mutableStateOf("") }
    var isPtt by remember { mutableStateOf(false) }
    val listState = rememberLazyListState()

    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) listState.animateScrollToItem(messages.lastIndex)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(iTantraBackground)
    ) {
        // ── Top Bar ───────────────────────────────────────────────────
        TopAppBar(
            title = {
                Column {
                    Text(
                        text = peer?.deviceName ?: peerId,
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                        color = iTantraBlack
                    )
                    Text(
                        text = if (peer?.isConnected == true) "Active Link · ${peer.rssi} dBm" else "Disconnected",
                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp),
                        color = if (peer?.isConnected == true) iTantraSuccess else iTantraBlack40
                    )
                }
            },
            navigationIcon = {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = iTantraBlack)
                }
            },
            colors = TopAppBarDefaults.topAppBarColors(containerColor = iTantraWhite)
        )

        // ── Speech Log ───────────────────────────────────────────────
        LazyColumn(
            state = listState,
            modifier = Modifier
                .weight(1f)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            contentPadding = PaddingValues(vertical = 12.dp)
        ) {
            if (messages.isEmpty()) {
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 80.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(Icons.Filled.Mic, contentDescription = null, tint = iTantraBlack40, modifier = Modifier.size(40.dp))
                            Spacer(Modifier.height(12.dp))
                            Text("Hold PTT to start speaking", style = MaterialTheme.typography.bodyMedium, color = iTantraBlack40)
                        }
                    }
                }
            }
            items(messages) { msg ->
                SpeechBubble(msg = msg)
            }
        }

        // ── Input Row ─────────────────────────────────────────────────
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(iTantraWhite)
                .border(1.dp, iTantraBorder)
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(46.dp)
                    .clip(CircleShape)
                    .background(if (isPtt) Color(0xFFDC2626) else iTantraBlack)
                    .clickable {
                        isPtt = !isPtt
                        if (!isPtt && textInput.isBlank()) {
                            messages.add(SpeechMessage("Voice captured — STT engine transcription appears here.", true, "hi"))
                        }
                    },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = if (isPtt) Icons.Filled.Mic else Icons.Filled.MicOff,
                    contentDescription = "PTT",
                    tint = iTantraWhite,
                    modifier = Modifier.size(22.dp)
                )
            }
            Spacer(Modifier.width(8.dp))
            OutlinedTextField(
                value = textInput,
                onValueChange = { textInput = it },
                placeholder = { Text("Type message or speak…", color = iTantraBlack40, fontSize = 13.sp) },
                singleLine = true,
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(22.dp),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                keyboardActions = KeyboardActions(
                    onSend = {
                        if (textInput.isNotBlank()) {
                            messages.add(SpeechMessage(textInput.trim(), isLocal = true))
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
                        messages.add(SpeechMessage(textInput.trim(), isLocal = true))
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
private fun SpeechBubble(msg: SpeechMessage) {
    val alignEnd = msg.isLocal
    val timeStr = SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(msg.timestamp))

    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = if (alignEnd) Alignment.End else Alignment.Start
    ) {
        Box(
            modifier = Modifier
                .clip(
                    RoundedCornerShape(
                        topStart = 18.dp,
                        topEnd = 18.dp,
                        bottomStart = if (alignEnd) 18.dp else 4.dp,
                        bottomEnd = if (alignEnd) 4.dp else 18.dp
                    )
                )
                .background(if (alignEnd) iTantraBlack else iTantraCardAlt)
                .border(
                    width = 1.dp,
                    color = if (alignEnd) iTantraBlack else iTantraBorder,
                    shape = RoundedCornerShape(18.dp)
                )
                .padding(horizontal = 14.dp, vertical = 10.dp)
        ) {
            Column {
                Text(
                    text = msg.text,
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (alignEnd) iTantraWhite else iTantraBlack
                )
                Spacer(Modifier.height(4.dp))
                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "[${msg.language.uppercase()}]",
                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp, fontWeight = FontWeight.Bold),
                        color = if (alignEnd) Color(0xFFD4D4D4) else iTantraBlack60
                    )
                    Text(
                        text = timeStr,
                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp),
                        color = if (alignEnd) Color(0xFF999999) else iTantraBlack40
                    )
                }
            }
        }
    }
}
