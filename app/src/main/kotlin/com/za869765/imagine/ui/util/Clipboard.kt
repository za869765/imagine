package com.za869765.imagine.ui.util

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast

object Clipboard {
    /** 複製文字到系統剪貼簿，並彈 toast 提示。 */
    fun copy(ctx: Context, text: String, label: String = "prompt", toastMsg: String = "已複製") {
        val cm = ctx.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        cm.setPrimaryClip(ClipData.newPlainText(label, text))
        Toast.makeText(ctx, toastMsg, Toast.LENGTH_SHORT).show()
    }

    /** 從剪貼簿取出純文字,空或無內容回 null。 */
    fun paste(ctx: Context): String? {
        val cm = ctx.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val clip = cm.primaryClip ?: return null
        if (clip.itemCount == 0) return null
        return clip.getItemAt(0).coerceToText(ctx).toString().takeIf { it.isNotBlank() }
    }
}
