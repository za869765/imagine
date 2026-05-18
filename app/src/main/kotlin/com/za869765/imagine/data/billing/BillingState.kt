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
    // Hard-coded because this app is single-user. To switch teams change here + rebuild.
    private const val TEAM_ID = "02192454-54ee-4835-9680-212eda8ba708"

    val balance: MutableState<String?> = mutableStateOf(null)
    val spent: MutableState<String?> = mutableStateOf(null)
    val syncedAt: MutableState<String?> = mutableStateOf(null)
    val syncing: MutableState<Boolean> = mutableStateOf(false)
    val error: MutableState<String?> = mutableStateOf(null)

    private val fmt = DateTimeFormatter.ofPattern("HH:mm:ss")

    fun sync(prefs: SecurePrefs, scope: CoroutineScope) {
        val key = prefs.managementKey ?: return
        if (key.isBlank()) return
        if (syncing.value) return
        scope.launch {
            syncing.value = true
            error.value = null
            try {
                val api = ManagementClient.build(key)
                val b = withContext(Dispatchers.IO) { api.getPrepaidBalance(TEAM_ID) }
                val inv = withContext(Dispatchers.IO) { api.getInvoicePreview(TEAM_ID) }
                balance.value = fmtMoney(b.total?.value)
                spent.value = fmtMoney(
                    inv.coreInvoice?.amountAfterVat ?: inv.coreInvoice?.amountBeforeVat,
                )
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

    // xAI Management API 金額用 cents 為單位(整數字串)。
    // 例:"2089" → "$20.89";"-2500" → "-$25.00"。
    // 不能 parse 成 Long 的字串保留原樣顯示,方便除錯。
    private fun fmtMoney(raw: String?): String? {
        if (raw.isNullOrBlank()) return null
        val cents = raw.toLongOrNull() ?: return raw
        val dollars = cents / 100.0
        return if (dollars < 0)
            "-$" + "%.2f".format(-dollars)
        else
            "$" + "%.2f".format(dollars)
    }

    fun clear() {
        balance.value = null
        spent.value = null
        syncedAt.value = null
        error.value = null
    }
}
