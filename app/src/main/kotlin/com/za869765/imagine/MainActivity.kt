package com.za869765.imagine

import android.app.Activity
import android.app.AlertDialog
import android.content.Context
import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.lifecycleScope
import com.za869765.imagine.data.prefs.SecurePrefs
import com.za869765.imagine.data.storage.MediaMigrator
import com.za869765.imagine.nav.ImagineRoot
import com.za869765.imagine.ui.theme.ImagineTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

// 限定機型白名單 — 純自用 + APK 被外洩時擋下其他裝置安裝執行
// SM-S908x 是 Galaxy S22 Ultra 所有區域版本 (B/U/W/N/E/0/...)
private val ALLOWED_MODEL_PREFIXES = listOf(
    "SM-S908",
)

private fun isAllowedDevice(): Boolean {
    val m = Build.MODEL ?: ""
    return ALLOWED_MODEL_PREFIXES.any { m.startsWith(it, ignoreCase = true) }
}

fun applyScreenshotFlag(activity: Activity, enabled: Boolean) {
    if (enabled) {
        activity.window.setFlags(
            WindowManager.LayoutParams.FLAG_SECURE,
            WindowManager.LayoutParams.FLAG_SECURE,
        )
    } else {
        activity.window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
    }
}

class MainActivity : FragmentActivity() {

    // v1.0.45: 第一次裝/升 v1.0.45 後跳系統權限 UI，撈舊版寫進系統相簿的 imagine_* 檔。
    // 不論 user 同意/拒絕都跑 MediaMigrator (拒絕只能撈 owner 還在的；同意能撈全部)。
    private val mediaPermLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { _ ->
        runMigratorAsync()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (!isAllowedDevice()) {
            // 不是 S22U → 顯示提示對話框後關閉 (純自用 APP，限制執行裝置避免外流安裝)
            AlertDialog.Builder(this)
                .setTitle("此 APP 僅限指定裝置")
                .setMessage(
                    "Imagine 為個人自用 APP，僅在 Galaxy S22 Ultra 上運行。\n\n" +
                        "偵測到的裝置型號：${Build.MODEL}\n" +
                        "Android 版本：${Build.VERSION.RELEASE}",
                )
                .setCancelable(false)
                .setPositiveButton("關閉") { _, _ -> finishAffinity() }
                .show()
            return
        }

        val prefs = SecurePrefs.get(this)
        applyScreenshotFlag(this, prefs.preventScreenshots)

        // 重設計強制深色 → 系統列固定淺色圖示(深底白 icon),不跟系統淺/深
        enableEdgeToEdge(
            statusBarStyle = androidx.activity.SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
            navigationBarStyle = androidx.activity.SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
        )
        setContent {
            ImagineTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background,
                ) {
                    ImagineRoot()
                }
            }
        }

        maybeRequestMediaPerm()
    }

    private fun maybeRequestMediaPerm() {
        val sp = getSharedPreferences("imagine_migration", Context.MODE_PRIVATE)
        if (sp.getBoolean("migrated_v1_45_done", false)) return

        val perms = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            arrayOf(
                android.Manifest.permission.READ_MEDIA_IMAGES,
                android.Manifest.permission.READ_MEDIA_VIDEO,
            )
        } else {
            arrayOf(android.Manifest.permission.READ_EXTERNAL_STORAGE)
        }
        mediaPermLauncher.launch(perms)
    }

    private fun runMigratorAsync() {
        lifecycleScope.launch(Dispatchers.IO) {
            MediaMigrator.runIfNeeded(this@MainActivity)
        }
    }
}
