package com.za869765.imagine.ui.grok

import android.annotation.SuppressLint
import android.content.Intent
import android.net.Uri
import android.os.Message
import android.view.ViewGroup
import android.webkit.CookieManager
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import com.za869765.imagine.ui.component.ImagineIconButton
import com.za869765.imagine.ui.component.ImagineScreen
import com.za869765.imagine.ui.component.ImagineTopAppBar

private const val GROK_URL = "https://grok.com"

// 假裝成真實 Chrome 的 UA — 降低部分 OAuth(Google/X)把內嵌 WebView 擋成「不安全瀏覽器」的機率。
private const val UA =
    "Mozilla/5.0 (Linux; Android 14; SM-S908B) AppleWebKit/537.36 (KHTML, like Gecko) " +
        "Chrome/126.0.0.0 Mobile Safari/537.36"

/**
 * 提示詞諮詢 — 內嵌 grok.com「網頁版」(用帳號登入,不是 API)。
 * WebView 持久化 cookie/DOM storage,登入狀態跨次保留;支援登入彈窗(OAuth)導回主視窗。
 * 若某登入方式擋內嵌 WebView,右上角「🌐」可改用系統瀏覽器開 grok.com。
 */
@SuppressLint("SetJavaScriptEnabled")
@Composable
fun GrokChatScreen(onBack: () -> Unit) {
    val ctx = LocalContext.current
    var webView by remember { mutableStateOf<WebView?>(null) }
    var progress by remember { mutableStateOf(0) }

    fun goBackOrExit() {
        val wv = webView
        if (wv != null && wv.canGoBack()) wv.goBack() else onBack()
    }
    BackHandler(enabled = true) { goBackOrExit() }

    ImagineScreen(
        appBar = {
            ImagineTopAppBar(
                title = "提示詞諮詢 · Grok",
                showBack = true,
                onBackClick = { goBackOrExit() },
                trailing = {
                    Row {
                        ImagineIconButton(name = "refresh", onClick = { webView?.reload() })
                        // 逃生口:登入被內嵌擋下時改用系統瀏覽器開 grok.com
                        ImagineIconButton(
                            name = "language",
                            onClick = {
                                runCatching {
                                    ctx.startActivity(
                                        Intent(Intent.ACTION_VIEW, Uri.parse(GROK_URL))
                                            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                                    )
                                }
                            },
                        )
                    }
                },
            )
        },
        showBalanceBar = false,
        scroll = false,
        bottomNav = null,
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            AndroidView(
                modifier = Modifier.fillMaxSize(),
                factory = { c ->
                    WebView(c).apply {
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
                            // 全部導覽留在 WebView 內(包含 grok 站內跳轉)
                            override fun shouldOverrideUrlLoading(
                                view: WebView,
                                request: WebResourceRequest,
                            ): Boolean = false
                        }
                        webChromeClient = object : WebChromeClient() {
                            override fun onProgressChanged(view: WebView, newProgress: Int) {
                                progress = newProgress
                            }

                            // OAuth 登入常開新視窗(window.open / target=_blank);把它導回主 WebView 載入,
                            // 才不會點了登入沒反應。
                            override fun onCreateWindow(
                                view: WebView,
                                isDialog: Boolean,
                                isUserGesture: Boolean,
                                resultMsg: Message,
                            ): Boolean {
                                val transport = resultMsg.obj as? WebView.WebViewTransport ?: return false
                                val popup = WebView(view.context)
                                popup.settings.javaScriptEnabled = true
                                popup.settings.userAgentString = UA
                                popup.webViewClient = object : WebViewClient() {
                                    override fun shouldOverrideUrlLoading(
                                        v: WebView,
                                        request: WebResourceRequest,
                                    ): Boolean {
                                        view.loadUrl(request.url.toString())
                                        return true
                                    }
                                }
                                transport.webView = popup
                                resultMsg.sendToTarget()
                                return true
                            }
                        }
                        loadUrl(GROK_URL)
                        webView = this
                    }
                },
            )
            if (progress in 1..99) {
                LinearProgressIndicator(
                    progress = { progress / 100f },
                    modifier = Modifier
                        .fillMaxWidth()
                        .align(Alignment.TopCenter),
                )
            }
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            CookieManager.getInstance().flush() // 把登入 cookie 寫盤,下次進來保持登入
            webView?.destroy()
        }
    }
}
