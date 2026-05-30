package com.za869765.imagine.nav

import android.net.Uri
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
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
import com.za869765.imagine.ui.component.NavTab
import com.za869765.imagine.ui.component.UpdateBanner
import com.za869765.imagine.ui.edit.EditScreen
import com.za869765.imagine.ui.generate.GenerateImageScreen
import com.za869765.imagine.ui.generate.GenerateVideoScreen
import com.za869765.imagine.ui.history.HistoryDetailScreen
import com.za869765.imagine.ui.history.HistoryScreen
import com.za869765.imagine.ui.onboarding.SplashScreen
import com.za869765.imagine.ui.settings.ApiKeyEditScreen
import com.za869765.imagine.ui.settings.SettingsScreen
import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import kotlinx.coroutines.launch

private const val KEY_INIT_MEDIA = "init_media_uri"
private const val KEY_INIT_PROMPT = "init_media_prompt"
private const val KEY_INIT_EDIT_MODE = "init_edit_mode"   // "image" / "video" / "extend"
private const val KEY_HISTORY_URI = "history_entry_uri"

@Composable
fun ImagineRoot() {
    val ctx = LocalContext.current
    val prefs = remember { SecurePrefs.get(ctx) }
    val navController = rememberNavController()
    val scope = rememberCoroutineScope()

    val currentRoute by navController.currentBackStackEntryAsState()
    val routeId = currentRoute?.destination?.route

    // ── in-app updater ────────────────────────────────────────────────
    var updateInfo by remember { mutableStateOf<UpdateInfo?>(null) }
    var bannerDismissed by remember { mutableStateOf(false) }
    val installerState by Installer.state.collectAsState()

    // v1.0.x: 不只開機檢查 —— 每次 App 回到前景 (ON_START) 都重新偵測,
    // 配合 UpdateChecker 的短 cooldown,新版發佈後不必整個重開就會跳更新橫幅。
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val obs = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_START) {
                scope.launch {
                    UpdateChecker.check(ctx)?.let { updateInfo = it; bannerDismissed = false }
                }
            }
        }
        lifecycleOwner.lifecycle.addObserver(obs)
        onDispose { lifecycleOwner.lifecycle.removeObserver(obs) }
    }

    val showUpdateBanner = !bannerDismissed &&
        routeId != null && routeId != Routes.SPLASH &&
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
                // PIN 已移除 — Splash 後直接進主畫面
                SplashScreen(onTimeout = {
                    navController.navigate(Routes.GENERATE_IMAGE) {
                        popUpTo(Routes.SPLASH) { inclusive = true }
                    }
                })
            }

            composable(Routes.GENERATE_IMAGE) {
                // History「使用此提示詞」帶進來的純文字 prompt(無媒體),一次性消費
                val initPrompt = navController.previousBackStackEntry
                    ?.savedStateHandle?.get<String>(KEY_INIT_PROMPT)
                LaunchedEffect(initPrompt) {
                    if (initPrompt != null) {
                        navController.previousBackStackEntry
                            ?.savedStateHandle?.remove<String>(KEY_INIT_PROMPT)
                    }
                }
                GenerateImageScreen(
                    initialPrompt = initPrompt,
                    onSwitchToVideo = {
                        // popUpTo 用 GENERATE_IMAGE (BottomNav 主舞台,永遠在 stack 底),
                        // 不用 startDestinationId — SPLASH 是 startDestination 但啟動後
                        // inclusive pop 掉了,popUpTo 不可達 destination 時 saveState 不可靠
                        navController.navigate(Routes.GENERATE_VIDEO) {
                            popUpTo(Routes.GENERATE_IMAGE) { saveState = true }
                            launchSingleTop = true
                            restoreState = true
                        }
                    },
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
                            set(KEY_INIT_EDIT_MODE, "image")
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
                            popUpTo(Routes.GENERATE_IMAGE) { saveState = true }
                            launchSingleTop = true
                            restoreState = true
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
                val initEditMode = navController.previousBackStackEntry
                    ?.savedStateHandle?.get<String>(KEY_INIT_EDIT_MODE)
                LaunchedEffect(initUrl) {
                    if (initUrl != null) {
                        navController.previousBackStackEntry?.savedStateHandle?.apply {
                            remove<String>(KEY_INIT_MEDIA)
                            remove<String>(KEY_INIT_PROMPT)
                            remove<String>(KEY_INIT_EDIT_MODE)
                        }
                    }
                }
                EditScreen(
                    onSettingsClick = { navController.navigate(Routes.SETTINGS) },
                    onNavSelected = { tab -> handleTabNav(navController, tab) },
                    initialMediaUri = initUrl?.let { Uri.parse(it) },
                    initialPrompt = initPrompt,
                    initialEditMode = initEditMode,
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
                        // 真的刪 internal file + PromptIndex，原本只 popBackStack 是假象
                        val e = entry
                        if (e != null) {
                            scope.launch {
                                runCatching {
                                    // v1.0.31 起檔案在 ctx.filesDir/media/，path 直接是 file path
                                    val path = e.uri.path
                                    if (path != null) java.io.File(path).delete()
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
                            // 圖片才往生影片頁送 (Img2Vid 起始圖 / Ref 參考圖)
                            "動起來（生影片）", "當參考圖" -> {
                                navController.currentBackStackEntry?.savedStateHandle?.apply {
                                    set(KEY_INIT_MEDIA, url)
                                    set(KEY_INIT_PROMPT, p)
                                }
                                navController.navigate(Routes.GENERATE_VIDEO)
                            }
                            // 影片相關都走 EditScreen，差在 mode hint
                            "延長影片" -> {
                                navController.currentBackStackEntry?.savedStateHandle?.apply {
                                    set(KEY_INIT_MEDIA, url)
                                    set(KEY_INIT_PROMPT, p)
                                    set(KEY_INIT_EDIT_MODE, "extend")
                                }
                                navController.navigate(Routes.EDIT)
                            }
                            "編輯這段" -> {
                                navController.currentBackStackEntry?.savedStateHandle?.apply {
                                    set(KEY_INIT_MEDIA, url)
                                    set(KEY_INIT_PROMPT, p)
                                    set(KEY_INIT_EDIT_MODE, "video")
                                }
                                navController.navigate(Routes.EDIT)
                            }
                            "編輯這張" -> {
                                navController.currentBackStackEntry?.savedStateHandle?.apply {
                                    set(KEY_INIT_MEDIA, url)
                                    set(KEY_INIT_PROMPT, p)
                                    set(KEY_INIT_EDIT_MODE, "image")
                                }
                                navController.navigate(Routes.EDIT)
                            }
                            // 只帶 prompt 文字(無媒體)去文生圖頁,當新的起點
                            "use_prompt" -> {
                                navController.currentBackStackEntry?.savedStateHandle?.apply {
                                    set(KEY_INIT_PROMPT, p)
                                }
                                navController.navigate(Routes.GENERATE_IMAGE)
                            }
                            else -> { /* "copy" 之類 HistoryDetailScreen 內處理 */ }
                        }
                    },
                )
            }

            composable(Routes.SETTINGS) {
                SettingsScreen(
                    onApiKeyClick = { navController.navigate(Routes.API_KEY_EDIT) },
                    onClearedAndReset = {
                        prefs.clearAll()
                        // PIN 已移除 — 清資料後回主畫面 (整個 stack 清掉重來)
                        navController.navigate(Routes.GENERATE_IMAGE) {
                            popUpTo(0) { inclusive = true }
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
            }
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
    // popUpTo 用 GENERATE_IMAGE (BottomNav 主舞台,永遠在 stack 底) 而非
    // navController.graph.startDestinationId(=SPLASH,啟動後被 inclusive pop 掉
    // 不在 stack 內)。popUpTo 不可達 destination 時 Compose Navigation 的
    // saveState 行為不可靠,導致切走再回來 state reset 為 default。
    navController.navigate(target) {
        popUpTo(Routes.GENERATE_IMAGE) { saveState = true }
        launchSingleTop = true
        restoreState = true
    }
}
