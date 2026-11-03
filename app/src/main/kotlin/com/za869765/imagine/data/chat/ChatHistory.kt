package com.za869765.imagine.data.chat

import android.content.Context
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.File

/**
 * v3.0.0 對話紀錄存手機(App 私有資料夾 chat_history.json,不上傳任何地方)。
 * 之前對話只放記憶體,更新 App 或被系統關掉就消失。最多留 100 段對話。
 */
@Serializable
data class SavedTurn(val role: String, val content: String, val meta: String = "")

@Serializable
data class SavedChat(val id: String, val title: String, val updatedAt: Long, val model: String = "", val turns: List<SavedTurn> = emptyList())

@Serializable
private data class ChatFile(val currentId: String? = null, val chats: List<SavedChat> = emptyList())

object ChatHistory {
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
    private const val MAX = 100
    private fun file(ctx: Context) = File(ctx.filesDir, "chat_history.json")

    @Synchronized
    private fun read(ctx: Context): ChatFile =
        runCatching { json.decodeFromString(ChatFile.serializer(), file(ctx).readText()) }.getOrDefault(ChatFile())

    @Synchronized
    private fun write(ctx: Context, f: ChatFile) {
        val tmp = File(ctx.filesDir, "chat_history.json.tmp")
        tmp.writeText(json.encodeToString(ChatFile.serializer(), f))
        tmp.renameTo(file(ctx))  // 先寫暫存再換名,寫到一半當機不會弄壞舊紀錄
    }

    /** 目前那段(重開 App 接著顯示);沒有就 null */
    fun current(ctx: Context): SavedChat? = read(ctx).let { f -> f.chats.firstOrNull { it.id == f.currentId } }

    fun all(ctx: Context): List<SavedChat> = read(ctx).chats.sortedByDescending { it.updatedAt }

    /** 存目前這段(沒有 id 就新建一段並設為目前) */
    fun save(ctx: Context, id: String?, model: String, turns: List<SavedTurn>): String {
        val f = read(ctx)
        val cid = id ?: "c" + System.currentTimeMillis()
        if (turns.isEmpty()) return cid
        val title = turns.firstOrNull { it.role == "user" }?.content?.replace('\n', ' ')?.take(30) ?: "對話"
        val chat = SavedChat(cid, title, System.currentTimeMillis(), model, turns)
        val chats = (listOf(chat) + f.chats.filter { it.id != cid }).sortedByDescending { it.updatedAt }.take(MAX)
        write(ctx, ChatFile(cid, chats))
        return cid
    }

    /** 開新對話:舊的留在歷史裡 */
    fun startNew(ctx: Context) { write(ctx, read(ctx).copy(currentId = null)) }

    fun open(ctx: Context, id: String): SavedChat? {
        val f = read(ctx)
        val c = f.chats.firstOrNull { it.id == id } ?: return null
        write(ctx, f.copy(currentId = id))
        return c
    }

    fun delete(ctx: Context, id: String) {
        val f = read(ctx)
        write(ctx, ChatFile(if (f.currentId == id) null else f.currentId, f.chats.filter { it.id != id }))
    }
}
