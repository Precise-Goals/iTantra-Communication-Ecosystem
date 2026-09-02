package com.itantra.ui.component

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.itantra.R
import com.itantra.ui.theme.iTantraBlack
import com.itantra.ui.theme.iTantraBlack40
import com.itantra.ui.theme.iTantraBlack60
import com.itantra.ui.theme.iTantraBorder
import com.itantra.ui.theme.iTantraCardAlt
import com.itantra.ui.theme.iTantraWhite

/**
 * Full-screen onboarding dialog shown on first launch.
 * Asks the user to set their display name / callsign.
 */
@Composable
fun OnboardingDialog(
    buildAlias: String,
    onSave: (String) -> Unit
) {
    var name by remember { mutableStateOf("") }
    val isValid = name.trim().length >= 2

    Dialog(
        onDismissRequest = { /* mandatory — cannot be dismissed without setting a name */ },
        properties = DialogProperties(
            dismissOnBackPress = false,
            dismissOnClickOutside = false,
            usePlatformDefaultWidth = false
        )
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .clip(RoundedCornerShape(28.dp))
                .background(iTantraWhite)
                .border(1.5.dp, iTantraBlack, RoundedCornerShape(28.dp))
                .padding(26.dp)
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Image(
                    painter = painterResource(id = R.drawable.app_logo),
                    contentDescription = "iTantra Logo",
                    modifier = Modifier
                        .size(76.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .border(1.dp, iTantraBorder, RoundedCornerShape(16.dp))
                )
                Spacer(Modifier.height(12.dp))
                Text(
                    text = "iTantra.",
                    style = MaterialTheme.typography.headlineMedium.copy(
                        fontWeight = FontWeight.Bold,
                        color = iTantraBlack
                    )
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = "Universal Communication Ecosystem",
                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp),
                    color = iTantraBlack60,
                    textAlign = TextAlign.Center
                )
                Spacer(Modifier.height(20.dp))

                Text(
                    text = "Welcome. Set your callsign to identify your node in the mesh network.",
                    style = MaterialTheme.typography.bodySmall,
                    color = iTantraBlack60,
                    textAlign = TextAlign.Center
                )
                Spacer(Modifier.height(18.dp))

                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it.take(24) },
                    label = { Text("Your name or callsign") },
                    placeholder = { Text("e.g. Commander, Arjun, Echo-1") },
                    singleLine = true,
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.fillMaxWidth(),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(
                        onDone = { if (isValid) onSave(name.trim()) }
                    ),
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
                Spacer(Modifier.height(8.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Hardware Build ID: $buildAlias",
                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                        color = iTantraBlack40
                    )
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
                    Text("Join the Mesh", style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold))
                }
            }
        }
    }
}
