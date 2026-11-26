package com.hymt.offline.data

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

data class HistoryItem(
    val id: Long,
    val time: Long,
    val sourceLang: String,
    val targetLang: String,
    val source: String,
    val target: String
)

/** 翻译历史，落盘为 filesDir/history.json，最多保留 50 条。 */
class HistoryRepository(context: Context) {

    private val file = File(context.filesDir, "history.json")

    fun load(): List<HistoryItem> {
        if (!file.exists()) return emptyList()
        return runCatching {
            val array = JSONArray(file.readText())
            (0 until array.length()).mapNotNull { i ->
                val o = array.optJSONObject(i) ?: return@mapNotNull null
                HistoryItem(
                    id = o.optLong("id"),
                    time = o.optLong("time"),
                    sourceLang = o.optString("sourceLang", Languages.AUTO),
                    targetLang = o.optString("targetLang", "en"),
                    source = o.optString("source"),
                    target = o.optString("target")
                )
            }
        }.getOrDefault(emptyList())
    }

    fun add(items: List<HistoryItem>, item: HistoryItem): List<HistoryItem> {
        val merged = (listOf(item) + items.filterNot { it.id == item.id }).take(MAX_ITEMS)
        return merged.also { save(it) }
    }

    fun clear(): List<HistoryItem> {
        file.delete()
        return emptyList()
    }

    private fun save(items: List<HistoryItem>) {
        val array = JSONArray()
        items.forEach { item ->
            array.put(JSONObject().apply {
                put("id", item.id)
                put("time", item.time)
                put("sourceLang", item.sourceLang)
                put("targetLang", item.targetLang)
                put("source", item.source)
                put("target", item.target)
            })
        }
        runCatching { file.writeText(array.toString()) }
    }

    companion object {
        private const val MAX_ITEMS = 50
    }
}
