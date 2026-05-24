package com.za869765.imagine.data.storage

import android.content.Context
import android.os.Build
import android.util.Log
import java.io.File
import java.io.PrintWriter
import java.io.StringWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * v1.0.50: 把 catch 到的 exception/error 寫到 filesDir/crash.log，
 * 之後 Settings 加按鈕 share 給開發者看。也註冊 Thread.UncaughtExceptionHandler
 * 接「沒人 catch 的」main thread crash (但 native SIGABRT 接不到)。
 *
 * 檔案 append 模式，保留多筆。讀取時可給最後 ~200 行。
 */
object CrashLogger {

    private const val TAG = "CrashLogger"
    private const val FILE = "crash.log"
    private const val MAX_BYTES = 256 * 1024  // 256 KB，超過從頭裁

    private fun file(ctx: Context): File = File(ctx.filesDir, FILE)

    fun install(ctx: Context) {
        val app = ctx.applicationContext
        val prev = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            try {
                record(app, "UNCAUGHT [${thread.name}]", throwable)
            } catch (_: Throwable) { /* 不能再失敗 */ }
            // 仍 propagate 給原 handler，讓系統正常處理 crash
            prev?.uncaughtException(thread, throwable)
        }
    }

    fun record(ctx: Context, tag: String, t: Throwable) {
        val sw = StringWriter()
        t.printStackTrace(PrintWriter(sw))
        val entry = buildString {
            append("=== ")
            append(timestamp())
            append(" | ")
            append(tag)
            append(" | ")
            append(Build.MODEL)
            append(" / Android ")
            append(Build.VERSION.RELEASE)
            append('\n')
            append(sw.toString())
            append('\n')
        }
        try {
            val f = file(ctx)
            // 超過 size 上限就 truncate 從頭重來 (簡單作法，不做 rotate)
            if (f.exists() && f.length() > MAX_BYTES) f.delete()
            f.appendText(entry)
        } catch (e: Throwable) {
            Log.e(TAG, "record failed", e)
        }
        Log.e(TAG, "$tag: ${t.message}", t)
    }

    fun readAll(ctx: Context): String {
        val f = file(ctx)
        return if (f.exists()) f.readText() else ""
    }

    fun clear(ctx: Context) {
        runCatching { file(ctx).delete() }
    }

    private fun timestamp(): String =
        SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date())
}
