package com.za869765.imagine.nav

import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.za869765.imagine.data.prefs.SecurePrefs
import com.za869765.imagine.lock.AppLockManager
import com.za869765.imagine.ui.component.NavTab
import com.za869765.imagine.ui.edit.EditScreen
import com.za869765.imagine.ui.generate.GenerateImageScreen
import com.za869765.imagine.ui.generate.GenerateVideoScreen
import com.za869765.imagine.ui.history.HistoryDetailScreen
import com.za869765.imagine.ui.history.HistoryItem
import com.za869765.imagine.ui.history.HistoryScreen
import com.za869765.imagine.ui.onboarding.LockScreen
import com.za869765.imagine.ui.onboarding.PinSetupScreen
import com.za869765.imagine.ui.onboarding.SplashScreen
import com.za869765.imagine.ui.settings.ApiKeyEditScreen
import com.za869765.imagine.ui.settings.ChangePinScreen
import com.za869765.imagine.ui.settings.SettingsScreen

private const val KEY_INIT_MEDIA = "init_media_uri"

@Composable
fun ImagineRoot() {
    val ctx = LocalContext.current
    val prefs = remember { SecurePrefs.get(ctx) }
    val lockManager = remember { AppLockManager.get(prefs) }
    val navController = rememberNavController()

    val currentRoute by navController.currentBackStackEntryAsState()
    val isLocked = lockManager.lockedState.value
    val routeId = currentRoute?.destination?.route
    // Lock overlay 不蓋 onboarding 階段（splash / pin_setup）— 那邊 isLocked 也沒意義。
    val showLockOverlay = isLocked && prefs.isPinSet &&
        routeId != null && routeId != Routes.SPLASH && routeId != Routes.PIN_SETUP

    Box(modifier = Modifier.fillMaxSize()) {
        NavHost(
            navController = navController,
            startDestination = Routes.SPLASH,
            modifier = Modifier.fillMaxSize(),
        ) {
            composable(Routes.SPLASH) {
                SplashScreen(onTimeout = {
                    val next = if (!prefs.isPinSet) Routes.PIN_SETUP else Routes.GENERATE_IMAGE
                    navController.navigate(next) {
                        popUpTo(Routes.SPLASH) { inclusive = true }
                    }
                })
            }

            composable(Routes.PIN_SETUP) {
                PinSetupScreen(onComplete = {
                    lockManager.unlock()
                    navController.navigate(Routes.GENERATE_IMAGE) {
                        popUpTo(Routes.PIN_SETUP) { inclusive = true }
                    }
                })
            }

            composable(Routes.GENERATE_IMAGE) {
                GenerateImageScreen(
                    onSwitchToVideo = { navController.navigate(Routes.GENERATE_VIDEO) },
                    onSettingsClick = { navController.navigate(Routes.SETTINGS) },
                    onNavSelected = { tab -> handleTabNav(navController, tab) },
                    onAnimateImage = { url ->
                        navController.currentBackStackEntry
                            ?.savedStateHandle?.set(KEY_INIT_MEDIA, url)
                        navController.navigate(Routes.GENERATE_VIDEO)
                    },
                    onEditImage = { url ->
                        navController.currentBackStackEntry
                            ?.savedStateHandle?.set(KEY_INIT_MEDIA, url)
                        navController.navigate(Routes.EDIT)
                    },
                )
            }

            composable(Routes.GENERATE_VIDEO) {
                val initUrl = navController.previousBackStackEntry
                    ?.savedStateHandle?.get<String>(KEY_INIT_MEDIA)
                LaunchedEffect(initUrl) {
                    if (initUrl != null) {
                        navController.previousBackStackEntry
                            ?.savedStateHandle?.remove<String>(KEY_INIT_MEDIA)
                    }
                }
                GenerateVideoScreen(
                    onSwitchToImage = {
                        navController.navigate(Routes.GENERATE_IMAGE) {
                            popUpTo(Routes.GENERATE_VIDEO) { inclusive = true }
                        }
                    },
                    onSettingsClick = { navController.navigate(Routes.SETTINGS) },
                    onNavSelected = { tab -> handleTabNav(navController, tab) },
                    initialImageUri = initUrl?.let { Uri.parse(it) },
                )
            }

            composable(Routes.EDIT) {
                val initUrl = navController.previousBackStackEntry
                    ?.savedStateHandle?.get<String>(KEY_INIT_MEDIA)
                LaunchedEffect(initUrl) {
                    if (initUrl != null) {
                        navController.previousBackStackEntry
                            ?.savedStateHandle?.remove<String>(KEY_INIT_MEDIA)
                    }
                }
                EditScreen(
                    onSettingsClick = { navController.navigate(Routes.SETTINGS) },
                    onNavSelected = { tab -> handleTabNav(navController, tab) },
                    initialMediaUri = initUrl?.let { Uri.parse(it) },
                )
            }

            composable(Routes.HISTORY) {
                HistoryScreen(
                    onNavSelected = { tab -> handleTabNav(navController, tab) },
                    onItemClick = { _ -> navController.navigate(Routes.HISTORY_DETAIL) },
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
                    onClearedAndReset = {
                        prefs.clearAll()
                        navController.navigate(Routes.PIN_SETUP) {
                            popUpTo(Routes.SETTINGS) { inclusive = true }
                        }
                    },
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
                        navController.popBackStack()
                    },
                )
            }

            composable(Routes.CHANGE_PIN) {
                ChangePinScreen(
                    onBack = { navController.popBackStack() },
                    onComplete = { navController.popBackStack() },
                )
            }
        }

        if (showLockOverlay) {
            // 鎖屏期間擋 back，避免使用者繞過 PIN 跳到底層 NavHost
            BackHandler(enabled = true) { /* swallow */ }
            LockScreen(
                onUnlock = { lockManager.unlock() },
                onForgotPin = {
                    prefs.clearAll()
                    lockManager.unlock()
                    navController.navigate(Routes.PIN_SETUP) {
                        popUpTo(0) { inclusive = true }
                    }
                },
            )
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
