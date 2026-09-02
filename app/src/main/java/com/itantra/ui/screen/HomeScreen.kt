package com.itantra.ui.screen

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
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
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CellTower
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Translate
import androidx.compose.material.icons.filled.Verified
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.itantra.R
import com.itantra.domain.model.AppMetadata
import com.itantra.domain.model.DownloadState
import com.itantra.domain.model.ModelPack
import com.itantra.ui.MainViewModel
import com.itantra.ui.component.OnboardingDialog
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

@Composable
fun HomeScreen(
    viewModel: MainViewModel,
    onNavigateToTransceiver: () -> Unit,
    onNavigateToDownloads: () -> Unit
) {
    val profile by viewModel.deviceProfile.collectAsState()
    val downloadStates by viewModel.downloadStates.collectAsState()

    val corePacks = ModelPack.coreTransceiverPacks()
    val allCoreDownloaded = corePacks.all { downloadStates[it] is DownloadState.Downloaded }

    var showProfileEditDialog by remember { mutableStateOf(false) }

    // Show onboarding dialog if callsign not set
    if (profile != null && !profile!!.isProfileComplete) {
        OnboardingDialog(
            buildAlias = profile!!.buildAlias,
            onSave = { name -> viewModel.saveDisplayName(name) }
        )
    }

    if (showProfileEditDialog) {
        ProfileEditDialog(
            currentName = profile?.displayName.orEmpty(),
            buildAlias = profile?.buildAlias.orEmpty(),
            onDismiss = { showProfileEditDialog = false },
            onSave = { newName ->
                viewModel.saveDisplayName(newName)
                showProfileEditDialog = false
            }
        )
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(iTantraBackground)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(bottom = 110.dp)
        ) {
            // ── TOP BLACK CURVE & PROFILE HEADER (Exact Mockup Match) ──
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(95.dp)
            ) {
                // Smooth black curve across the top
                Canvas(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(85.dp)
                ) {
                    val path = Path().apply {
                        moveTo(0f, 0f)
                        lineTo(size.width, 0f)
                        lineTo(size.width, size.height * 0.65f)
                        cubicTo(
                            size.width * 0.75f, size.height * 1.15f,
                            size.width * 0.25f, size.height * 1.15f,
                            0f, size.height * 0.65f
                        )
                        close()
                    }
                    drawPath(path, color = Color(0xFF0A0A0A))
                }

                // Profile circular badge intersecting the curve on the right
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(end = 28.dp)
                        .size(46.dp)
                        .clip(CircleShape)
                        .background(iTantraWhite)
                        .border(2.dp, iTantraBlack, CircleShape)
                        .clickable { showProfileEditDialog = true },
                    contentAlignment = Alignment.Center
                ) {
                    if (profile?.displayName != null) {
                        Text(
                            text = profile!!.displayName!!.take(2).uppercase(),
                            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                            color = iTantraBlack
                        )
                    } else {
                        Icon(
                            imageVector = Icons.Filled.Person,
                            contentDescription = "Profile",
                            tint = iTantraBlack,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                }
            }

            Spacer(Modifier.height(16.dp))

            // ── HERO TEXT: Title + Subtitle + Get Started Button ───────
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 28.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "iTantra.",
                    style = MaterialTheme.typography.displayMedium.copy(
                        fontWeight = FontWeight.Bold,
                        fontSize = 42.sp,
                        letterSpacing = (-1).sp
                    ),
                    color = iTantraBlack
                )

                Spacer(Modifier.height(14.dp))

                Text(
                    text = "iTantra is a fully offline, AI-powered multilingual voice communication Android application designed for use in zero-connectivity environments",
                    style = MaterialTheme.typography.bodyMedium.copy(
                        fontSize = 14.sp,
                        lineHeight = 22.sp
                    ),
                    color = iTantraBlack80,
                    textAlign = TextAlign.Center
                )

                Spacer(Modifier.height(20.dp))

                // "Get Started" black pill button
                Button(
                    onClick = onNavigateToTransceiver,
                    modifier = Modifier
                        .height(48.dp)
                        .padding(horizontal = 8.dp),
                    shape = RoundedCornerShape(24.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = iTantraBlack,
                        contentColor = iTantraWhite
                    )
                ) {
                    Text(
                        text = "Get Started",
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontWeight = FontWeight.Bold,
                            fontSize = 16.sp
                        ),
                        modifier = Modifier.padding(horizontal = 16.dp)
                    )
                }
            }

            Spacer(Modifier.height(18.dp))

            // ── HERO ILLUSTRATION (Communicator holding phone) ─────────
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp),
                contentAlignment = Alignment.Center
            ) {
                Image(
                    painter = painterResource(id = R.drawable.home_hero),
                    contentDescription = "iTantra Communicator",
                    modifier = Modifier
                        .height(260.dp)
                        .fillMaxWidth(),
                    contentScale = ContentScale.Fit
                )
            }

            Spacer(Modifier.height(24.dp))

            // ── BENTO GRID SECTION (High border-radius, Icons only) ────
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "System Architecture",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                        color = iTantraBlack
                    )
                    Text(
                        text = "PS-26173 Specifications",
                        style = MaterialTheme.typography.labelSmall,
                        color = iTantraBlack60
                    )
                }

                // Row 1: 4GB RAM + 10 Indic Languages
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    BentoCard(
                        icon = Icons.Filled.Memory,
                        title = "4GB+ RAM Target",
                        subtitle = "Optimized for Low & Mid-range Phones",
                        detail = "Runs smoothly without thermal throttling on budget chipsets.",
                        modifier = Modifier.weight(1f)
                    )
                    BentoCard(
                        icon = Icons.Filled.Translate,
                        title = "10 Indic Languages",
                        subtitle = "AI4Bharat Multilingual",
                        detail = "Hindi, Gujarati, Marathi, Kannada, Malayalam, Tamil, Telugu, Odia, Bengali, English.",
                        modifier = Modifier.weight(1f)
                    )
                }

                // Row 2: Dual Mesh Radio + Neural Voice Transceiver
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    BentoCard(
                        icon = Icons.Filled.CellTower,
                        title = "Dual Mesh Transport",
                        subtitle = "Wi-Fi Direct + Bluetooth",
                        detail = "High-throughput P2P TCP socket (port 8765) with auto-fallback to BLE RFCOMM.",
                        modifier = Modifier.weight(1f)
                    )
                    BentoCard(
                        icon = Icons.Filled.GraphicEq,
                        title = "Neural Transceiver",
                        subtitle = "~200 Bytes / Packet",
                        detail = "Silero VAD detects pauses; STT compresses speech into compact Protobuf frames.",
                        modifier = Modifier.weight(1f)
                    )
                }

                // Row 3: Full Width - Node Identity & Model Status
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(24.dp))
                        .background(iTantraCardAlt)
                        .border(1.dp, iTantraBorder, RoundedCornerShape(24.dp))
                        .padding(18.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.weight(1f)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(40.dp)
                                    .clip(CircleShape)
                                    .background(iTantraWhite)
                                    .border(1.dp, iTantraBorder, CircleShape),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.Fingerprint,
                                    contentDescription = null,
                                    tint = iTantraBlack,
                                    modifier = Modifier.size(22.dp)
                                )
                            }
                            Spacer(Modifier.width(12.dp))
                            Column {
                                Text(
                                    text = "Node: ${profile?.buildAlias ?: "ITantra-5A11"}",
                                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                                    color = iTantraBlack
                                )
                                Text(
                                    text = "Callsign: ${profile?.displayName ?: "Unassigned"} · Status: ${if (allCoreDownloaded) "Mesh Online" else "Models Pending"}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = iTantraBlack60
                                )
                            }
                        }

                        if (!allCoreDownloaded) {
                            Button(
                                onClick = onNavigateToDownloads,
                                shape = RoundedCornerShape(18.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = iTantraBlack,
                                    contentColor = iTantraWhite
                                ),
                                contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 14.dp, vertical = 6.dp)
                            ) {
                                Icon(Icons.Filled.Download, contentDescription = null, modifier = Modifier.size(14.dp))
                                Spacer(Modifier.width(6.dp))
                                Text("Packs", style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold))
                            }
                        }
                    }
                }

                // Row 4: ISRO SIH Mandate Compliance Card
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(24.dp))
                        .background(iTantraWhite)
                        .border(1.5.dp, iTantraBlack, RoundedCornerShape(24.dp))
                        .padding(18.dp)
                ) {
                    Row(verticalAlignment = Alignment.Top) {
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .clip(CircleShape)
                                .background(iTantraCardAlt),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Verified,
                                contentDescription = null,
                                tint = iTantraBlack,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                        Spacer(Modifier.width(12.dp))
                        Column {
                            Text(
                                text = "PS-26173 Mandate Compliance",
                                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                                color = iTantraBlack
                            )
                            Spacer(Modifier.height(4.dp))
                            Text(
                                text = "100% Offline · Zero Cloud Dependencies · Open-Source TinyML Stack (AI4Bharat + ONNX Runtime Mobile). Audio synthesis ensures inclusivity for non-literate citizens in disaster zones.",
                                style = MaterialTheme.typography.bodySmall,
                                color = iTantraBlack80
                            )
                        }
                    }
                }

                Spacer(Modifier.height(10.dp))

                Text(
                    text = "iTantra v2.0.0 · Smart India Hackathon 2026 · Problem #26173",
                    style = MaterialTheme.typography.labelSmall,
                    color = iTantraBlack40,
                    modifier = Modifier.fillMaxWidth(),
                    textAlign = TextAlign.Center
                )
            }
        }
    }
}

@Composable
private fun BentoCard(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    subtitle: String,
    detail: String,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(24.dp))
            .background(iTantraCardAlt)
            .border(1.dp, iTantraBorder, RoundedCornerShape(24.dp))
            .padding(16.dp)
    ) {
        Column {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(iTantraWhite)
                    .border(1.dp, iTantraBorder, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = iTantraBlack,
                    modifier = Modifier.size(18.dp)
                )
            }
            Spacer(Modifier.height(10.dp))
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                color = iTantraBlack
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp, fontWeight = FontWeight.SemiBold),
                color = iTantraBlack60
            )
            Spacer(Modifier.height(6.dp))
            Text(
                text = detail,
                style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp, lineHeight = 16.sp),
                color = iTantraBlack80
            )
        }
    }
}

@Composable
fun ProfileEditDialog(
    currentName: String,
    buildAlias: String,
    onDismiss: () -> Unit,
    onSave: (String) -> Unit
) {
    var name by remember { mutableStateOf(currentName) }
    val isValid = name.trim().length >= 2

    androidx.compose.ui.window.Dialog(
        onDismissRequest = onDismiss
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(28.dp))
                .background(iTantraWhite)
                .border(1.5.dp, iTantraBlack, RoundedCornerShape(28.dp))
                .padding(24.dp)
        ) {
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Node Identity & Hardware",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                        color = iTantraBlack
                    )
                    androidx.compose.material3.IconButton(
                        onClick = onDismiss,
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Close,
                            contentDescription = "Close",
                            tint = iTantraBlack60,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }

                Spacer(Modifier.height(16.dp))

                // Avatar
                val avatarText: String = if (name.isNotBlank()) name.take(2).uppercase() else "IT"
                Box(
                    modifier = Modifier
                        .size(64.dp)
                        .clip(CircleShape)
                        .background(iTantraCardAlt)
                        .border(2.dp, iTantraBlack, CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = avatarText,
                        style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold),
                        color = iTantraBlack
                    )
                }

                Spacer(Modifier.height(16.dp))

                // Editable Callsign Field
                OutlinedTextField(
                    value = name,
                    onValueChange = { newText: String -> name = newText.take(24) },
                    label = { Text("Callsign / User Name") },
                    placeholder = { Text("e.g. Commander, Field-Alpha") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = iTantraBlack,
                        unfocusedBorderColor = iTantraBorder,
                        focusedLabelColor = iTantraBlack,
                        unfocusedLabelColor = iTantraBlack60,
                        cursorColor = iTantraBlack,
                        focusedTextColor = iTantraBlack,
                        unfocusedTextColor = iTantraBlack,
                        focusedContainerColor = iTantraCardAlt,
                        unfocusedContainerColor = iTantraWhite
                    )
                )

                Spacer(Modifier.height(16.dp))

                // Hardware & Radio Specifications Box
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .background(iTantraCardAlt)
                        .border(1.dp, iTantraBorder, RoundedCornerShape(16.dp))
                        .padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = "Device Specifications",
                        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                        color = iTantraBlack
                    )
                    ProfileDetailRow("Device Model", "${android.os.Build.MANUFACTURER.uppercase()} ${android.os.Build.MODEL}")
                    ProfileDetailRow("Build ID", android.os.Build.ID)
                    ProfileDetailRow("Node ID", buildAlias.ifBlank { "ITantra-5A11" })
                    ProfileDetailRow("Android OS", "Android ${android.os.Build.VERSION.RELEASE} (API ${android.os.Build.VERSION.SDK_INT})")
                    ProfileDetailRow("Radio Mesh", "Wi-Fi Direct P2P + Bluetooth 5.x")
                    ProfileDetailRow("Port", "8765 (TCP Protobuf Stream)")
                }

                Spacer(Modifier.height(20.dp))

                Button(
                    onClick = { if (isValid) onSave(name.trim()) },
                    enabled = isValid,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp),
                    shape = RoundedCornerShape(24.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = iTantraBlack,
                        contentColor = iTantraWhite,
                        disabledContainerColor = iTantraBorder,
                        disabledContentColor = iTantraBlack40
                    )
                ) {
                    Text("Save Changes", style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold))
                }
            }
        }
    }
}

@Composable
private fun ProfileDetailRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(text = label, style = MaterialTheme.typography.labelSmall, color = iTantraBlack60)
        Text(text = value, style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold), color = iTantraBlack)
    }
}

