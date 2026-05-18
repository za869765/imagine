package com.za869765.imagine.data.backup

import com.za869765.imagine.data.prefs.SecurePrefs
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

@Serializable
data class KeyBackup(
    val apiKey: String? = null,
    val apiKeyVerifiedAt: String? = null,
    val managementKey: String? = null,
    val version: Int = 1,
)

object KeyBackupCodec {
    private val json = Json {
        prettyPrint = true
        ignoreUnknownKeys = true
        explicitNulls = false
    }

    fun export(prefs: SecurePrefs): String = json.encodeToString(
        KeyBackup(
            apiKey = prefs.apiKey,
            apiKeyVerifiedAt = prefs.apiKeyVerifiedAt,
            managementKey = prefs.managementKey,
        ),
    )

    fun importInto(prefs: SecurePrefs, jsonStr: String): KeyBackup {
        val b = json.decodeFromString<KeyBackup>(jsonStr.trim())
        b.apiKey?.takeIf { it.isNotBlank() }?.let { prefs.apiKey = it }
        b.apiKeyVerifiedAt?.takeIf { it.isNotBlank() }?.let { prefs.apiKeyVerifiedAt = it }
        b.managementKey?.takeIf { it.isNotBlank() }?.let { prefs.managementKey = it }
        return b
    }
}
