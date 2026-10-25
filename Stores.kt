package com.mecnun.assistant

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

data class HistoryItem(
    val time: Long,
    val heard: String,
    val understood: String,
    val result: String,
    val ok: Boolean
)

data class NoteItem(val time: Long, val text: String)

/** Komut geçmişi: ne duydu, ne anladı, ne yaptı. Sadece metin tutulur, ses kaydı tutulmaz. */
object History {
    private lateinit var file: File
    private val _items = MutableStateFlow<List<HistoryItem>>(emptyList())
    val items: StateFlow<List<HistoryItem>> = _items

    fun init(ctx: Context) {
        if (::file.isInitialized) return
        file = File(ctx.filesDir, "history.json")
        _items.value = load()
    }

    private fun load(): List<HistoryItem> = try {
        val arr = JSONArray(file.readText())
        (0 until arr.length()).map {
            val o = arr.getJSONObject(it)
            HistoryItem(o.getLong("t"), o.getString("h"), o.getString("u"), o.getString("r"), o.getBoolean("ok"))
        }
    } catch (e: Exception) {
        emptyList()
    }

    @Synchronized
    fun add(item: HistoryItem) {
        val list = (listOf(item) + _items.value).take(300)
        _items.value = list
        save(list)
    }

    @Synchronized
    fun clear() {
        _items.value = emptyList()
        save(emptyList())
    }

    private fun save(list: List<HistoryItem>) {
        try {
            val arr = JSONArray()
            list.forEach {
                arr.put(JSONObject().put("t", it.time).put("h", it.heard).put("u", it.understood)
                    .put("r", it.result).put("ok", it.ok))
            }
            file.writeText(arr.toString())
        } catch (_: Exception) {
        }
    }

    fun exportText(): String {
        val fmt = java.text.SimpleDateFormat("dd.MM.yyyy HH:mm:ss", java.util.Locale("tr", "TR"))
        return _items.value.joinToString("\n\n") {
            "${fmt.format(java.util.Date(it.time))}\nDuyulan: ${it.heard}\nAnlaşılan: ${it.understood}\nSonuç: ${it.result}${if (it.ok) "" else "  (başarısız)"}"
        }
    }
}

/** Notların uygulama içi kopyası (telefondaki not uygulamasına ek olarak). */
object Notes {
    private lateinit var file: File
    private val _items = MutableStateFlow<List<NoteItem>>(emptyList())
    val items: StateFlow<List<NoteItem>> = _items

    fun init(ctx: Context) {
        if (::file.isInitialized) return
        file = File(ctx.filesDir, "notes.json")
        _items.value = try {
            val arr = JSONArray(file.readText())
            (0 until arr.length()).map { NoteItem(arr.getJSONObject(it).getLong("t"), arr.getJSONObject(it).getString("x")) }
        } catch (e: Exception) {
            emptyList()
        }
    }

    @Synchronized
    fun add(text: String) {
        val list = listOf(NoteItem(System.currentTimeMillis(), text)) + _items.value
        _items.value = list
        save(list)
    }

    @Synchronized
    fun remove(item: NoteItem) {
        val list = _items.value.filter { it.time != item.time }
        _items.value = list
        save(list)
    }

    private fun save(list: List<NoteItem>) {
        try {
            val arr = JSONArray()
            list.forEach { arr.put(JSONObject().put("t", it.time).put("x", it.text)) }
            file.writeText(arr.toString())
        } catch (_: Exception) {
        }
    }
}
