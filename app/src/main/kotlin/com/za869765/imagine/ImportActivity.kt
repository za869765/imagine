package com.za869765.imagine

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import com.za869765.imagine.data.storage.MediaImporter
import com.za869765.imagine.data.storage.PromptIndex
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
// v1.0.54 B7: 跟 MainActivity 同樣 device guard，避免外洩裝置直接 import
private val ALLOWED_MODEL_PREFIXES = listOf("SM-S908")
private fun isAllowedImportDevice(): Boolean {
    val m = Build.MODEL ?: ""
    return ALLOWED_MODEL_PREFIXES.any { m.startsWith(it, ignoreCase = true) }
}

class ImportActivity : Activity() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // v1.0.54 B7: 非白名單裝置拒絕匯入 — 跟 MainActivity 一致防外洩裝置寫檔
        if (!isAllowedImportDevice()) {
            Toast.makeText(this, "Imagine 僅限指定裝置使用", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        val uris = collectUris(intent)
        if (uris.isEmpty()) {
            Toast.makeText(this, "沒收到可匯入的檔案", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        // v1.0.48: 抓分享進來的 prompt 文字 (Grok app 分享圖時可能會帶在 EXTRA_TEXT)
        val prompt = extractPrompt(intent)

        scope.launch {
            val saved = MediaImporter.importAll(this@ImportActivity, uris)
            // v1.0.48: 有 prompt 就寫進 PromptIndex，History 點進去看得到原 prompt
            if (!prompt.isNullOrBlank()) {
                for (name in saved) {
                    PromptIndex.put(this@ImportActivity, name, prompt)
                }
            }
            withContext(Dispatchers.Main) {
                val count = saved.size
                Toast.makeText(
                    this@ImportActivity,
                    if (count > 0) {
                        if (!prompt.isNullOrBlank()) "已匯入 $count 個檔到 Imagine (含 prompt)"
                        else "已匯入 $count 個檔到 Imagine"
                    } else "匯入失敗 (格式不支援或讀取被拒)",
                    Toast.LENGTH_LONG,
                ).show()
                finish()
            }
        }
    }

    /**
     * v1.0.48: 從 share intent 取 prompt 文字。
     * 過濾掉純 URL (避免把 grok 分享連結當 prompt 存起來)。
     * 若 EXTRA_TEXT 是「prompt 文字 \n https://...」混排，取第一個非 URL 段。
     */
    private fun extractPrompt(intent: Intent?): String? {
        val text = intent?.getStringExtra(Intent.EXTRA_TEXT)?.trim() ?: return null
        if (text.isBlank()) return null
        if (text.startsWith("http://", ignoreCase = true) ||
            text.startsWith("https://", ignoreCase = true)
        ) {
            // 純 URL — 看有沒有非 URL 行
            val nonUrlLine = text.lineSequence()
                .map { it.trim() }
                .firstOrNull {
                    it.isNotBlank() &&
                        !it.startsWith("http://", ignoreCase = true) &&
                        !it.startsWith("https://", ignoreCase = true)
                }
            return nonUrlLine?.takeIf { it.isNotBlank() }
        }
        return text
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
