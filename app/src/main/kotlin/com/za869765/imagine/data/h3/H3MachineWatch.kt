package com.za869765.imagine.data.h3

import android.content.Context
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.za869765.imagine.ImagineApp
import com.za869765.imagine.R
import com.za869765.imagine.data.notify.Notifications
import com.za869765.imagine.data.prefs.SecurePrefs
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * v2.1.2 製圖機盯場:機器開著時每 20 秒向 H3 Studio 問一次狀態(不花錢),在通知列顯示
 * 「做圖中」或「閒置中，約 N 分後關機｜現在下單免開機費」倒數;剛進入閒置時響一次提醒,關機時收掉。
 * 倒數由工人決定:待命中接到新單會暫停,做完重新從 8 分倒數。APP 程序活著時有效。
 */
object H3MachineWatch {
    private const val NOTIF_ID = 0x48334D57 // "H3MW"
    private var job: Job? = null

    @Synchronized
    fun start(ctx: Context) {
        if (job?.isActive == true) return
        val app = ctx.applicationContext
        val prefs = SecurePrefs.get(app)
        if (!prefs.isH3TokenSet) return
        job = ImagineApp.appScope.launch {
            var seenAlive = false
            var wasIdle = false
            var misses = 0
            val started = System.currentTimeMillis()
            while (isActive) {
                val st = (H3Client.imageEstimate(prefs, 1, 1, "gputw_image") as? H3Client.Result.Ok)?.value
                if (st == null) {
                    if (++misses > 15) break // 連續 5 分鐘問不到就放棄
                } else {
                    misses = 0
                    if (st.gputwImageAlive) {
                        seenAlive = true
                        val left = st.gputwImageIdleLeft
                        if (left != null) {
                            post(app, "製圖機閒置中，約 ${left.toInt().coerceAtLeast(1)} 分後關機", "現在下單不用再付開機費；有新單會暫停倒數，做完重新倒數", alert = !wasIdle)
                            wasIdle = true
                        } else {
                            post(app, "製圖機做圖中", "做完會待命 8 分鐘，期間再下單免開機費", alert = false)
                            wasIdle = false
                        }
                    } else if (seenAlive) {
                        cancel(app)
                        post(app, "製圖機已關機", "待命時間到，自動關機；下次下單會重新開機（約 NT$4～5）", alert = false, ongoing = false)
                        break
                    } else if (System.currentTimeMillis() - started > 25 * 60_000) {
                        break // 25 分鐘都沒開起來:不再盯(開機失敗會在任務頁顯示)
                    }
                }
                delay(20_000)
            }
        }
    }

    private fun post(ctx: Context, title: String, text: String, alert: Boolean, ongoing: Boolean = true) {
        Notifications.ensureChannels(ctx)
        if (alert) cancel(ctx) // 重新發才會響一次
        val n = NotificationCompat.Builder(ctx, Notifications.CHANNEL_COMPLETE)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setOngoing(ongoing)
            .setAutoCancel(!ongoing)
            .setOnlyAlertOnce(true)
            .setSilent(!alert)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .build()
        runCatching { NotificationManagerCompat.from(ctx).notify(NOTIF_ID, n) }
    }

    private fun cancel(ctx: Context) {
        runCatching { NotificationManagerCompat.from(ctx).cancel(NOTIF_ID) }
    }
}
