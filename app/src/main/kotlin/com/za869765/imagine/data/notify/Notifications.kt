package com.za869765.imagine.data.notify

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.za869765.imagine.MainActivity
import com.za869765.imagine.R

/**
 * 影片背景生成用的通知 channel + builder。
 *
 * 兩個 channel：
 * - PROGRESS：低優先（IMPORTANCE_LOW）— Worker 前景服務常駐,不發聲不彈出,只在通知列顯示「生成中…」
 * - COMPLETE：預設優先（IMPORTANCE_DEFAULT）— 完成 / 失敗 一次性提示,有聲音
 */
object Notifications {
    const val CHANNEL_PROGRESS = "imagine_video_progress"
    const val CHANNEL_COMPLETE = "imagine_video_complete"

    // notification id 命名空間 — 進度通知用 requestId.hashCode(),完成通知用負 hash 避免衝突
    fun progressId(requestId: String): Int = requestId.hashCode() and 0x7fffffff
    fun completeId(requestId: String): Int = -(requestId.hashCode() and 0x7fffffff) - 1

    fun ensureChannels(ctx: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val nm = ctx.getSystemService(NotificationManager::class.java) ?: return
        if (nm.getNotificationChannel(CHANNEL_PROGRESS) == null) {
            nm.createNotificationChannel(
                NotificationChannel(
                    CHANNEL_PROGRESS,
                    "影片生成進度",
                    NotificationManager.IMPORTANCE_LOW,
                ).apply {
                    description = "影片背景生成時顯示進度的常駐通知"
                    setShowBadge(false)
                },
            )
        }
        if (nm.getNotificationChannel(CHANNEL_COMPLETE) == null) {
            nm.createNotificationChannel(
                NotificationChannel(
                    CHANNEL_COMPLETE,
                    "影片完成通知",
                    NotificationManager.IMPORTANCE_DEFAULT,
                ).apply {
                    description = "影片生成完成或失敗時通知"
                },
            )
        }
    }

    private fun openAppIntent(ctx: Context): PendingIntent {
        val intent = Intent(ctx, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        return PendingIntent.getActivity(
            ctx, 0, intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
    }

    fun buildProgress(ctx: Context, elapsedSec: Int): android.app.Notification {
        val mins = elapsedSec / 60
        val secs = elapsedSec % 60
        val timeText = "%d:%02d".format(mins, secs)
        return NotificationCompat.Builder(ctx, CHANNEL_PROGRESS)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle("Imagine 影片生成中")
            .setContentText("已等待 $timeText  · 點此回 APP")
            .setOngoing(true)
            .setProgress(0, 0, true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setContentIntent(openAppIntent(ctx))
            .setOnlyAlertOnce(true)
            .build()
    }

    fun postComplete(ctx: Context, requestId: String, success: Boolean, message: String) {
        ensureChannels(ctx)
        val n = NotificationCompat.Builder(ctx, CHANNEL_COMPLETE)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle(if (success) "Imagine 影片完成" else "Imagine 影片失敗")
            .setContentText(message)
            .setStyle(NotificationCompat.BigTextStyle().bigText(message))
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setContentIntent(openAppIntent(ctx))
            .build()
        // Android 13+ POST_NOTIFICATIONS 沒給授權時 notify() 會被靜默 drop,不會 crash
        runCatching { NotificationManagerCompat.from(ctx).notify(completeId(requestId), n) }
    }

    fun cancelProgress(ctx: Context, requestId: String) {
        runCatching { NotificationManagerCompat.from(ctx).cancel(progressId(requestId)) }
    }
}
