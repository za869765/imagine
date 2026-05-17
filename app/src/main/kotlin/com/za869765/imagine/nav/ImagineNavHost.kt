package com.za869765.imagine.nav

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.za869765.imagine.data.prefs.SecurePrefs
import com.za869765.imagine.lock.AppLockManager
import com.za869765.imagine.ui.component.NavTab
import com.za869765.imagine.ui.dialog.ClearDataDialog
import com.za869765.imagine.ui.edit.EditScreen
import com.za869765.imagine.ui.generate.GenerateImageScreen
import com.za869765.imagine.ui.generate.GenerateVideoScreen
import com.za869765.imagine.ui.generate.VideoGeneratingScreen
import com.za869765.imagine.ui.history.HistoryDetailScreen
import com.za869765.imagine.ui.history.HistoryItem
import com.za869765.imagine.ui.history.HistoryScreen
import com.za869765.imagine.ui.onboarding.ApiKeySetupScreen
import com.za869765.imagine.ui.onboarding.LockScreen
import com.za869765.imagine.ui.onboarding.PinSetupScreen
import com.za869765.imagine.ui.onboarding.SplashScreen
import com.za869765.imagine.ui.settings.ApiKeyEditScreen
import com.za869765.imagine.ui.settings.SettingsScreen

@Composable
fun ImagineRoot() {
    val ctx = LocalContext.current
    val prefs = remember { SecurePrefs.get(ctx) }
    val lockManager = remember { AppLockManager.get(prefs) }
    val navController = rememberNavController()

    // First time month switch → auto-reset usage
    LaunchedEffect(Unit) { prefs.maybeAutoResetForNewMonth() }

    NavHost(
        navController = navController,
        startDestination = Routes.SPLASH,
        modifier = Modifier.fillMaxSize(),
    ) {
        composable(Routes.SPLASH) {
            SplashScreen(onTimeout = {
                val next = when {
                    !prefs.isPinSet -> Routes.PIN_SETUP
                    !prefs.isApiKeySet -> Routes.API_KEY_SETUP
                    lockManager.isLocked -> Routes.LOCK
                    else -> Routes.GENERATE_IMAGE
                }
                navController.navigate(next) {
                    popUpTo(Routes.SPLASH) { inclusive = true }
                }
            })
        }

        composable(Routes.PIN_SETUP) {
            PinSetupScreen(onComplete = {
                prefs.biometricEnabled = false
                if (prefs.isApiKeySet) {
                    lockManager.unlock()
                    navController.navigate(Routes.GENERATE_IMAGE) {
                        popUpTo(Routes.PIN_SETUP) { inclusive = true }
                    }
                } else {
                    navController.navigate(Routes.API_KEY_SETUP) {
                        popUpTo(Routes.PIN_SETUP) { inclusive = true }
                    }
                }
            })
        }

        composable(Routes.API_KEY_SETUP) {
            ApiKeySetupScreen(onSaved = {
                lockManager.unlock()
                navController.navigate(Routes.GENERATE_IMAGE) {
                    popUpTo(Routes.API_KEY_SETUP) { inclusive = true }
                }
            })
        }

        composable(Routes.LOCK) {
            LockScreen(
                onUnlock = {
                    lockManager.unlock()
                    navController.navigate(Routes.GENERATE_IMAGE) {
                        popUpTo(Routes.LOCK) { inclusive = true }
                    }
                },
                onForgotPin = {
                    prefs.clearAll()
                    navController.navigate(Routes.PIN_SETUP) {
                        popUpTo(Routes.LOCK) { inclusive = true }
                    }
                },
            )
        }

        composable(Routes.GENERATE_IMAGE) {
            GenerateImageScreen(
                onSwitchToVideo = { navController.navigate(Routes.GENERATE_VIDEO) },
                onSettingsClick = { navController.navigate(Routes.SETTINGS) },
                onNavSelected = { tab -> handleTabNav(navController, tab) },
                onGenerate = { _, _, _, _ -> /* TODO: call API */ },
            )
        }

        composable(Routes.GENERATE_VIDEO) {
            GenerateVideoScreen(
                onSwitchToImage = {
                    navController.navigate(Routes.GENERATE_IMAGE) {
                        popUpTo(Routes.GENERATE_VIDEO) { inclusive = true }
                    }
                },
                onSettingsClick = { navController.navigate(Routes.SETTINGS) },
                onNavSelected = { tab -> handleTabNav(navController, tab) },
                onGenerate = { _, _, _, _, _ ->
                    navController.navigate(Routes.VIDEO_GENERATING)
                },
            )
        }

        composable(Routes.VIDEO_GENERATING) {
            VideoGeneratingScreen(
                onCancel = { navController.popBackStack() },
                onDone = { navController.popBackStack() },
            )
        }

        composable(Routes.EDIT) {
            EditScreen(
                onSettingsClick = { navController.navigate(Routes.SETTINGS) },
                onNavSelected = { tab -> handleTabNav(navController, tab) },
                onExecute = { _, _ -> /* TODO */ },
            )
        }

        composable(Routes.HISTORY) {
            HistoryScreen(
                onNavSelected = { tab -> handleTabNav(navController, tab) },
                onItemClick = { _ ->
                    navController.navigate(Routes.HISTORY_DETAIL)
                },
            )
        }

        composable(Routes.HISTORY_DETAIL) {
            HistoryDetailScreen(
                item = HistoryItem(id = "demo", date = "2026-05-17", isVideo = false),
                onBack = { navController.popBackStack() },
                onDelete = { navController.popBackStack() },
                onAction = {},
            )
        }

        composable(Routes.SETTINGS) {
            SettingsScreen(
                onApiKeyClick = { navController.navigate(Routes.API_KEY_EDIT) },
                onChangePinClick = { navController.navigate(Routes.CHANGE_PIN) },
                onClearDataClick = { /* show confirm dialog handled inside? — kept simple */ },
                onNavSelected = { tab -> handleTabNav(navController, tab) },
            )
        }

        composable(Routes.API_KEY_EDIT) {
            ApiKeyEditScreen(
                onBack = { navController.popBackStack() },
                onSaved = { navController.popBackStack() },
                onRemove = {
                    prefs.apiKey = null
                    prefs.apiKeyVerifiedAt = null
                    navController.navigate(Routes.API_KEY_SETUP) {
                        popUpTo(Routes.SETTINGS) { inclusive = true }
                    }
                },
            )
        }

        composable(Routes.CHANGE_PIN) {
            PinSetupScreen(onComplete = { navController.popBackStack() })
        }
    }
}

private fun handleTabNav(
    navController: androidx.navigation.NavController,
    tab: NavTab,
) {
    val target = when (tab) {
        NavTab.GENERATE -> Routes.GENERATE_IMAGE
        NavTab.EDIT -> Routes.EDIT
        NavTab.HISTORY -> Routes.HISTORY
        NavTab.SETTINGS -> Routes.SETTINGS
    }
    navController.navigate(target) {
        popUpTo(navController.graph.startDestinationId) { saveState = true }
        launchSingleTop = true
        restoreState = true
    }
}
