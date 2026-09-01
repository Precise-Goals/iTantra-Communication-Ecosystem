package com.itantra.ui.navigation

import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.core.EaseInOutCubic
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import com.itantra.ui.MainViewModel
import com.itantra.ui.screen.DashboardScreen
import com.itantra.ui.screen.RadarScreen
import com.itantra.ui.screen.SOSScreen
import com.itantra.ui.screen.SettingsScreen
import com.itantra.ui.screen.TransceiverScreen

sealed class Screen(val route: String, val label: String, val iconRes: Int) {
    object Dashboard   : Screen("dashboard",   "Dashboard",   0)
    object Transceiver : Screen("transceiver", "Transceiver", 0)
    object SOS         : Screen("sos",         "SOS",         0)
    object Settings    : Screen("settings",    "Settings",    0)
    object Radar       : Screen("radar",       "Radar",       0)
}

@Composable
fun NavGraph(
    navController: NavHostController,
    viewModel: MainViewModel,
    modifier: Modifier = Modifier
) {
    NavHost(
        navController = navController,
        startDestination = Screen.Dashboard.route,
        modifier = modifier,
        enterTransition = {
            fadeIn(animationSpec = tween(200, easing = EaseInOutCubic)) +
            slideIntoContainer(
                towards = AnimatedContentTransitionScope.SlideDirection.Left,
                animationSpec = tween(300, easing = EaseInOutCubic)
            )
        },
        exitTransition = {
            fadeOut(animationSpec = tween(150)) +
            slideOutOfContainer(
                towards = AnimatedContentTransitionScope.SlideDirection.Left,
                animationSpec = tween(300, easing = EaseInOutCubic)
            )
        },
        popEnterTransition = {
            fadeIn(animationSpec = tween(200)) +
            slideIntoContainer(
                towards = AnimatedContentTransitionScope.SlideDirection.Right,
                animationSpec = tween(300, easing = EaseInOutCubic)
            )
        },
        popExitTransition = {
            fadeOut(animationSpec = tween(150)) +
            slideOutOfContainer(
                towards = AnimatedContentTransitionScope.SlideDirection.Right,
                animationSpec = tween(300, easing = EaseInOutCubic)
            )
        }
    ) {
        composable(Screen.Dashboard.route)   { DashboardScreen(viewModel) }
        composable(Screen.Transceiver.route) { TransceiverScreen(viewModel) }
        composable(Screen.SOS.route)         { SOSScreen(viewModel) }
        composable(Screen.Settings.route)    { SettingsScreen(viewModel) }
        composable(Screen.Radar.route)       { RadarScreen(viewModel) }
    }
}
