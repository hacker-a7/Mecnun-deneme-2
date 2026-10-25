package com.mecnun.assistant

import android.content.Context
import java.util.Calendar

enum class VolKind { UP, DOWN, MUTE, SET }
enum class MediaAct { PAUSE, PLAY, NEXT, PREV }

/** Anlaşılan komut. label = geçmiş ekranında "ne anladı" olarak gösterilir. */
sealed class Cmd(val label: String) {
    object Lock : Cmd("Ekranı kilitle")
    object Screenshot : Cmd("Ekran görüntüsü al")
    data class Torch(val on: Boolean?) : Cmd(
        when (on) { true -> "Feneri aç"; false -> "Feneri kapat"; null -> "Feneri değiştir" }
    )
    data class Volume(val kind: VolKind, val percent: Int? = null) : Cmd(
        when (kind) {
            VolKind.UP -> "Sesi artır"
            VolKind.DOWN -> "Sesi azalt"
            VolKind.MUTE -> "Sesi kapat"
            VolKind.SET -> "Sesi %${percent ?: 0} yap"
        }
    )
    object Time : Cmd("Saati söyle")
    object Battery : Cmd("Şarj durumunu söyle")
    data class OpenApp(val app: AppEntry?, val heard: String) :
        Cmd(if (app != null) "Uygulama aç: ${app.label}" else "Uygulama aç: bulunamadı (\"$heard\")")
    object RecStart : Cmd("Ses kaydını başlat")
    object RecStop : Cmd("Ses kaydını durdur")
    data class Call(val contact: ContactEntry?, val number: String?, val heard: String) : Cmd(
        when {
            contact != null -> "Ara: ${contact.name}"
            number != null -> "Ara: $number"
            else -> "Ara: kişi bulunamadı (\"$heard\")"
        }
    )
    data class Sms(val contact: ContactEntry?, val heard: String) :
        Cmd(if (contact != null) "Mesaj gönder: ${contact.name}" else "Mesaj gönder: kişi bulunamadı (\"$heard\")")
    data class NoteNew(val text: String?) : Cmd(if (text.isNullOrBlank()) "Not al (söylenecek)" else "Not al: $text")
    data class NoteRead(val all: Boolean) : Cmd(if (all) "Notları oku" else "Son notu oku")
    data class Alarm(val hour: Int, val minute: Int) : Cmd("Alarm kur: %02d:%02d".format(hour, minute))
    data class AlarmFail(val heard: String) : Cmd("Alarm: saat anlaşılamadı")
    data class Media(val act: MediaAct) : Cmd(
        when (act) {
            MediaAct.PAUSE -> "Müziği durdur"
            MediaAct.PLAY -> "Müziği başlat"
            MediaAct.NEXT -> "Sonraki şarkı"
            MediaAct.PREV -> "Önceki şarkı"
        }
    )
    data class Unknown(val heard: String) : Cmd("Anlaşılamadı")
}

object Interpreter {

    private val appStop = setOf(
        "uygulamasina", "uygulamaya", "uygulamasini", "uygulamasi", "uygulama", "uygulamayi",
        "gir", "girer", "girsene", "girin", "ac", "acar", "acsana", "baslat", "calistir", "git",
        "lutfen", "sunu", "su", "bu", "bir", "bi", "misin", "hadi", "ya", "icine", "ici"
    )
    private val callStop = setOf(
        "ara", "arar", "arayin", "arasana", "misin", "lutfen", "telefon", "et", "aramasi",
        "cagri", "baslat", "yap", "aramak", "bir", "bi", "sunu", "su", "hadi"
    )
    private val smsStop = setOf(
        "mesaj", "mesaji", "mesajini", "sms", "gonder", "gondersene", "at", "atar", "yaz", "yazar",
        "misin", "lutfen", "icin", "bir", "bi", "sunu", "su", "hadi", "mesajlas"
    )

    private fun cleaned(tokens: List<String>, stop: Set<String>): List<String> {
        val t = tokens.filter { it !in stop }
        val long = t.filter { it.length > 2 }
        return if (long.isNotEmpty()) long else t
    }

    private fun has(t: List<String>, vararg words: String) = t.any { it in words }
    private fun starts(t: List<String>, vararg prefixes: String) = t.any { tok -> prefixes.any { tok.startsWith(it) } }

    fun parse(ctx: Context, tokens: List<String>, rawWords: List<String> = tokens): Cmd {
        val heard = tokens.joinToString(" ")
        if (tokens.isEmpty()) return Cmd.Unknown(heard)

        // 1) Ses kaydı
        if (starts(tokens, "kayd", "kayit")) {
            return if (has(tokens, "dur", "durdur", "bitir", "kapat", "bitti", "sonlandir", "kaydet", "son")
                || starts(tokens, "durd", "bitir", "sonland")
            ) Cmd.RecStop else Cmd.RecStart
        }

        // 2) Not
        if (starts(tokens, "not") && !starts(tokens, "nota", "notr")) {
            if (has(tokens, "oku", "okur", "dinlet", "okusana") || starts(tokens, "okur")) {
                return Cmd.NoteRead(all = !has(tokens, "son", "sonuncu", "sonunu") && !starts(tokens, "sonu"))
            }
            return Cmd.NoteNew(noteInline(tokens, rawWords))
        }

        // 3) Mesaj
        if (starts(tokens, "mesaj", "sms")) {
            val name = cleaned(tokens, smsStop)
            val c = matchContact(ctx, name)
            return Cmd.Sms(c, name.joinToString(" "))
        }

        // 4) Arama
        if (has(tokens, "ara", "arar", "arayin", "arasana", "aramak") || starts(tokens, "telefon")) {
            val name = cleaned(tokens, callStop)
            val digits = name.mapNotNull { Tr.digitWord(it) }.joinToString("")
            if (digits.length >= 7 && digits.length >= name.size - 1) return Cmd.Call(null, digits, heard)
            val c = matchContact(ctx, name)
            return Cmd.Call(c, null, name.joinToString(" "))
        }

        // 5) Alarm
        if (starts(tokens, "alarm")) {
            return parseAlarm(tokens) ?: Cmd.AlarmFail(heard)
        }

        // 6) Fener
        if (starts(tokens, "fener")) {
            val off = has(tokens, "kapat", "kapa", "sondur", "kapatir", "kapatsana") || starts(tokens, "kapat", "sondur")
            val on = has(tokens, "ac", "yak", "acar", "acsana", "yakar") || starts(tokens, "yak")
            return Cmd.Torch(if (off) false else if (on) true else null)
        }

        // 7) Ses seviyesi
        if (starts(tokens, "ses") && !starts(tokens, "sesli")) {
            val num = Tr.firstNumber(tokens)
            return when {
                starts(tokens, "sessiz") || has(tokens, "kapat", "sustur", "sifirla", "sifir") && num == null ->
                    Cmd.Volume(VolKind.MUTE)
                num != null && !starts(tokens, "kis", "azalt", "alcalt", "dusur") ->
                    Cmd.Volume(VolKind.SET, num.coerceIn(0, 100))
                starts(tokens, "kis", "azalt", "alcalt", "dusur") || has(tokens, "kis", "kisar") ->
                    Cmd.Volume(VolKind.DOWN)
                has(tokens, "ac", "acar") || starts(tokens, "yukselt", "artir", "coalt", "yuksel", "arttir") ->
                    Cmd.Volume(VolKind.UP)
                else -> Cmd.Unknown(heard)
            }
        }

        // 8) Müzik
        if (starts(tokens, "durdur", "duraklat", "sonraki", "ileri", "onceki") ||
            has(tokens, "gec", "oynat", "cal", "devam", "sirada", "pause", "play", "next")
        ) {
            return when {
                starts(tokens, "durdur", "duraklat") || has(tokens, "dur", "pause") -> Cmd.Media(MediaAct.PAUSE)
                starts(tokens, "sonraki", "ileri") || has(tokens, "gec", "next", "sirada") -> Cmd.Media(MediaAct.NEXT)
                starts(tokens, "onceki") -> Cmd.Media(MediaAct.PREV)
                else -> Cmd.Media(MediaAct.PLAY)
            }
        }

        // 9) Saat
        if (has(tokens, "saat") && (has(tokens, "kac", "ne", "kaç") || tokens.size == 1)) return Cmd.Time

        // 10) Şarj
        if (starts(tokens, "sarj", "pil", "batarya")) return Cmd.Battery

        // 11) Ekran görüntüsü
        if (starts(tokens, "goruntu", "screenshot") || (has(tokens, "ekran") && has(tokens, "al", "cek", "yakala"))) {
            return Cmd.Screenshot
        }

        // 12) Ekranı kilitle / telefonu kapat
        if (has(tokens, "kapan", "kapat", "kilitle", "kilit", "uyu", "uyut", "kapatir") ||
            starts(tokens, "kilitle", "kapan", "kapat")
        ) return Cmd.Lock

        // 13) Uygulama aç
        if (has(tokens, "gir", "ac", "baslat", "git", "calistir", "girer", "acar", "girsene", "acsana", "girin") ||
            starts(tokens, "uygulama")
        ) {
            val name = cleaned(tokens, appStop)
            return Cmd.OpenApp(matchApp(ctx, name), name.joinToString(" "))
        }

        // 14) Son çare: komutun tamamını uygulama adı gibi dene (örn. "Mecnun WhatsApp")
        val app = matchApp(ctx, cleaned(tokens, appStop))
        if (app != null && tokens.size <= 3) return Cmd.OpenApp(app, heard)

        return Cmd.Unknown(heard)
    }

    // ---------- yardımcılar ----------

    private fun noteInline(tokens: List<String>, raw: List<String>): String? {
        // "not al süt al" -> "süt al"; "şunu not al süt" -> "süt"
        val r = if (raw.size == tokens.size) raw else tokens
        val verbs = setOf("al", "et", "yaz", "kaydet", "alir", "alsana", "alabilir", "misin", "lutfen")
        val fillers = setOf("sunu", "su", "bu", "bir", "bi", "lutfen")
        var idx = -1
        for (i in tokens.indices) if (tokens[i].startsWith("not")) { idx = i; break }
        if (idx < 0) return null
        var j = idx + 1
        while (j < tokens.size && tokens[j] in verbs) j++
        val keep = ArrayList<String>()
        for (k in tokens.indices) {
            if (k == idx) continue
            if (k > idx && k < j) continue
            if (k < idx && tokens[k] in fillers) continue
            keep.add(r[k])
        }
        return if (keep.isEmpty()) null else keep.joinToString(" ")
    }

    fun matchApp(ctx: Context, tokens: List<String>): AppEntry? {
        if (tokens.isEmpty()) return null
        val aliases = Prefs.aliases()
        val apps = Directory.apps(ctx)
        // Takma ad tam eşleşme önceliklidir
        val joined = tokens.joinToString("")
        for ((alias, pkg) in aliases) {
            val aj = Tr.phon(alias)
            if (aj.isNotEmpty() && (joined == aj || (joined.startsWith(aj) && joined.length - aj.length <= 3))) {
                apps.firstOrNull { it.pkg == pkg }?.let { return it }
            }
        }
        val withAliases = apps.map { a ->
            val extra = aliases.filter { it.value == a.pkg }.map { Key(Tr.phon(it.key), -0.2f) }
            if (extra.isEmpty()) a else AppEntry(a.label, a.pkg, a.keys + extra)
        }
        return Matcher.best(tokens, withAliases) { it.keys }
    }

    private fun matchContact(ctx: Context, tokens: List<String>): ContactEntry? {
        if (tokens.isEmpty()) return null
        return Matcher.best(tokens, Directory.contacts(ctx)) { it.keys }
    }

    private fun parseAlarm(tokens: List<String>): Cmd? {
        val now = Calendar.getInstance()
        // "10 dakika sonra", "iki saat sonra"
        if (has(tokens, "sonra", "sonraya")) {
            for (i in tokens.indices) {
                val p = Tr.parseNumber(tokens, i) ?: continue
                val nextIdx = i + p.second
                if (nextIdx < tokens.size) {
                    val unit = tokens[nextIdx]
                    val add = when {
                        unit.startsWith("dakika") || unit == "dk" -> p.first
                        unit.startsWith("saat") -> p.first * 60
                        else -> continue
                    }
                    now.add(Calendar.MINUTE, add)
                    return Cmd.Alarm(now.get(Calendar.HOUR_OF_DAY), now.get(Calendar.MINUTE))
                }
            }
        }
        // Mutlak saat: "yedi buçuk", "yedi otuz", "sekize"
        var i = 0
        while (i < tokens.size) {
            val p = Tr.parseNumber(tokens, i)
            if (p == null || p.first > 24) { i++; continue }
            var hour = p.first
            var minute = 0
            val ni = i + p.second
            if (ni < tokens.size && tokens[ni].startsWith("bucuk")) {
                minute = 30
            } else if (ni < tokens.size) {
                val q = Tr.parseNumber(tokens, ni)
                if (q != null && q.first in 0..59) minute = q.first
            }
            val pm = has(tokens, "aksam", "aksama", "aksamki") ||
                (has(tokens, "ogleden") && has(tokens, "sonra")) ||
                (has(tokens, "gece", "geceyi") && hour in 6..11)
            if (pm && hour in 1..11) hour += 12
            if (has(tokens, "sabah", "sabahki", "sabaha") && hour == 12) hour = 0
            if (hour == 24) hour = 0
            return Cmd.Alarm(hour, minute)
        }
        return null
    }
}
