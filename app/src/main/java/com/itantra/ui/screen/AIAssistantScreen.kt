package com.itantra.ui.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MicOff
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
import androidx.compose.ui.graphics.Color
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

@Composable
fun AIAssistantScreen(
    viewModel: MainViewModel,
    onNavigateToDownloads: () -> Unit
) {
    val aiMessages by viewModel.aiMessages.collectAsState()
    val isThinking by viewModel.isAiThinking.collectAsState()
    val listState = rememberLazyListState()
    var textInput by remember { mutableStateOf("") }
    var isMicActive by remember { mutableStateOf(false) }

    val quickPrompts = listOf(
        "Translate: We need water",
        "How to use Radio PTT?",
        "Emergency SOS protocol",
        "Offline 10 Indic languages"
    )

    LaunchedEffect(aiMessages.size, aiMessages.lastOrNull()?.text) {
        if (aiMessages.isNotEmpty()) {
            listState.scrollToItem(aiMessages.lastIndex)
        }
    }

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
                .padding(horizontal = 20.dp, vertical = 18.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = "AI Tactical Assistant",
                    style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                    color = iTantraBlack
                )
                Text(
                    text = "100% Offline · Multilingual Disaster Triage & Translation",
                    style = MaterialTheme.typography.labelSmall,
                    color = iTantraSuccess
                )
            }
            if (aiMessages.isNotEmpty()) {
                IconButton(onClick = { viewModel.clearAiChat() }) {
                    Icon(Icons.Filled.Clear, contentDescription = "Clear chat", tint = iTantraBlack60)
                }
            }
        }

        // ── Suggested Quick Action Chips ─────────────────────────────
        LazyRow(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(quickPrompts) { prompt ->
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(16.dp))
                        .background(iTantraCardAlt)
                        .border(1.dp, iTantraBorder, RoundedCornerShape(16.dp))
                        .clickable { viewModel.sendAiQuery(prompt) }
                        .padding(horizontal = 12.dp, vertical = 6.dp)
                ) {
                    Text(
                        text = prompt,
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                        color = iTantraBlack
                    )
                }
            }
        }

        Spacer(Modifier.height(8.dp))

        // ── Message Feed ─────────────────────────────────────────────
        LazyColumn(
            state = listState,
            modifier = Modifier
                .weight(1f)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            contentPadding = PaddingValues(vertical = 12.dp)
        ) {
            items(aiMessages, key = { it.id }) { msg ->
                AiMessageBubbleWhite(msg = msg)
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
                        Text("Neural engine inferring…", style = MaterialTheme.typography.labelSmall, color = iTantraBlack60)
                    }
                }
            }
        }

        // ── Input Bar ────────────────────────────────────────────────
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
                    .size(44.dp)
                    .clip(CircleShape)
                    .background(if (isMicActive) Color(0xFFDC2626) else iTantraBlack)
                    .clickable {
                        isMicActive = !isMicActive
                        if (!isMicActive && textInput.isBlank()) {
                            viewModel.sendAiQuery("Translate to Hindi: We need water and medical supplies")
                        }
                    },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = if (isMicActive) Icons.Filled.Mic else Icons.Filled.MicOff,
                    contentDescription = "Voice input",
                    tint = iTantraWhite,
                    modifier = Modifier.size(20.dp)
                )
            }
            Spacer(Modifier.width(8.dp))
            OutlinedTextField(
                value = textInput,
                onValueChange = { textInput = it },
                placeholder = { Text("Ask tactical query or translate…", color = iTantraBlack40, fontSize = 13.sp) },
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
private fun AiMessageBubbleWhite(msg: AiMessage) {
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
                Spacer(Modifier.height(4.dp))
                Text(
                    text = timeStr,
                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp),
                    color = if (isUser) Color(0xFF999999) else iTantraBlack40,
                    modifier = Modifier.align(Alignment.End)
                )
            }
        }
    }
}
