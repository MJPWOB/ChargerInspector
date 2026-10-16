package com.example.evinspector.ui.nav

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.example.evinspector.AppContainer
import com.example.evinspector.ui.dashboard.DashboardScreen
import com.example.evinspector.ui.dashboard.DashboardViewModel
import com.example.evinspector.ui.history.HistoryScreen
import com.example.evinspector.ui.history.HistoryViewModel
import com.example.evinspector.ui.inspect.InspectScreen
import com.example.evinspector.ui.inspect.InspectViewModel
import com.example.evinspector.ui.start.StartScreen
import com.example.evinspector.ui.start.StartViewModel

object Routes {
    const val START = "start"
    const val HISTORY = "history"
    const val DASHBOARD_PATTERN = "dashboard/{sessionId}"
    const val INSPECT_PATTERN = "inspect/{sessionId}/{chargerNo}"

    fun dashboard(sessionId: Long) = "dashboard/$sessionId"
    fun inspect(sessionId: Long, chargerNo: Int) = "inspect/$sessionId/$chargerNo"
}

@Composable
fun AppNav(container: AppContainer) {
    val navController = rememberNavController()

    // Crash/restart recovery: if an unfinished session exists, go straight back to its dashboard.
    var startRoute by rememberSaveable { mutableStateOf<String?>(null) }
    LaunchedEffect(Unit) {
        if (startRoute == null) {
            val activeId = container.repository.getActiveSessionId()
            startRoute = if (activeId != null) Routes.dashboard(activeId) else Routes.START
        }
    }

    val start = startRoute
    if (start == null) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
        return
    }

    NavHost(navController = navController, startDestination = start) {

        composable(Routes.START) {
            val vm: StartViewModel = viewModel(factory = StartViewModel.factory(container))
            StartScreen(
                vm = vm,
                onStarted = { sessionId ->
                    navController.navigate(Routes.dashboard(sessionId)) {
                        popUpTo(Routes.START) { inclusive = true }
                    }
                },
                onHistory = { navController.navigate(Routes.HISTORY) },
            )
        }

        composable(
            route = Routes.DASHBOARD_PATTERN,
            arguments = listOf(navArgument("sessionId") { type = NavType.LongType }),
        ) { entry ->
            val sessionId = entry.arguments?.getLong("sessionId") ?: return@composable
            val vm: DashboardViewModel = viewModel(factory = DashboardViewModel.factory(container, sessionId))
            DashboardScreen(
                vm = vm,
                onOpenCharger = { number -> navController.navigate(Routes.inspect(sessionId, number)) },
                onHistory = { navController.navigate(Routes.HISTORY) },
                onFinished = {
                    navController.navigate(Routes.START) {
                        popUpTo(Routes.DASHBOARD_PATTERN) { inclusive = true }
                    }
                },
            )
        }

        composable(
            route = Routes.INSPECT_PATTERN,
            arguments = listOf(
                navArgument("sessionId") { type = NavType.LongType },
                navArgument("chargerNo") { type = NavType.IntType },
            ),
        ) { entry ->
            val sessionId = entry.arguments?.getLong("sessionId") ?: return@composable
            val chargerNo = entry.arguments?.getInt("chargerNo") ?: return@composable
            val vm: InspectViewModel = viewModel(factory = InspectViewModel.factory(container, sessionId, chargerNo))
            InspectScreen(vm = vm, onDone = { navController.popBackStack() })
        }

        composable(Routes.HISTORY) {
            val vm: HistoryViewModel = viewModel(factory = HistoryViewModel.factory(container))
            HistoryScreen(vm = vm, onBack = { navController.popBackStack() })
        }
    }
}
