package com.medidiary.app.ui

import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Timeline
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.medidiary.app.ui.checkin.CheckInScreen
import com.medidiary.app.ui.checkin.CheckInViewModel
import com.medidiary.app.ui.review.ReviewScreen
import com.medidiary.app.ui.settings.SettingsScreen
import com.medidiary.app.ui.timeline.TimelineScreen
import com.medidiary.app.ui.transcript.TranscriptScreen

object Routes {
    const val CHECK_IN = "checkin"
    const val TRANSCRIPT = "transcript"
    const val REVIEW = "review"
    const val TIMELINE = "timeline"
    const val SETTINGS = "settings"
}

@Composable
fun MediDiaryNavHost() {
    val nav = rememberNavController()
    // Activity-scoped so the in-progress check-in survives moving between flow screens.
    val flowVm: CheckInViewModel = viewModel()
    val backStack by nav.currentBackStackEntryAsState()
    val route = backStack?.destination?.route

    fun goTab(r: String) = nav.navigate(r) {
        popUpTo(nav.graph.findStartDestination().id) { saveState = true }
        launchSingleTop = true
        restoreState = true
    }

    Scaffold(
        bottomBar = {
            NavigationBar {
                NavigationBarItem(
                    selected = route in setOf(Routes.CHECK_IN, Routes.TRANSCRIPT, Routes.REVIEW),
                    onClick = { goTab(Routes.CHECK_IN) }, icon = { Icon(Icons.Filled.Mic, null) }, label = { Text("Check-in") },
                )
                NavigationBarItem(
                    selected = route == Routes.TIMELINE,
                    onClick = { goTab(Routes.TIMELINE) }, icon = { Icon(Icons.Filled.Timeline, null) }, label = { Text("Timeline") },
                )
                NavigationBarItem(
                    selected = route == Routes.SETTINGS,
                    onClick = { goTab(Routes.SETTINGS) }, icon = { Icon(Icons.Filled.Settings, null) }, label = { Text("Settings") },
                )
            }
        },
    ) { padding ->
        NavHost(nav, startDestination = Routes.CHECK_IN, modifier = Modifier.padding(padding)) {
            composable(Routes.CHECK_IN) {
                CheckInScreen(
                    vm = flowVm,
                    onTranscriptReady = { nav.navigate(Routes.TRANSCRIPT) { launchSingleTop = true } },
                    onTypeInstead = { nav.navigate(Routes.TRANSCRIPT) { launchSingleTop = true } },
                )
            }
            composable(Routes.TRANSCRIPT) {
                TranscriptScreen(
                    vm = flowVm,
                    onExtracted = { nav.navigate(Routes.REVIEW) { launchSingleTop = true } },
                    onBack = { nav.popBackStack(Routes.CHECK_IN, inclusive = false) },
                )
            }
            composable(Routes.REVIEW) {
                ReviewScreen(
                    vm = flowVm,
                    onSaved = { goTab(Routes.TIMELINE) },
                    onDiscard = { nav.popBackStack(Routes.CHECK_IN, inclusive = false) },
                )
            }
            composable(Routes.TIMELINE) { TimelineScreen(viewModel()) }
            composable(Routes.SETTINGS) { SettingsScreen(viewModel()) }
        }
    }
}
