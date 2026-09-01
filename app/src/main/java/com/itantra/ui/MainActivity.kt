package com.itantra.ui

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Dashboard
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.navigation.NavHostController
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.itantra.ui.navigation.NavGraph
import com.itantra.ui.navigation.Screen
import com.itantra.ui.screen.AlertOverlay
import com.itantra.ui.theme.ITantraTheme
import com.itantra.ui.theme.SpaceBlue900
import com.itantra.ui.theme.SurfaceVariant

class MainActivity : ComponentActivity() {

    private val viewModel: MainViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        viewModel.bindService()

        setContent {
            ITantraTheme {
                ITantraApp(viewModel)
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        viewModel.unbindService()
    }
}

data class BottomNavItem(
    val screen: Screen,
    val icon: ImageVector,
    val label: String
)

val bottomNavItems = listOf(
    BottomNavItem(Screen.Dashboard, Icons.Filled.Dashboard, "Command"),
    BottomNavItem(Screen.Transceiver, Icons.Filled.Mic, "Transceiver"),
    BottomNavItem(Screen.SOS, Icons.Filled.Warning, "SOS"),
    BottomNavItem(Screen.Settings, Icons.Filled.Settings, "Settings"),
    BottomNavItem(Screen.Radar, Icons.Filled.Wifi, "Radar")
)

@Composable
fun ITantraApp(viewModel: MainViewModel) {
    val navController = rememberNavController()
    val snackbarHostState = remember { SnackbarHostState() }
    val alertEvent by viewModel.alertFlow.collectAsState(initial = null)

    // Show errors as snackbars
    val errorFlow = viewModel.errorFlow
    LaunchedEffect(Unit) {
        errorFlow.collect { error ->
            snackbarHostState.showSnackbar(
                message = errorCodeToMessage(error.code.name),
                actionLabel = "Dismiss"
            )
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Scaffold(
            modifier = Modifier.fillMaxSize(),
            containerColor = SpaceBlue900,
            snackbarHost = {
                SnackbarHost(snackbarHostState) { data ->
                    Snackbar(
                        snackbarData = data,
                        containerColor = MaterialTheme.colorScheme.errorContainer,
                        contentColor = MaterialTheme.colorScheme.onErrorContainer
                    )
                }
            },
            bottomBar = { ITantraBottomBar(navController) }
        ) { paddingValues ->
            NavGraph(
                navController = navController,
                viewModel = viewModel,
                modifier = Modifier.padding(paddingValues)
            )
        }

        // Full-screen Alert overlay — non-dismissible until TTS completes
        AnimatedVisibility(
            visible = alertEvent != null,
            enter = fadeIn(),
            exit = fadeOut()
        ) {
            alertEvent?.let { event ->
                AlertOverlay(
                    alertEvent = event,
                    onDismissed = { /* Dismissed by TTS completion */ }
                )
            }
        }
    }
}

@Composable
fun ITantraBottomBar(navController: NavHostController) {
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route

    NavigationBar(
        containerColor = SurfaceVariant.copy(alpha = 0.95f),
        contentColor = MaterialTheme.colorScheme.onSurface
    ) {
        bottomNavItems.forEach { item ->
            val selected = currentRoute == item.screen.route
            NavigationBarItem(
                selected = selected,
                onClick = {
                    navController.navigate(item.screen.route) {
                        popUpTo(navController.graph.startDestinationId) { saveState = true }
                        launchSingleTop = true
                        restoreState = true
                    }
                },
                icon = {
                    Icon(imageVector = item.icon, contentDescription = item.label)
                },
                label = {
                    Text(text = item.label, style = MaterialTheme.typography.labelSmall)
                },
                colors = NavigationBarItemDefaults.colors(
                    selectedIconColor = MaterialTheme.colorScheme.primary,
                    selectedTextColor = MaterialTheme.colorScheme.primary,
                    indicatorColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f),
                    unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant
                )
            )
        }
    }
}

private fun errorCodeToMessage(code: String): String = when (code) {
    "MODEL_LOAD_FAILED"      -> "⚠️ AI model unavailable. Check device storage."
    "NETWORK_DROPPED"        -> "📡 Connection lost. Reconnecting..."
    "NETWORK_TIMEOUT"        -> "⏱️ Connection timed out. Retrying..."
    "STT_INFERENCE_FAILED"   -> "🎤 Speech recognition failed. Try again."
    "TTS_SYNTHESIS_FAILED"   -> "🔊 Voice synthesis failed."
    "PERMISSION_DENIED"      -> "🔒 Permission required. Please grant access."
    "BLUETOOTH_UNAVAILABLE"  -> "📶 Bluetooth unavailable. Enable Bluetooth."
    "WIFI_DIRECT_UNAVAILABLE"-> "📡 Wi-Fi Direct unavailable. Switching to Bluetooth."
    else                     -> "⚠️ An error occurred."
}
