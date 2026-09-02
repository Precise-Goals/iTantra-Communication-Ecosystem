package com.itantra

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
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
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.itantra.ui.MainViewModel
import com.itantra.ui.navigation.BottomNavItem
import com.itantra.ui.navigation.ITantraNavHost
import com.itantra.ui.navigation.NavRoute
import com.itantra.ui.navigation.bottomNavItems
import com.itantra.ui.theme.ITantraTheme
import com.itantra.ui.theme.iTantraBackground
import com.itantra.ui.theme.iTantraBlack
import com.itantra.ui.theme.iTantraBlack40
import com.itantra.ui.theme.iTantraBlack60
import com.itantra.ui.theme.iTantraBlack80
import com.itantra.ui.theme.iTantraBorder
import com.itantra.ui.theme.iTantraCard
import com.itantra.ui.theme.iTantraCardAlt
import com.itantra.ui.theme.iTantraWhite

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            ITantraTheme {
                ITantraApp()
            }
        }
    }
}

@Composable
fun ITantraApp() {
    val context = LocalContext.current
    val navController = rememberNavController()
    val viewModel: MainViewModel = viewModel()

    // ── Automatic One-Time Runtime Permissions Request on App Launch ────
    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val allGranted = permissions.values.all { it }
        android.util.Log.d("ITantraApp", "Permissions request result: allGranted=$allGranted")
    }

    LaunchedEffect(Unit) {
        val permissionsToRequest = mutableListOf(
            Manifest.permission.RECORD_AUDIO,
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION
        )

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            permissionsToRequest.add(Manifest.permission.BLUETOOTH_SCAN)
            permissionsToRequest.add(Manifest.permission.BLUETOOTH_ADVERTISE)
            permissionsToRequest.add(Manifest.permission.BLUETOOTH_CONNECT)
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            permissionsToRequest.add(Manifest.permission.NEARBY_WIFI_DEVICES)
            permissionsToRequest.add(Manifest.permission.POST_NOTIFICATIONS)
        }

        val missingPermissions = permissionsToRequest.filter { perm ->
            ContextCompat.checkSelfPermission(context, perm) != PackageManager.PERMISSION_GRANTED
        }

        if (missingPermissions.isNotEmpty()) {
            permissionLauncher.launch(missingPermissions.toTypedArray())
        }
    }

    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route

    // Hide bottom nav on peer session sub-screen
    val showBottomNav = currentRoute != NavRoute.PeerSession.route &&
        !currentRoute.orEmpty().startsWith("peer_session/")

    Scaffold(
        modifier = Modifier
            .fillMaxSize()
            .background(iTantraBackground),
        containerColor = iTantraBackground,
        bottomBar = {
            AnimatedVisibility(
                visible = showBottomNav,
                enter = fadeIn(tween(200)) + slideInVertically(tween(200)) { it },
                exit = fadeOut(tween(200)) + slideOutVertically(tween(200)) { it }
            ) {
                ITantraExclusiveBottomNav(
                    currentRoute = currentRoute,
                    onNavItemClick = { route ->
                        navController.navigate(route) {
                            popUpTo(navController.graph.findStartDestination().id) {
                                saveState = true
                            }
                            launchSingleTop = true
                            restoreState = true
                        }
                    }
                )
            }
        }
    ) { innerPadding ->
        ITantraNavHost(
            navController = navController,
            viewModel = viewModel,
            innerPadding = innerPadding
        )
    }
}

/**
 * Custom Monochromatic White Bottom Navigation Bar with Emphasized Center Radio Button.
 * Order: 1. Home, 2. Radar, 3. Radio (Center Hero Raised Button), 4. Downloads, 5. Assistant
 */
@Composable
private fun ITantraExclusiveBottomNav(
    currentRoute: String?,
    onNavItemClick: (String) -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .navigationBarsPadding(),
        contentAlignment = Alignment.BottomCenter
    ) {
        // ── 1. Bottom Surface (Height 68dp, 5 equal flexbox slots) ───────
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .height(68.dp),
            shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
            color = iTantraWhite,
            shadowElevation = 10.dp,
            border = androidx.compose.foundation.BorderStroke(1.dp, iTantraBorder)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(68.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                bottomNavItems.forEachIndexed { index, item ->
                    val isSelected = currentRoute == item.route

                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height(68.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        if (index == 2) {
                            // Empty slot for the center raised button; tap still navigates to Radio
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .clickable { onNavItemClick(item.route) }
                            )
                        } else {
                            // Standard Slots (Home, Radar, Downloads, Assistant)
                            Column(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .clickable { onNavItemClick(item.route) }
                                    .padding(vertical = 8.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.Center
                            ) {
                                Icon(
                                    imageVector = if (isSelected) item.selectedIcon else item.unselectedIcon,
                                    contentDescription = item.label,
                                    tint = if (isSelected) iTantraBlack else iTantraBlack40,
                                    modifier = Modifier.size(22.dp)
                                )
                                Spacer(Modifier.height(3.dp))
                                Text(
                                    text = item.label,
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        fontSize = 10.sp,
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                                    ),
                                    color = if (isSelected) iTantraBlack else iTantraBlack40,
                                    maxLines = 1
                                )
                            }
                        }
                    }
                }
            }
        }

        // ── 2. Raised Hero Radio Button (Floating Outside Surface to avoid overflow clipping) ──
        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .offset(y = (-18).dp)
                .size(62.dp)
                .shadow(12.dp, CircleShape)
                .clip(CircleShape)
                .background(iTantraBlack)
                .border(2.5.dp, iTantraWhite, CircleShape)
                .clickable { onNavItemClick(NavRoute.Transceiver.route) },
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Filled.GraphicEq,
                contentDescription = "Radio",
                tint = iTantraWhite,
                modifier = Modifier.size(30.dp)
            )
        }
    }
}
