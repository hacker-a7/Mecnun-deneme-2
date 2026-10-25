package com.mecnun.assistant

import android.content.Context
import android.content.SharedPreferences
import org.json.JSONObject

/** Tüm ayarlar burada. Servis ve arayüz aynı yerden okur. */
object Prefs {
    private lateinit var sp: SharedPreferences

    fun init(ctx: Context) {
        if (!::sp.isInitialized) {
            sp = ctx.applicationContext.getSharedPreferences("mecnun", Context.MODE_PRIVATE)
        }
    }

    private fun s(k: String, d: String) = sp.getString(k, d) ?: d
    private fun put(k: String, v: String) = sp.edit().putString(k, v).apply()
    private fun i(k: String, d: Int) = sp.getInt(k, d)
    private fun put(k: String, v: Int) = sp.edit().putInt(k, v).apply()
    private fun b(k: String, d: Boolean) = sp.getBoolean(k, d)
    private fun put(k: String, v: Boolean) = sp.edit().putBoolean(k, v).apply()
    private fun f(k: String, d: Float) = sp.getFloat(k, d)
    private fun put(k: String, v: Float) = sp.edit().putFloat(k, v).apply()

    /** Hitap: "Efendim", "Patron", ... */
    var hitap: String
        get() = s("hitap", "Efendim")
        set(v) = put("hitap", v.trim())

    /** Uyandırma kelimesi */
    var wakeWord: String
        get() = s("wake", "mecnun")
        set(v) = put("wake", v.trim().ifEmpty { "mecnun" })

    /** 0 = sıkı, 1 = normal, 2 = esnek */
    var wakeTolerance: Int
        get() = i("wakeTol", 1)
        set(v) = put("wakeTol", v)

    /** 0 = kapalı, 1 = sadece sorular ve onaylar, 2 = her şey */
    var voiceMode: Int
        get() = i("voiceMode", 2)
        set(v) = put("voiceMode", v)

    var speechRate: Float
        get() = f("rate", 1.0f)
        set(v) = put("rate", v)

    var pitch: Float
        get() = f("pitch", 1.0f)
        set(v) = put("pitch", v)

    var quietEnabled: Boolean
        get() = b("quietOn", true)
        set(v) = put("quietOn", v)

    /** Günün dakikası (0..1439) */
    var quietStart: Int
        get() = i("quietStart", 60)
        set(v) = put("quietStart", v)

    var quietEnd: Int
        get() = i("quietEnd", 390)
        set(v) = put("quietEnd", v)

    var confirmCritical: Boolean
        get() = b("confirm", true)
        set(v) = put("confirm", v)

    var recFolder: String
        get() = s("recFolder", "Mecnun")
        set(v) = put("recFolder", v.trim().ifEmpty { "Mecnun" })

    /** 0 = 16 kHz, 1 = 48 kHz */
    var recQuality: Int
        get() = i("recQuality", 0)
        set(v) = put("recQuality", v)

    var batteryWarn: Boolean
        get() = b("battWarn", true)
        set(v) = put("battWarn", v)

    var batteryLevel: Int
        get() = i("battLevel", 15)
        set(v) = put("battLevel", v)

    /** 0 = otomatik, 1 = Samsung Notes, 2 = Google Keep, 3 = sadece uygulama içi */
    var noteApp: Int
        get() = i("noteApp", 0)
        set(v) = put("noteApp", v)

    var logIgnored: Boolean
        get() = b("logIgnored", false)
        set(v) = put("logIgnored", v)

    // ---- Uygulama takma adları: "vatsap" -> com.whatsapp ----
    fun aliases(): Map<String, String> {
        val o = JSONObject(s("aliases", "{}"))
        val m = LinkedHashMap<String, String>()
        o.keys().forEach { m[it] = o.getString(it) }
        return m
    }

    fun addAlias(alias: String, pkg: String) {
        val a = Tr.norm(alias)
        if (a.isEmpty()) return
        val o = JSONObject(s("aliases", "{}"))
        o.put(a, pkg)
        put("aliases", o.toString())
    }

    fun removeAlias(alias: String) {
        val o = JSONObject(s("aliases", "{}"))
        o.remove(alias)
        put("aliases", o.toString())
    }

    // ---- Özelleştirilmiş cevap cümleleri ----
    fun responseOverride(key: String): String? {
        val o = JSONObject(s("responses", "{}"))
        return if (o.has(key)) o.getString(key) else null
    }

    fun setResponseOverride(key: String, text: String?) {
        val o = JSONObject(s("responses", "{}"))
        if (text == null) o.remove(key) else o.put(key, text)
        put("responses", o.toString())
    }
}
