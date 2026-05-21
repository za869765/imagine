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
import com.za869765.imagine.data.storage.MediaEntry
import com.za869765.imagine.data.storage.MediaHistory
import com.za869765.imagine.data.update.Installer
import com.za869765.imagine.data.update.UpdateChecker
import com.za869765.imagine.data.update.UpdateInfo
import com.za869765.imagine.lock.AppLockManager
import com.za869765.imagine.ui.component.NavTab
import com.za869765.imagine.ui.component.UpdateBanner
import com.za869765.imagine.ui.edit.EditScreen
import com.za869765.imagine.ui.generate.GenerateImageScreen
import com.za869765.imagine.ui.generate.GenerateVideoScreen
import com.za869765.imagine.ui.history.HistoryDetailScreen
import com.za869765.imagine.ui.history.HistoryScreen
import com.za869765.imagine.ui.onboarding.LockScreen
import com.za869765.imagine.ui.onboarding.PinSetupScreen
import com.za869765.imagine.ui.onboarding.SplashScreen
import com.za869765.imagine.ui.settings.ApiKeyEditScreen
import com.za869765.imagine.ui.settings.ChangePinScreen
import com.za869765.imagine.ui.settings.SettingsScreen
import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import kotlinx.coroutines.launch

private const val KEY_INIT_MEDIA = "init_media_uri"
private const val KEY_INIT_PROMPT = "init_media_prompt"
private const val KEY_HISTORY_URI = "history_entry_uri"

@Composable
fun ImagineRoot() {
    val ctx = LocalContext.current
    val prefs = remember { SecurePrefs.get(ctx) }
    val lockManager = remember { AppLockManager.get(prefs) }
    val navController = rememberNavController()
    val scope = rememberCoroutineScope()

    val currentRoute by navController.currentBackStackEntryAsState()
    val isLocked = lockManager.lockedState.value
    val routeId = currentRoute?.destination?.route
    // Lock overlay 不蓋 onboarding 階段（splash / pin_setup）— 那邊 isLocked 也沒意義。
    val showLockOverlay = isLocked && prefs.isPinSet &&
        routeId != null && routeId != Routes.SPLASH && routeId != Routes.PIN_SETUP

    // ── in-app updater ────────────────────────────────────────────────
    var updateInfo by remember { mutableStateOf<UpdateInfo?>(null) }
    var bannerDismissed by remember { mutableStateOf(false) }
    val installerState by Installer.state.collectAsState()

    // 開機拉一次最新 release (repo 已 public, 不需 PAT)
    LaunchedEffect(Unit) {
        updateInfo = UpdateChecker.check()
    }

    val showUpdateBanner = !bannerDismissed &&
        routeId != null && routeId != Routes.SPLASH && routeId != Routes.PIN_SETUP &&
        !isLocked &&
        (updateInfo != null || installerState.stage != Installer.Stage.Idle)

    Box(modifier = Modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize()) {
            if (showUpdateBanner) {
                UpdateBanner(
                    info = updateInfo,
                    progress = installerState,
                    onUpdateClick = {
                        val info = updateInfo ?: return@UpdateBanner
                        scope.launch { Installer.downloadAndLaunch(ctx, info) }
                    },
                    onDismiss = { bannerDismissed = true },
                )
            }
            NavHost(
                navController = navController,
                startDestination = Routes.SPLASH,
                modifier = Modifier.weight(1f),
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
                    onAnimateImage = { url, prompt ->
                        navController.currentBackStackEntry?.savedStateHandle?.apply {
                            set(KEY_INIT_MEDIA, url)
                            set(KEY_INIT_PROMPT, prompt)
                        }
                        navController.navigate(Routes.GENERATE_VIDEO)
                    },
                    onEditImage = { url, prompt ->
                        navController.currentBackStackEntry?.savedStateHandle?.apply {
                            set(KEY_INIT_MEDIA, url)
                            set(KEY_INIT_PROMPT, prompt)
                        }
                        navController.navigate(Routes.EDIT)
                    },
                )
            }

            composable(Routes.GENERATE_VIDEO) {
                val initUrl = navController.previousBackStackEntry
                    ?.savedStateHandle?.get<String>(KEY_INIT_MEDIA)
                val initPrompt = navController.previousBackStackEntry
                    ?.savedStateHandle?.get<String>(KEY_INIT_PROMPT)
                LaunchedEffect(initUrl) {
                    if (initUrl != null) {
                        navController.previousBackStackEntry?.savedStateHandle?.apply {
                            remove<String>(KEY_INIT_MEDIA)
                            remove<String>(KEY_INIT_PROMPT)
                        }
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
                    initialPrompt = initPrompt,
                )
            }

            composable(Routes.EDIT) {
                val initUrl = navController.previousBackStackEntry
                    ?.savedStateHandle?.get<String>(KEY_INIT_MEDIA)
                val initPrompt = navController.previousBackStackEntry
                    ?.savedStateHandle?.get<String>(KEY_INIT_PROMPT)
                LaunchedEffect(initUrl) {
                    if (initUrl != null) {
                        navController.previousBackStackEntry?.savedStateHandle?.apply {
                            remove<String>(KEY_INIT_MEDIA)
                            remove<String>(KEY_INIT_PROMPT)
                        }
                    }
                }
                EditScreen(
                    onSettingsClick = { navController.navigate(Routes.SETTINGS) },
                    onNavSelected = { tab -> handleTabNav(navController, tab) },
                    initialMediaUri = initUrl?.let { Uri.parse(it) },
                    initialPrompt = initPrompt,
                )
            }

            composable(Routes.HISTORY) {
                HistoryScreen(
                    onNavSelected = { tab -> handleTabNav(navController, tab) },
                    onItemClick = { item ->
                        navController.currentBackStackEntry
                            ?.savedStateHandle?.set(KEY_HISTORY_URI, item.id)
                        navController.navigate(Routes.HISTORY_DETAIL)
                    },
                )
            }

            composable(Routes.HISTORY_DETAIL) {
                val uriStr = navController.previousBackStackEntry
                    ?.savedStateHandle?.get<String>(KEY_HISTORY_URI)
                var entry by remember { mutableStateOf<MediaEntry?>(null) }
                LaunchedEffect(uriStr) {
                    if (uriStr != null) {
                        entry = MediaHistory.findByUri(ctx, Uri.parse(uriStr))
                    }
                }
                HistoryDetailScreen(
                    entry = entry,
                    onBack = { navController.popBackStack() },
                    onDelete = {
                        // 真的刪 MediaStore + PromptIndex，原本只 popBackStack 是假象
                        val e = entry
                        if (e != null) {
                            scope.launch {
                                runCatching {
                                    ctx.contentResolver.delete(e.uri, null, null)
                                    com.za869765.imagine.data.storage.PromptIndex.remove(ctx, e.displayName)
                                }
                            }
                        }
                        navController.popBackStack()
                    },
                    onAction = { label ->
                        val e = entry ?: return@HistoryDetailScreen
                        val url = e.uri.toString()
                        val p = e.prompt.orEmpty()
                        when (label) {
                            "動起來（生影片）", "當參考圖", "延長影片" -> {
                                navController.currentBackStackEntry?.savedStateHandle?.apply {
                                    set(KEY_INIT_MEDIA, url)
                                    set(KEY_INIT_PROMPT, p)
                                }
                                navController.navigate(Routes.GENERATE_VIDEO)
                            }
                            "編輯這張", "編輯這段" -> {
                                navController.currentBackStackEntry?.savedStateHandle?.apply {
                                    set(KEY_INIT_MEDIA, url)
                                    set(KEY_INIT_PROMPT, p)
                                }
                                navController.navigate(Routes.EDIT)
                            }
                            else -> { /* "copy" 之類 HistoryDetailScreen 內處理 */ }
                        }
                    },
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
