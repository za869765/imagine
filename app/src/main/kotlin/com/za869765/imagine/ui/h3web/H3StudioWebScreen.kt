package com.za869765.imagine.ui.h3web

import android.annotation.SuppressLint
import android.app.Activity
import android.app.DownloadManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Environment
import android.os.Message
import android.view.ViewGroup
import android.webkit.CookieManager
import android.webkit.URLUtil
import android.webkit.ValueCallback
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.za869765.imagine.Constants
import com.za869765.imagine.data.h3.H3Client
import com.za869765.imagine.data.prefs.SecurePrefs
import com.za869765.imagine.ui.component.ImagineBottomNav
import com.za869765.imagine.ui.component.ImagineIconButton
import com.za869765.imagine.ui.component.ImagineScreen
import com.za869765.imagine.ui.component.ImagineTopAppBar
import com.za869765.imagine.ui.component.NavTab

private const val DEFAULT_H3_URL = "https://h3-studio.pages.dev"
private const val UA = Constants.GROK_WEBVIEW_UA

/** H3 Studio 網址:設定裡有有效的 H3 網址就用它,否則用預設站。 */
fun h3StudioUrl(ctx: Context): String {
    val prefs = SecurePrefs.get(ctx)
    return if (H3Client.isBaseUrlValid(prefs)) H3Client.baseUrl(prefs) else DEFAULT_H3_URL
}

/**
 * v2.0.5 H3 Studio 分頁的 WebView 保管者 — 由 ImagineRoot(NavHost 之上)持有,
 * 切到其他分頁時 WebView 只從畫面拔下、不銷毀,回來不重載;Activity 結束才 destroy。
 * 登入靠網站 session cookie(HttpOnly、長效),CookieManager.flush() 寫盤,App 重開仍維持登入。
 */
class H3WebHolder {
    var webView: WebView? = null
        private set
    var progress by mutableIntStateOf(0)

    // Google 登入(GIS)走 window.open 彈窗並以 window.opener 回傳結果 → 彈窗要真的顯示成子視窗,不能導回主頁
    var popup by mutableStateOf<WebView?>(null)

    // 網站的 input type=file(上傳圖片/影片/參考音)
    var fileCallback: ValueCallback<Array<Uri>>? = null
    var launchChooser: ((Intent) -> Boolean)? = null

    fun closePopup() {
        val p = popup ?: return
        popup = null
        (p.parent as? ViewGroup)?.removeView(p)
        p.destroy()
    }

    @SuppressLint("SetJavaScriptEnabled")
    fun obtain(ctx: Context): WebView {
        webView?.let { wv ->
            (wv.parent as? ViewGroup)?.removeView(wv)
            return wv
        }
        val wv = WebView(ctx).apply {
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT,
            )
            with(settings) {
                javaScriptEnabled = true
                domStorageEnabled = true
                databaseEnabled = true
                javaScriptCanOpenWindowsAutomatically = true
                setSupportMultipleWindows(true)
                userAgentString = UA
                mediaPlaybackRequiresUserGesture = false
            }
            CookieManager.getInstance().setAcceptCookie(true)
            CookieManager.getInstance().setAcceptThirdPartyCookies(this, true)
            webViewClient = object : WebViewClient() {
                override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean = false

                override fun onPageFinished(view: WebView, url: String?) {
                    CookieManager.getInstance().flush()
                }
            }
            webChromeClient = object : WebChromeClient() {
                override fun onProgressChanged(view: WebView, newProgress: Int) {
                    this@H3WebHolder.progress = newProgress
                }

                override fun onCreateWindow(
                    view: WebView,
                    isDialog: Boolean,
                    isUserGesture: Boolean,
                    resultMsg: Message,
                ): Boolean {
                    val transport = resultMsg.obj as? WebView.WebViewTransport ?: return false
                    closePopup()
                    val p = WebView(view.context).apply {
                        layoutParams = ViewGroup.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT,
                            ViewGroup.LayoutParams.MATCH_PARENT,
                        )
                        settings.javaScriptEnabled = true
                        settings.domStorageEnabled = true
                        settings.javaScriptCanOpenWindowsAutomatically = true
                        settings.userAgentString = UA
                        CookieManager.getInstance().setAcceptThirdPartyCookies(this, true)
                        webViewClient = object : WebViewClient() {
                            override fun shouldOverrideUrlLoading(v: WebView, request: WebResourceRequest): Boolean = false
                        }
                        webChromeClient = object : WebChromeClient() {
                            override fun onCloseWindow(window: WebView) {
                                closePopup()
                            }
                        }
                    }
                    this@H3WebHolder.popup = p
                    transport.webView = p
                    resultMsg.sendToTarget()
                    return true
                }

                override fun onShowFileChooser(
                    webView: WebView,
                    filePathCallback: ValueCallback<Array<Uri>>,
                    fileChooserParams: WebChromeClient.FileChooserParams,
                ): Boolean {
                    fileCallback?.onReceiveValue(null)
                    fileCallback = filePathCallback
                    val intent = runCatching { fileChooserParams.createIntent() }
                        .getOrElse { Intent(Intent.ACTION_GET_CONTENT).setType("*/*").addCategory(Intent.CATEGORY_OPENABLE) }
                    if (fileChooserParams.mode == WebChromeClient.FileChooserParams.MODE_OPEN_MULTIPLE) {
                        intent.putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true)
                    }
                    val launched = launchChooser?.invoke(intent) == true
                    if (!launched) {
                        fileCallback = null
                        filePathCallback.onReceiveValue(null)
                    }
                    return true
                }
            }
            // 成品「⬇ 下載」:帶登入 cookie 交給系統下載管理員,存到「下載」資料夾
            setDownloadListener { url, userAgent, contentDisposition, mimeType, _ ->
                runCatching {
                    val name = URLUtil.guessFileName(url, contentDisposition, mimeType)
                    val req = DownloadManager.Request(Uri.parse(url))
                        .addRequestHeader("Cookie", CookieManager.getInstance().getCookie(url).orEmpty())
                        .addRequestHeader("User-Agent", userAgent)
                        .setMimeType(mimeType)
                        .setTitle(name)
                        .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
                        .setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, name)
                    (ctx.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager).enqueue(req)
                    Toast.makeText(ctx, "開始下載：$name", Toast.LENGTH_SHORT).show()
                }.onFailure {
                    Toast.makeText(ctx, "下載失敗：${it.message?.take(60)}", Toast.LENGTH_LONG).show()
                }
            }
            loadUrl(h3StudioUrl(ctx))
        }
        webView = wv
        return wv
    }

    fun destroy() {
        CookieManager.getInstance().flush()
        fileCallback?.onReceiveValue(null)
        fileCallback = null
        closePopup()
        webView?.let { (it.parent as? ViewGroup)?.removeView(it); it.destroy() }
        webView = null
    }
}

/**
 * v2.0.5 H3 Studio 分頁 — 內嵌 h3-studio.pages.dev(AI 導演台等完整網頁版),用 Google 帳號登入。
 * 返回鍵先在網頁內上一頁;右上「🌐」改用系統瀏覽器開(登入被內嵌擋下時的備援)。
 */
@Composable
fun H3StudioWebScreen(
    holder: H3WebHolder,
    onBack: () -> Unit,
    onNavSelected: (NavTab) -> Unit,
) {
    val ctx = LocalContext.current

    val chooserLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        val cb = holder.fileCallback
        holder.fileCallback = null
        val data = result.data
        val uris: Array<Uri>? = if (result.resultCode != Activity.RESULT_OK || data == null) {
            null
        } else {
            val clip = data.clipData
            if (clip != null && clip.itemCount > 0) {
                Array(clip.itemCount) { clip.getItemAt(it).uri }
            } else {
                data.data?.let { arrayOf(it) }
            }
        }
        cb?.onReceiveValue(uris)
    }
    DisposableEffect(chooserLauncher) {
        holder.launchChooser = { intent ->
            runCatching { chooserLauncher.launch(intent) }.isSuccess
        }
        onDispose { holder.launchChooser = null }
    }

    fun goBackOrExit() {
        if (holder.popup != null) {
            holder.closePopup()
            return
        }
        val wv = holder.webView
        if (wv != null && wv.canGoBack()) wv.goBack() else onBack()
    }
    BackHandler(enabled = true) { goBackOrExit() }

    // 背景/離開分頁時把登入 cookie 寫盤
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val obs = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_PAUSE) CookieManager.getInstance().flush()
        }
        lifecycleOwner.lifecycle.addObserver(obs)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(obs)
            CookieManager.getInstance().flush()
        }
    }

    ImagineScreen(
        appBar = {
            ImagineTopAppBar(
                title = "H3 Studio",
                trailing = {
                    Row {
                        ImagineIconButton(name = "refresh", onClick = { holder.webView?.reload() })
                        ImagineIconButton(
                            name = "language",
                            onClick = {
                                runCatching {
                                    ctx.startActivity(
                                        Intent(Intent.ACTION_VIEW, Uri.parse(holder.webView?.url ?: h3StudioUrl(ctx)))
                                            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                                    )
                                }
                            },
                        )
                    }
                },
            )
        },
        scroll = false,
        bottomNav = { ImagineBottomNav(active = NavTab.H3, onTabSelected = onNavSelected) },
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            AndroidView(
                modifier = Modifier.fillMaxSize(),
                factory = { c -> holder.obtain(c) },
            )
            val progress = holder.progress
            if (progress in 1..99) {
                LinearProgressIndicator(
                    progress = { progress / 100f },
                    modifier = Modifier
                        .fillMaxWidth()
                        .align(Alignment.TopCenter),
                )
            }
            val popup = holder.popup
            if (popup != null) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(MaterialTheme.colorScheme.surface),
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(MaterialTheme.colorScheme.surfaceVariant)
                            .padding(start = 14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = "登入視窗",
                            fontSize = 13.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.weight(1f),
                        )
                        ImagineIconButton(name = "close", onClick = { holder.closePopup() })
                    }
                    key(popup) {
                        AndroidView(
                            modifier = Modifier.fillMaxWidth().weight(1f),
                            factory = {
                                (popup.parent as? ViewGroup)?.removeView(popup)
                                popup
                            },
                        )
                    }
                }
            }
        }
    }
}
