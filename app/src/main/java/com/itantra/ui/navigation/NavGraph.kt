package com.itantra.ui.navigation

import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material.icons.filled.Radar
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.GraphicEq
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Psychology
import androidx.compose.material.icons.outlined.Radar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import com.itantra.ui.MainViewModel
import com.itantra.ui.screen.AIAssistantScreen
import com.itantra.ui.screen.DownloadsScreen
import com.itantra.ui.screen.HomeScreen
import com.itantra.ui.screen.PeerSessionScreen
import com.itantra.ui.screen.RadarScreen
import com.itantra.ui.screen.TransceiverScreen

sealed class NavRoute(val route: String) {
    data object Home : NavRoute("home")
    data object Transceiver : NavRoute("transceiver")
    data object Radar : NavRoute("radar")
    data object AIAssistant : NavRoute("ai_assistant")
    data object Downloads : NavRoute("downloads")
    data object PeerSession : NavRoute("peer_session/{peerId}") {
        fun withPeer(peerId: String) = "peer_session/$peerId"
    }
}

data class BottomNavItem(
    val route: String,
    val label: String,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector
)

val bottomNavItems = listOf(
    BottomNavItem(NavRoute.Home.route, "Home", Icons.Filled.Home, Icons.Outlined.Home),
    BottomNavItem(NavRoute.Radar.route, "Radar", Icons.Filled.Radar, Icons.Outlined.Radar),
    BottomNavItem(NavRoute.Transceiver.route, "Radio", Icons.Filled.GraphicEq, Icons.Outlined.GraphicEq),
    BottomNavItem(NavRoute.Downloads.route, "Downloads", Icons.Filled.Download, Icons.Outlined.Download),
    BottomNavItem(NavRoute.AIAssistant.route, "Assistant", Icons.Filled.Psychology, Icons.Outlined.Psychology)
)

@Composable
fun ITantraNavHost(
    navController: NavHostController,
    viewModel: MainViewModel,
    innerPadding: PaddingValues
) {
    NavHost(
        navController = navController,
        startDestination = NavRoute.Home.route,
        modifier = Modifier.padding(innerPadding),
        enterTransition = { fadeIn(animationSpec = tween(220)) + slideInHorizontally(tween(220)) { it / 6 } },
        exitTransition = { fadeOut(animationSpec = tween(150)) + slideOutHorizontally(tween(150)) { -it / 6 } },
        popEnterTransition = { fadeIn(animationSpec = tween(220)) + slideInHorizontally(tween(220)) { -it / 6 } },
        popExitTransition = { fadeOut(animationSpec = tween(150)) + slideOutHorizontally(tween(150)) { it / 6 } }
    ) {
        composable(NavRoute.Home.route) {
            HomeScreen(
                viewModel = viewModel,
                onNavigateToTransceiver = { navController.navigate(NavRoute.Transceiver.route) },
                onNavigateToDownloads = { navController.navigate(NavRoute.Downloads.route) }
            )
        }
        composable(NavRoute.Transceiver.route) {
            TransceiverScreen(
                viewModel = viewModel,
                onPeerSelected = { peerId ->
                    navController.navigate(NavRoute.PeerSession.withPeer(peerId))
                },
                onNavigateToDownloads = { navController.navigate(NavRoute.Downloads.route) }
            )
        }
        composable(NavRoute.Radar.route) {
            RadarScreen(viewModel = viewModel)
        }
        composable(NavRoute.AIAssistant.route) {
            AIAssistantScreen(
                viewModel = viewModel,
                onNavigateToDownloads = { navController.navigate(NavRoute.Downloads.route) }
            )
        }
        composable(NavRoute.Downloads.route) {
            DownloadsScreen(viewModel = viewModel)
        }
        composable(
            route = NavRoute.PeerSession.route,
            arguments = listOf(navArgument("peerId") { type = NavType.StringType })
        ) { backStack ->
            val peerId = backStack.arguments?.getString("peerId") ?: return@composable
            PeerSessionScreen(
                peerId = peerId,
                viewModel = viewModel,
                onBack = { navController.popBackStack() }
            )
        }
    }
}
