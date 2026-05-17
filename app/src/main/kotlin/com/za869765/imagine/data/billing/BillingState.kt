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
    val balance: MutableState<String?> = mutableStateOf(null)
    val spent: MutableState<String?> = mutableStateOf(null)
    val syncedAt: MutableState<String?> = mutableStateOf(null)
    val syncing: MutableState<Boolean> = mutableStateOf(false)
    val error: MutableState<String?> = mutableStateOf(null)

    private val fmt = DateTimeFormatter.ofPattern("HH:mm:ss")

    fun sync(prefs: SecurePrefs, scope: CoroutineScope) {
        val key = prefs.managementKey ?: return
        val team = prefs.teamId ?: return
        if (key.isBlank() || team.isBlank()) return
        if (syncing.value) return
        scope.launch {
            syncing.value = true
            error.value = null
            try {
                val api = ManagementClient.build(key)
                val b = withContext(Dispatchers.IO) { api.getPrepaidBalance(team) }
                val inv = withContext(Dispatchers.IO) { api.getInvoicePreview(team) }
                balance.value = b.total?.value
                spent.value = inv.coreInvoice?.amountAfterVat ?: inv.coreInvoice?.amountBeforeVat
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

    fun clear() {
        balance.value = null
        spent.value = null
        syncedAt.value = null
        error.value = null
    }
}
