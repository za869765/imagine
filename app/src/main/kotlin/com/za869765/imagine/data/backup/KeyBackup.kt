package com.za869765.imagine.data.backup

import com.za869765.imagine.data.prefs.SecurePrefs
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

// v1.0.13 之後預設 CSV 匯出/匯入；舊版 JSON 匯入仍兼容 (auto-detect)。
// CSV 格式 (Excel 直接開):
//   key,value
//   api_key,xai-...
//   api_key_verified_at,2026-05-20T...
//   openrouter_key,sk-or-v1-...          (v1.8.0)
//   openrouter_key_verified_at,2026-08-22 (v1.8.0)

@Serializable
data class KeyBackup(
    val apiKey: String? = null,
    val apiKeyVerifiedAt: String? = null,
    val managementKey: String? = null,
    val teamId: String? = null,
    val githubPat: String? = null,
    val openRouterKey: String? = null,
    val openRouterKeyVerifiedAt: String? = null,
    val provider: String? = null,
    val version: Int = 4,
)

object KeyBackupCodec {
    private val json = Json {
        prettyPrint = true
        ignoreUnknownKeys = true
        explicitNulls = false
    }

    fun export(prefs: SecurePrefs): String {
        val rows = buildList {
            prefs.apiKey?.takeIf { it.isNotBlank() }?.let { add("api_key" to it) }
            prefs.apiKeyVerifiedAt?.takeIf { it.isNotBlank() }?.let { add("api_key_verified_at" to it) }
            prefs.openRouterKey?.takeIf { it.isNotBlank() }?.let { add("openrouter_key" to it) }
            prefs.openRouterKeyVerifiedAt?.takeIf { it.isNotBlank() }?.let { add("openrouter_key_verified_at" to it) }
        }
        val body = rows.joinToString("\n") { (k, v) -> "${csvCell(k)},${csvCell(v)}" }
        return "key,value\n$body\n"
    }

    fun importInto(prefs: SecurePrefs, content: String): KeyBackup {
        val trimmed = content.trim()
        // auto-detect 舊 JSON 匯出 (v1.0.12 之前)
        if (trimmed.startsWith("{")) return importJsonLegacy(prefs, trimmed)
        return importCsv(prefs, trimmed)
    }

    private fun importJsonLegacy(prefs: SecurePrefs, jsonStr: String): KeyBackup {
        val b = json.decodeFromString<KeyBackup>(jsonStr)
        b.apiKey?.takeIf { it.isNotBlank() }?.let { prefs.apiKey = it }
        b.apiKeyVerifiedAt?.takeIf { it.isNotBlank() }?.let { prefs.apiKeyVerifiedAt = it }
        b.openRouterKey?.takeIf { it.isNotBlank() }?.let { prefs.openRouterKey = it }
        b.openRouterKeyVerifiedAt?.takeIf { it.isNotBlank() }?.let { prefs.openRouterKeyVerifiedAt = it }
        // provider 欄位(v1.8.0~1.8.2 短暫存在)已無意義,忽略
        // v1.0.21 砍 BillingState、v1.0.29 砍 GitHub PAT — 舊 backup 內這些欄位
        // 仍 parse 進 data class，但這裡 silently ignore 不寫進 SecurePrefs
        return b
    }

    private fun importCsv(prefs: SecurePrefs, csv: String): KeyBackup {
        var apiKey: String? = null
        var verifiedAt: String? = null
        var orKey: String? = null
        var orVerifiedAt: String? = null
        var provider: String? = null
        csv.lineSequence()
            .map { it.trim().removePrefix("﻿") }  // 砍 UTF-8 BOM (Excel 存 CSV 常有)
            .filter { it.isNotBlank() }
            .forEachIndexed { idx, raw ->
                val (k, v) = parseRow(raw) ?: return@forEachIndexed
                if (idx == 0 && k.equals("key", ignoreCase = true)) return@forEachIndexed  // header
                if (v.isEmpty()) return@forEachIndexed
                when (k.lowercase()) {
                    "api_key", "apikey" -> apiKey = v
                    "api_key_verified_at", "apikeyverifiedat" -> verifiedAt = v
                    "openrouter_key", "openrouterkey" -> orKey = v
                    "openrouter_key_verified_at", "openrouterkeyverifiedat" -> orVerifiedAt = v
                    "api_provider", "provider" -> provider = v
                    "management_key", "managementkey", "team_id", "teamid",
                    "github_pat", "githubpat" -> {
                        /* 舊 backup 兼容欄位 — silently ignore */
                    }
                }
            }
        apiKey?.let { prefs.apiKey = it }
        verifiedAt?.let { prefs.apiKeyVerifiedAt = it }
        orKey?.let { prefs.openRouterKey = it }
        orVerifiedAt?.let { prefs.openRouterKeyVerifiedAt = it }
        return KeyBackup(
            apiKey = apiKey,
            apiKeyVerifiedAt = verifiedAt,
            openRouterKey = orKey,
            openRouterKeyVerifiedAt = orVerifiedAt,
            provider = provider,
        )
    }

    // 解一行 CSV (支援 "..."  quoted field 跟雙引號 escape `""`)
    private fun parseRow(line: String): Pair<String, String>? {
        val cells = splitCsvLine(line)
        if (cells.isEmpty()) return null
        val key = cells[0].trim()
        val value = cells.getOrNull(1)?.trim().orEmpty()
        if (key.isEmpty()) return null
        return key to value
    }

    private fun splitCsvLine(line: String): List<String> {
        val out = mutableListOf<String>()
        val cur = StringBuilder()
        var inQuote = false
        var i = 0
        while (i < line.length) {
            val ch = line[i]
            if (inQuote) {
                if (ch == '"') {
                    if (i + 1 < line.length && line[i + 1] == '"') { cur.append('"'); i++ }
                    else inQuote = false
                } else cur.append(ch)
            } else {
                when (ch) {
                    ',' -> { out += cur.toString(); cur.clear() }
                    '"' -> if (cur.isEmpty()) inQuote = true else cur.append(ch)
                    else -> cur.append(ch)
                }
            }
            i++
        }
        out += cur.toString()
        return out
    }

    // 值內含 , " 或換行就 quote；其他原樣
    private fun csvCell(v: String): String {
        if (v.contains(',') || v.contains('"') || v.contains('\n')) {
            return "\"" + v.replace("\"", "\"\"") + "\""
        }
        return v
    }
}
