package com.za869765.imagine

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import com.za869765.imagine.data.storage.MediaImporter
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * v1.0.46: 接 ACTION_SEND / ACTION_SEND_MULTIPLE 從外部 app (Grok / 相簿 / 瀏覽器)
 * 分享進來的 image 或 video MIME，拷貝到 filesDir/media/ 進 Imagine History。
 *
 * Activity 自己無 UI (透明 theme)，背景拷貝完跳 Toast 即 finish，
 * 不觸發主 app 的 PIN lock 流程 (只是純檔案寫入)。
 */
class ImportActivity : Activity() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val uris = collectUris(intent)
        if (uris.isEmpty()) {
            Toast.makeText(this, "沒收到可匯入的檔案", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        // 限定 S22U 機型，跟 MainActivity 一致；非白名單也照存 (純檔案，不洩 secret)
        // 之後使用者開主 app 仍會被 device guard 擋下
        scope.launch {
            val count = MediaImporter.importAll(this@ImportActivity, uris)
            withContext(Dispatchers.Main) {
                Toast.makeText(
                    this@ImportActivity,
                    if (count > 0) "已匯入 $count 個檔到 Imagine" else "匯入失敗 (格式不支援或讀取被拒)",
                    Toast.LENGTH_LONG,
                ).show()
                finish()
            }
        }
    }

    @Suppress("DEPRECATION")
    private fun collectUris(intent: Intent?): List<Uri> {
        intent ?: return emptyList()
        return when (intent.action) {
            Intent.ACTION_SEND -> {
                val uri = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    intent.getParcelableExtra(Intent.EXTRA_STREAM, Uri::class.java)
                } else {
                    intent.getParcelableExtra<Uri>(Intent.EXTRA_STREAM)
                }
                if (uri != null) listOf(uri) else emptyList()
            }
            Intent.ACTION_SEND_MULTIPLE -> {
                val list = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    intent.getParcelableArrayListExtra(Intent.EXTRA_STREAM, Uri::class.java)
                } else {
                    intent.getParcelableArrayListExtra<Uri>(Intent.EXTRA_STREAM)
                }
                list?.toList() ?: emptyList()
            }
            else -> emptyList()
        }
    }
}
