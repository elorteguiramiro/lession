package com.RamiroElortegui.lession.ui.navigation

import android.app.Application
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Timeline
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.RamiroElortegui.lession.ui.screens.analysis.AnalysisScreen
import com.RamiroElortegui.lession.ui.screens.analysis.AnalysisViewModel
import com.RamiroElortegui.lession.ui.screens.detail.DetailScreen
import com.RamiroElortegui.lession.ui.screens.detail.DetailViewModel
import com.RamiroElortegui.lession.ui.screens.devices.DeviceDetailScreen
import com.RamiroElortegui.lession.ui.screens.devices.DevicesScreen
import com.RamiroElortegui.lession.ui.screens.devices.DevicesViewModel
import com.RamiroElortegui.lession.ui.screens.history.HistoryScreen
import com.RamiroElortegui.lession.ui.screens.history.HistoryViewModel
import com.RamiroElortegui.lession.ui.screens.measurement.MeasurementScreen
import com.RamiroElortegui.lession.ui.screens.measurement.MeasurementViewModel

sealed class BottomNavItem(val screen: Screen, val title: String, val icon: ImageVector) {
    data object Measurement : BottomNavItem(Screen.Measurement, "Métricas", Icons.Default.Timeline)
    data object History : BottomNavItem(Screen.History, "Historial", Icons.Default.History)
}

@Composable
fun MainScreen() {
    val navController = rememberNavController()
    val items = listOf(
        BottomNavItem.Measurement,
        BottomNavItem.History
    )

    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route

    val showBottomBar = currentRoute in items.map { it.screen.route }

    Scaffold(
        bottomBar = {
            if (showBottomBar) {
                NavigationBar {
                    items.forEach { item ->
                        NavigationBarItem(
                            icon = { Icon(item.icon, contentDescription = item.title) },
                            label = { Text(item.title) },
                            selected = currentRoute == item.screen.route,
                            onClick = {
                                if (currentRoute != item.screen.route) {
                                    navController.navigate(item.screen.route) {
                                        popUpTo(navController.graph.findStartDestination().id) {
                                            saveState = true
                                        }
                                        launchSingleTop = true
                                        restoreState = true
                                    }
                                }
                            }
                        )
                    }
                }
            }
        }
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = Screen.Measurement.route,
            modifier = Modifier.padding(innerPadding)
        ) {
            composable(Screen.Measurement.route) {
                val context = LocalContext.current
                val viewModel: MeasurementViewModel = viewModel(
                    factory = object : ViewModelProvider.Factory {
                        @Suppress("UNCHECKED_CAST")
                        override fun <T : ViewModel> create(modelClass: Class<T>): T {
                            return MeasurementViewModel(context.applicationContext as Application) as T
                        }
                    }
                )
                MeasurementScreen(
                    viewModel = viewModel,
                    onNavigateToDevices = { navController.navigate(Screen.Devices.route) }
                )
            }
            composable(Screen.Devices.route) {
                val context = LocalContext.current
                val viewModel: DevicesViewModel = viewModel(
                    factory = object : ViewModelProvider.Factory {
                        @Suppress("UNCHECKED_CAST")
                        override fun <T : ViewModel> create(modelClass: Class<T>): T {
                            return DevicesViewModel(context.applicationContext as Application) as T
                        }
                    }
                )
                DevicesScreen(
                    viewModel = viewModel,
                    onBack = { navController.popBackStack() },
                    onNavigateToDetail = { navController.navigate(Screen.DeviceDetail.route) }
                )
            }
            composable(Screen.DeviceDetail.route) {
                val parentEntry = remember { navController.getBackStackEntry(Screen.Devices.route) }
                val viewModel: DevicesViewModel = viewModel(parentEntry)
                DeviceDetailScreen(
                    viewModel = viewModel,
                    onBack = { navController.popBackStack() }
                )
            }
            composable(Screen.History.route) {
                val viewModel: HistoryViewModel = viewModel()
                HistoryScreen(
                    viewModel = viewModel,
                    onSessionClick = { sessionId ->
                        navController.navigate(Screen.Detail.createRoute(sessionId))
                    },
                    onNavigateToAnalysis = {
                        navController.navigate(Screen.Analysis.route)
                    }
                )
            }
            composable(Screen.Analysis.route) {
                val viewModel: AnalysisViewModel = viewModel()
                AnalysisScreen(
                    viewModel = viewModel,
                    onBack = { navController.popBackStack() }
                )
            }
            composable(
                route = Screen.Detail.route,
                arguments = listOf(navArgument("sessionId") { type = NavType.LongType })
            ) { backStackEntry ->
                val sessionId = backStackEntry.arguments?.getLong("sessionId") ?: 0L
                val context = LocalContext.current
                val viewModel: DetailViewModel = viewModel(
                    factory = object : ViewModelProvider.Factory {
                        @Suppress("UNCHECKED_CAST")
                        override fun <T : ViewModel> create(modelClass: Class<T>): T {
                            return DetailViewModel(context.applicationContext as Application, sessionId) as T
                        }
                    }
                )
                DetailScreen(
                    viewModel = viewModel,
                    onBack = { navController.popBackStack() }
                )
            }
        }
    }
}
