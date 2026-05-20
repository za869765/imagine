package com.za869765.imagine.data.billing

import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import com.za869765.imagine.data.api.ManagementClient
import com.za869765.imagine.data.prefs.SecurePrefs
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalTime
import java.time.format.DateTimeFormatter

// Singleton mirror of the xAI billing snapshot — kept in memory only.
// Subscribed by XaiBalanceBar (top of every main screen) and the SettingsScreen.
object BillingState {
    // 預設 team uuid (v1.0.10 hardcode)。使用者可在 Settings 覆寫 SecurePrefs.teamId。
    // xAI Management API 沒提供 list teams endpoint，team uuid 要從 console.x.ai 取得。
    private const val DEFAULT_TEAM_ID = "02192454-54ee-4835-9680-212eda8ba708"

    val balance: MutableState<String?> = mutableStateOf(null)
    val spent: MutableState<String?> = mutableStateOf(null)
    // Raw 字串 — Settings 旁邊顯示讓使用者比對 xAI 後台真實數字決定正確單位
    val balanceRaw: MutableState<String?> = mutableStateOf(null)
    val spentRaw: MutableState<String?> = mutableStateOf(null)
    val syncedAt: MutableState<String?> = mutableStateOf(null)
    val syncing: MutableState<Boolean> = mutableStateOf(false)
    val error: MutableState<String?> = mutableStateOf(null)

    private val fmt = DateTimeFormatter.ofPattern("HH:mm:ss")

    fun sync(prefs: SecurePrefs, scope: CoroutineScope) {
        val key = prefs.managementKey ?: return
        if (key.isBlank()) return
        if (syncing.value) return
        val teamId = prefs.teamId?.takeIf { it.isNotBlank() } ?: DEFAULT_TEAM_ID
        scope.launch {
            syncing.value = true
            error.value = null
            try {
                val api = ManagementClient.build(key)
                val b = withContext(Dispatchers.IO) { api.getPrepaidBalance(teamId) }
                val inv = withContext(Dispatchers.IO) { api.getInvoicePreview(teamId) }
                val bRaw = b.total?.value
                val sRaw = inv.coreInvoice?.amountAfterVat ?: inv.coreInvoice?.amountBeforeVat
                balanceRaw.value = bRaw
                spentRaw.value = sRaw
                balance.value = fmtMoney(bRaw)
                spent.value = fmtMoney(sRaw)
                syncedAt.value = LocalTime.now().format(fmt)
            } catch (e: retrofit2.HttpException) {
                val body = runCatching { e.response()?.errorBody()?.string() }.getOrNull()
                error.value = "HTTP ${e.code()}" + (body?.let { " — $it" } ?: "")
            } catch (e: Throwable) {
                error.value = "${e::class.simpleName}: ${e.message}"
            } finally {
                syncing.value = false
            }
        }
    }

    // xAI Management API 金額單位未官方文件化，從 raw 字串特徵 heuristic 猜:
    //   * 含 "."          → 已是 dollars decimal 字串 (如 "20.89")
    //   * 整數絕對值 < 10^8 → 當 cents 解 (典型帳戶餘額 $0.01~$1M 對應 1~10^8 cents)
    //   * 整數絕對值 ≥ 10^8 → 當 micro-USD 解 (有些 xAI billing API 用 10^-6 USD)
    // Settings 內仍會顯示 raw 字串讓使用者比對 console.x.ai 後台真實 USD。
    // 若猜錯回報實際單位後改寫死分支。
    private fun fmtMoney(raw: String?): String? {
        if (raw.isNullOrBlank()) return null
        val trimmed = raw.trim()
        // 含 "." 視為 dollars decimal 字串
        if (trimmed.contains('.')) {
            val d = trimmed.toDoubleOrNull() ?: return raw
            return formatUsd(d)
        }
        val n = trimmed.toLongOrNull() ?: return raw
        val abs = if (n < 0) -n else n
        val dollars = if (abs < 100_000_000L) n / 100.0 else n / 1_000_000.0
        return formatUsd(dollars)
    }

    private fun formatUsd(n: Double): String =
        if (n < 0) "-$" + "%.2f".format(-n) else "$" + "%.2f".format(n)

    fun clear() {
        balance.value = null
        spent.value = null
        balanceRaw.value = null
        spentRaw.value = null
        syncedAt.value = null
        error.value = null
    }
}
