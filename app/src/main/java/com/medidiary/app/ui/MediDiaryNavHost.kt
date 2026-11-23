package com.medidiary.app.ui

import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.medidiary.app.ui.checkin.CheckInViewModel
import com.medidiary.app.ui.diary.DiaryViewModel
import com.medidiary.app.ui.home.HomeScreen
import com.medidiary.app.ui.settings.SettingsScreen
import com.medidiary.app.ui.theme.Mocha

object Routes {
    const val HOME = "home"
    const val SETTINGS = "settings"
}

/** Two destinations, matching the wireframe: the main screen and the Settings window. */
@Composable
fun MediDiaryNavHost() {
    val nav = rememberNavController()
    // Activity-scoped: an in-progress check-in survives opening Settings and coming back.
    val flowVm: CheckInViewModel = viewModel()
    val diaryVm: DiaryViewModel = viewModel()

    NavHost(
        navController = nav,
        startDestination = Routes.HOME,
        modifier = Modifier.fillMaxSize().background(Mocha.Base),
        enterTransition = { fadeIn() + slideInHorizontally { it / 6 } },
        exitTransition = { fadeOut() },
        popEnterTransition = { fadeIn() },
        popExitTransition = { fadeOut() + slideOutHorizontally { it / 6 } },
    ) {
        composable(Routes.HOME) {
            HomeScreen(flowVm, diaryVm, onOpenSettings = { nav.navigate(Routes.SETTINGS) { launchSingleTop = true } })
        }
        composable(Routes.SETTINGS) {
            SettingsScreen(viewModel(), onBack = { nav.popBackStack() })
        }
    }
}
