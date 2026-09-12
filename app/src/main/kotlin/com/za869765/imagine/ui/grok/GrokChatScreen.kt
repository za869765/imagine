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
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.za869765.imagine.ui.component.ImagineIcon
import com.za869765.imagine.ui.component.ImagineIconButton
import com.za869765.imagine.ui.component.ImagineScreen
import com.za869765.imagine.ui.component.ImagineTopAppBar
import com.za869765.imagine.ui.util.Clipboard

private const val GROK_URL = "https://grok.com"

// 假裝成真實 Chrome 的 UA — 降低部分 OAuth(Google/X)把內嵌 WebView 擋成「不安全瀏覽器」的機率。
private const val UA =
    "Mozilla/5.0 (Linux; Android 14; SM-S908B) AppleWebKit/537.36 (KHTML, like Gecko) " +
        "Chrome/126.0.0.0 Mobile Safari/537.36"

/**
 * Grok 網頁諮詢 — 內嵌 grok.com「網頁版」(用帳號登入,不是 API)。
 * WebView 持久化 cookie/DOM storage,登入狀態跨次保留;支援登入彈窗(OAuth)導回主視窗。
 * 若某登入方式擋內嵌 WebView,右上角「🌐」可改用系統瀏覽器開 grok.com。
 * UI_REDESIGN_PLAN 3.6:AppBar 下方標示「外部網頁・以你的 Grok 帳號登入」;
 * 「貼上提示詞」由使用者主動按下才讀剪貼簿(不自動讀取),帶回生成頁。
 */
@SuppressLint("SetJavaScriptEnabled")
@Composable
fun GrokChatScreen(
    onBack: () -> Unit,
    onPastePrompt: (String) -> Unit = {},
) {
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
                title = "Grok 網頁諮詢",
                showBack = true,
                onBackClick = { goBackOrExit() },
                trailing = {
                    Row {
                        // 使用者主動按才讀剪貼簿 → 帶回生成頁提示詞欄
                        ImagineIconButton(
                            name = "content_paste",
                            onClick = {
                                val text = Clipboard.paste(ctx)
                                if (text == null) {
                                    Toast.makeText(ctx, "剪貼簿沒有文字，先在 Grok 複製回覆", Toast.LENGTH_SHORT).show()
                                } else {
                                    onPastePrompt(text)
                                }
                            },
                        )
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
        scroll = false,
        bottomNav = null,
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            // 外部網頁狀態列:避免以為 App 的 API Key 等同網頁登入
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.surfaceVariant)
                    .padding(horizontal = 14.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                ImagineIcon(name = "open_in_new", size = 14.dp, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(
                    text = "外部網頁 · 以你的 Grok 帳號登入 · 複製回覆後按右上「貼上」帶回生成頁",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Box(modifier = Modifier.fillMaxWidth().weight(1f)) {
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
    }

    DisposableEffect(Unit) {
        onDispose {
            CookieManager.getInstance().flush() // 把登入 cookie 寫盤,下次進來保持登入
            webView?.destroy()
        }
    }
}
