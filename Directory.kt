package com.mecnun.assistant

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.provider.ContactsContract
import androidx.core.content.ContextCompat

class Key(val k: String, val penalty: Float)

class AppEntry(val label: String, val pkg: String, val keys: List<Key>)

class ContactEntry(val name: String, val number: String, val keys: List<Key>)

/** Telefondaki uygulamaların ve rehberin listesi (önbellekli). */
object Directory {
    @Volatile private var apps: List<AppEntry> = emptyList()
    @Volatile private var appsAt = 0L
    @Volatile private var contacts: List<ContactEntry> = emptyList()
    @Volatile private var contactsAt = 0L

    fun invalidateApps() {
        appsAt = 0L
    }

    @Synchronized
    fun apps(ctx: Context, force: Boolean = false): List<AppEntry> {
        val now = System.currentTimeMillis()
        if (!force && apps.isNotEmpty() && now - appsAt < 10 * 60_000) return apps
        val pm = ctx.packageManager
        val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        val list = try {
            pm.queryIntentActivities(intent, 0)
        } catch (e: Exception) {
            emptyList()
        }
        val seen = HashSet<String>()
        val out = ArrayList<AppEntry>()
        for (ri in list) {
            val pkg = ri.activityInfo.packageName
            if (!seen.add(pkg)) continue
            val label = ri.loadLabel(pm).toString()
            val keys = ArrayList<Key>()
            keys.add(Key(Tr.phon(label), 0f))
            for (w in label.split(" ", "-", ".")) {
                val p = Tr.phon(w)
                if (p.length >= 4 && p != keys[0].k) keys.add(Key(p, 0.15f))
            }
            out.add(AppEntry(label, pkg, keys))
        }
        out.sortBy { Tr.norm(it.label) }
        apps = out
        appsAt = now
        return out
    }

    @Synchronized
    fun contacts(ctx: Context): List<ContactEntry> {
        val now = System.currentTimeMillis()
        if (contacts.isNotEmpty() && now - contactsAt < 5 * 60_000) return contacts
        if (ContextCompat.checkSelfPermission(ctx, Manifest.permission.READ_CONTACTS) != PackageManager.PERMISSION_GRANTED) {
            return emptyList()
        }
        val out = ArrayList<ContactEntry>()
        try {
            val cols = arrayOf(
                ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME,
                ContactsContract.CommonDataKinds.Phone.NUMBER,
                ContactsContract.CommonDataKinds.Phone.TYPE
            )
            ctx.contentResolver.query(
                ContactsContract.CommonDataKinds.Phone.CONTENT_URI, cols, null, null, null
            )?.use { c ->
                val byName = LinkedHashMap<String, Pair<String, Boolean>>()
                while (c.moveToNext()) {
                    val name = c.getString(0) ?: continue
                    val num = c.getString(1) ?: continue
                    val mobile = c.getInt(2) == ContactsContract.CommonDataKinds.Phone.TYPE_MOBILE
                    val old = byName[name]
                    if (old == null || (!old.second && mobile)) byName[name] = num to mobile
                }
                for ((name, v) in byName) {
                    val keys = ArrayList<Key>()
                    val full = Tr.phon(name)
                    keys.add(Key(full, 0f))
                    for (w in name.split(" ")) {
                        val p = Tr.phon(w)
                        if (p.length >= 3 && p != full) keys.add(Key(p, 0.15f))
                    }
                    out.add(ContactEntry(name, v.first, keys))
                }
            }
        } catch (_: Exception) {
        }
        contacts = out
        contactsAt = now
        return out
    }
}

/** Bulanık eşleştirme: söylenen kelimeler ile isimleri karşılaştırır. */
object Matcher {
    fun score(window: String, key: Key): Float? {
        val k = key.k
        if (k.isEmpty() || window.isEmpty()) return null
        val d = if (window.startsWith(k) && window.length - k.length <= 3) 0 else Tr.lev(window, k)
        val allowed = when {
            k.length <= 3 -> 0
            k.length <= 5 -> 1
            else -> (k.length * 0.3).toInt()
        }
        if (d > allowed) return null
        return d.toFloat() / k.length + key.penalty
    }

    fun <T> best(tokens: List<String>, items: List<T>, keysOf: (T) -> List<Key>): T? {
        if (tokens.isEmpty()) return null
        val phTokens = tokens.map { Tr.phon(it) }
        var bestItem: T? = null
        var bestScore = Float.MAX_VALUE
        for (item in items) {
            for (key in keysOf(item)) {
                for (start in phTokens.indices) {
                    var w = ""
                    for (end in start until minOf(phTokens.size, start + 4)) {
                        w += phTokens[end]
                        val s = score(w, key)
                        if (s != null && s < bestScore) {
                            bestScore = s
                            bestItem = item
                        }
                    }
                }
            }
        }
        return bestItem
    }
}
