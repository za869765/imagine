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
import com.za869765.imagine.ui.hub.MaterialHubScreen
import com.za869765.imagine.ui.hub.MaterialLibraryScreen
import com.za869765.imagine.ui.longvideo.LongVideoScreen
import com.za869765.imagine.ui.onboarding.SplashScreen
import com.za869765.imagine.ui.settings.ApiKeyEditScreen
import com.za869765.imagine.ui.settings.SettingsScreen
import com.za869765.imagine.ui.tutorial.TutorialScreen
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
private const val KEY_INIT_VIDEO_MODE = "init_video_mode" // "i2v" → 影片頁開在圖生影模式(教學 i2v 範本用)
private const val KEY_INIT_EXTEND_BASE = "init_extend_base" // 組合延長:原片 file:// uri
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
                    UpdateChecker.check(ctx)?.let { info ->
                        // 只有偵測到「比目前更新的版本」才重開橫幅;同一新版重複偵測不覆蓋使用者已按的關閉
                        if (info.latestVersionCode != updateInfo?.latestVersionCode) bannerDismissed = false
                        updateInfo = info
                    }
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
                // PIN 已移除 — Splash 後直接進素材生成首頁
                SplashScreen(onTimeout = {
                    navController.navigate(Routes.MATERIAL_HUB) {
                        popUpTo(Routes.SPLASH) { inclusive = true }
                    }
                })
            }

            composable(Routes.MATERIAL_HUB) {
                MaterialHubScreen(
                    onPickImage = { navController.navigate(Routes.GENERATE_IMAGE) },
                    onPickVideo = { navController.navigate(Routes.GENERATE_VIDEO) },
                    onOpenLibrary = { navController.navigate(Routes.MATERIAL_LIBRARY) },
                    onSettingsClick = { navController.navigate(Routes.SETTINGS) },
                    onNavSelected = { tab -> handleTabNav(navController, tab) },
                )
            }

            composable(Routes.MATERIAL_LIBRARY) {
                MaterialLibraryScreen(
                    onBack = { navController.popBackStack() },
                    // 沿用 KEY_INIT_MEDIA:false → 編輯/圖生圖(EDIT image),true → 圖生影(GENERATE_VIDEO)。
                    onUseImage = { url, asVideo ->
                        navController.currentBackStackEntry?.savedStateHandle?.apply {
                            set(KEY_INIT_MEDIA, url)
                            if (!asVideo) set(KEY_INIT_EDIT_MODE, "image")
                        }
                        navController.navigate(if (asVideo) Routes.GENERATE_VIDEO else Routes.EDIT)
                    },
                )
            }

            composable(Routes.LONG_VIDEO) {
                LongVideoScreen(
                    onSettingsClick = { navController.navigate(Routes.SETTINGS) },
                    onNavSelected = { tab -> handleTabNav(navController, tab) },
                    // 片段 prompt 一鍵套用 → 帶到文/圖生影頁 (沿用 KEY_INIT_PROMPT 預填)。
                    onUsePrompt = { prompt ->
                        navController.currentBackStackEntry
                            ?.savedStateHandle?.set(KEY_INIT_PROMPT, prompt)
                        navController.navigate(Routes.GENERATE_VIDEO)
                    },
                    // 影片擷取一格(file:// 圖)→ 圖生影頁開在圖生影模式、預填 prompt
                    onUseFrameForVideo = { frameUri, prompt ->
                        navController.currentBackStackEntry?.savedStateHandle?.apply {
                            set(KEY_INIT_MEDIA, frameUri)
                            set(KEY_INIT_PROMPT, prompt)
                            set(KEY_INIT_VIDEO_MODE, "i2v")
                        }
                        navController.navigate(Routes.GENERATE_VIDEO)
                    },
                    // 組合延長:此片某格當圖生影來源 + 帶原片 → 生成成功後 Worker 自動串接原片+新片
                    onCombineExtend = { frameUri, baseVideoUri ->
                        navController.currentBackStackEntry?.savedStateHandle?.apply {
                            set(KEY_INIT_MEDIA, frameUri)
                            set(KEY_INIT_VIDEO_MODE, "i2v")
                            set(KEY_INIT_EXTEND_BASE, baseVideoUri)
                        }
                        navController.navigate(Routes.GENERATE_VIDEO)
                    },
                )
            }

            composable(Routes.TUTORIAL) {
                TutorialScreen(
                    // 沿用 History「use_prompt」既有預填機制:在目前 entry 設 KEY_INIT_PROMPT,
                    // 導到生成頁後由 previousBackStackEntry 取出消費。
                    // usage: t2i→文生圖頁;t2v→影片頁(文生影);i2v→影片頁並開在圖生影模式(範本是純動作,需使用者再選來源圖)。
                    onUsePrompt = { text, usage ->
                        navController.currentBackStackEntry?.savedStateHandle?.apply {
                            set(KEY_INIT_PROMPT, text)
                            if (usage == "i2v") set(KEY_INIT_VIDEO_MODE, "i2v")
                        }
                        navController.navigate(
                            if (usage == "t2i") Routes.GENERATE_IMAGE else Routes.GENERATE_VIDEO,
                        )
                    },
                    // 課程範例圖 → 動起來(圖生影,GENERATE_VIDEO) 或 重繪/編輯(EDIT image 模式)。
                    // 沿用 KEY_INIT_MEDIA;圖為 super-i CDN https URL,由生成端 http(s) 直通。
                    onUseImage = { url, asVideo ->
                        navController.currentBackStackEntry?.savedStateHandle?.apply {
                            set(KEY_INIT_MEDIA, url)
                            if (!asVideo) set(KEY_INIT_EDIT_MODE, "image")
                        }
                        navController.navigate(if (asVideo) Routes.GENERATE_VIDEO else Routes.EDIT)
                    },
                    // 課程示範影片 → 影片修改(影生影,mode=video) 或 影片延長(mode=extend),走 EDIT。
                    onUseVideo = { url, mode ->
                        navController.currentBackStackEntry?.savedStateHandle?.apply {
                            set(KEY_INIT_MEDIA, url)
                            set(KEY_INIT_EDIT_MODE, mode)
                        }
                        navController.navigate(Routes.EDIT)
                    },
                    onSettingsClick = { navController.navigate(Routes.SETTINGS) },
                    onNavSelected = { tab -> handleTabNav(navController, tab) },
                )
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
                        // 圖/影互切錨定 MATERIAL_HUB(素材生成 section root,啟動後永遠在 stack 底)。
                        // 不可錨 GENERATE_IMAGE:從 hub 直接進影片頁時它根本不在 stack,
                        // popUpTo 不可達會讓 saveState 失效 + 畫面一直疊加。
                        navController.navigate(Routes.GENERATE_VIDEO) {
                            popUpTo(Routes.MATERIAL_HUB) { saveState = true }
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
                    // 圖片編輯改為生成頁內切模式(EditPane),不再導頁進 EditScreen → 移除 onOpenImageEdit
                )
            }

            composable(Routes.GENERATE_VIDEO) {
                val initUrl = navController.previousBackStackEntry
                    ?.savedStateHandle?.get<String>(KEY_INIT_MEDIA)
                val initPrompt = navController.previousBackStackEntry
                    ?.savedStateHandle?.get<String>(KEY_INIT_PROMPT)
                val initVideoMode = navController.previousBackStackEntry
                    ?.savedStateHandle?.get<String>(KEY_INIT_VIDEO_MODE)
                val initExtendBase = navController.previousBackStackEntry
                    ?.savedStateHandle?.get<String>(KEY_INIT_EXTEND_BASE)
                // 清除 gate 涵蓋各種來源,否則殘留會汙染下一次進場。
                LaunchedEffect(initUrl, initPrompt, initVideoMode, initExtendBase) {
                    if (initUrl != null || initPrompt != null || initVideoMode != null || initExtendBase != null) {
                        navController.previousBackStackEntry?.savedStateHandle?.apply {
                            remove<String>(KEY_INIT_MEDIA)
                            remove<String>(KEY_INIT_PROMPT)
                            remove<String>(KEY_INIT_VIDEO_MODE)
                            remove<String>(KEY_INIT_EXTEND_BASE)
                        }
                    }
                }
                GenerateVideoScreen(
                    onSwitchToImage = {
                        navController.navigate(Routes.GENERATE_IMAGE) {
                            popUpTo(Routes.MATERIAL_HUB) { saveState = true }
                            launchSingleTop = true
                            restoreState = true
                        }
                    },
                    // 影片延長 / 影片編輯改為生成頁內切模式(EditPane),不再導頁進 EditScreen
                    // → 移除 onExtend / onOpenVideoEdit
                    onSettingsClick = { navController.navigate(Routes.SETTINGS) },
                    onNavSelected = { tab -> handleTabNav(navController, tab) },
                    initialImageUri = initUrl?.let { Uri.parse(it) },
                    initialPrompt = initPrompt,
                    initialVideoMode = initVideoMode,
                    initialExtendBase = initExtendBase,
                )
            }

            composable(Routes.EDIT) {
                val initUrl = navController.previousBackStackEntry
                    ?.savedStateHandle?.get<String>(KEY_INIT_MEDIA)
                val initPrompt = navController.previousBackStackEntry
                    ?.savedStateHandle?.get<String>(KEY_INIT_PROMPT)
                val initEditMode = navController.previousBackStackEntry
                    ?.savedStateHandle?.get<String>(KEY_INIT_EDIT_MODE)
                LaunchedEffect(initUrl, initEditMode) {
                    // initEditMode != null 也要清 (GenerateVideoScreen「延長」只帶 mode、不帶 media,
                    // 否則 KEY_INIT_EDIT_MODE 會殘留在上一頁的 savedStateHandle)
                    if (initUrl != null || initEditMode != null) {
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
                    onBack = { navController.popBackStack() },
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
                                    com.za869765.imagine.data.storage.MaterialLibrary.remove(ctx, e.displayName)
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
                            // 圖片 → 生影片頁 (當輸入圖)
                            "動起來（生影片）" -> {
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
                    onLibraryClick = { navController.navigate(Routes.HISTORY) },
                    onClearedAndReset = {
                        prefs.clearAll()
                        // 清資料後回素材生成首頁 (整個 stack 清掉重來)
                        navController.navigate(Routes.MATERIAL_HUB) {
                            popUpTo(0) { inclusive = true }
                        }
                    },
                    onBack = { navController.popBackStack() },
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
        NavTab.MATERIAL -> Routes.MATERIAL_HUB
        NavTab.LONG_VIDEO -> Routes.LONG_VIDEO
        NavTab.TUTORIAL -> Routes.TUTORIAL
    }
    // popUpTo 用 MATERIAL_HUB(BottomNav 主舞台,啟動後永遠在 stack 底);saveState/restoreState
    // 讓兩頁切換保留各自狀態。
    navController.navigate(target) {
        popUpTo(Routes.MATERIAL_HUB) { saveState = true }
        launchSingleTop = true
        restoreState = true
    }
}
