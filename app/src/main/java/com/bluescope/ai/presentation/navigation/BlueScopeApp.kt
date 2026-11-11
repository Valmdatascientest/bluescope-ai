package com.bluescope.ai.presentation.navigation

import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.BluetoothSearching
import androidx.compose.material.icons.automirrored.outlined.LibraryBooks
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.bluescope.ai.presentation.analysis.AnalysisScreen
import com.bluescope.ai.presentation.device.DeviceDetailScreen
import com.bluescope.ai.presentation.gatt.GattScreen
import com.bluescope.ai.presentation.home.HomeScreen
import com.bluescope.ai.presentation.library.LibraryScreen
import com.bluescope.ai.presentation.scanner.ScannerScreen
import com.bluescope.ai.presentation.settings.SettingsScreen

private sealed class TopDestination(
    val route: String,
    val label: String,
    val icon: androidx.compose.ui.graphics.vector.ImageVector,
) {
    data object Home : TopDestination("home", "Accueil", Icons.Outlined.Home)
    data object Scanner : TopDestination("scanner", "Scanner", Icons.AutoMirrored.Outlined.BluetoothSearching)
    data object Library : TopDestination("library", "Bibliothèque", Icons.AutoMirrored.Outlined.LibraryBooks)
    data object Settings : TopDestination("settings", "Paramètres", Icons.Outlined.Settings)
}

private val topDestinations = listOf(
    TopDestination.Home,
    TopDestination.Scanner,
    TopDestination.Library,
    TopDestination.Settings,
)

@Composable
fun BlueScopeApp() {
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val destination = backStackEntry?.destination
    val showNavigation = topDestinations.any { it.route == destination?.route }

    Scaffold(
        bottomBar = {
            if (showNavigation) {
                NavigationBar {
                    topDestinations.forEach { item ->
                        val selected = destination?.hierarchy?.any { it.route == item.route } == true
                        NavigationBarItem(
                            selected = selected,
                            onClick = {
                                navController.navigate(item.route) {
                                    popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            },
                            icon = { Icon(item.icon, contentDescription = item.label) },
                            label = { Text(item.label) },
                        )
                    }
                }
            }
        },
    ) { padding ->
        NavHost(
            navController = navController,
            startDestination = TopDestination.Home.route,
            modifier = Modifier.padding(padding),
        ) {
            composable(TopDestination.Home.route) {
                HomeScreen(
                    onScan = { navController.navigate(TopDestination.Scanner.route) },
                    onDevice = { navController.navigate("device/$it") },
                )
            }
            composable(TopDestination.Scanner.route) {
                ScannerScreen(onDevice = { navController.navigate("device/$it") })
            }
            composable(TopDestination.Library.route) {
                LibraryScreen(onDevice = { navController.navigate("device/$it") })
            }
            composable(TopDestination.Settings.route) { SettingsScreen() }
            composable(
                route = "device/{deviceId}",
                arguments = listOf(navArgument("deviceId") { type = NavType.StringType }),
            ) {
                DeviceDetailScreen(
                    onBack = navController::popBackStack,
                    onGatt = { navController.navigate("gatt/$it") },
                    onAnalysis = { navController.navigate("analysis/$it") },
                )
            }
            composable(
                route = "gatt/{deviceId}",
                arguments = listOf(navArgument("deviceId") { type = NavType.StringType }),
            ) { GattScreen(onBack = navController::popBackStack) }
            composable(
                route = "analysis/{deviceId}",
                arguments = listOf(navArgument("deviceId") { type = NavType.StringType }),
            ) { AnalysisScreen(onBack = navController::popBackStack) }
        }
    }
}
