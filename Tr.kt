package com.mecnun.assistant

import kotlin.math.max
import kotlin.math.min

/** Türkçe metin yardımcıları: sadeleştirme, bulanık eşleşme, sayılar. */
object Tr {

    /** Küçük harf, Türkçe karakterleri ASCII'ye indir, noktalama -> boşluk. */
    fun norm(s: String): String {
        val sb = StringBuilder()
        for (ch in s) {
            val c = when (ch) {
                'İ', 'I', 'ı', 'î', 'Î' -> 'i'
                'Ç', 'ç' -> 'c'
                'Ğ', 'ğ' -> 'g'
                'Ö', 'ö' -> 'o'
                'Ş', 'ş' -> 's'
                'Ü', 'ü', 'û', 'Û' -> 'u'
                'â', 'Â' -> 'a'
                else -> ch.lowercaseChar()
            }
            sb.append(if (c.isLetterOrDigit()) c else ' ')
        }
        return sb.toString().trim().replace(Regex("\\s+"), " ")
    }

    fun tokens(s: String): List<String> {
        val n = norm(s)
        return if (n.isEmpty()) emptyList() else n.split(" ")
    }

    /** Kabaca ses benzerliği anahtarı: "WhatsApp" ile "vatsap" aynı çıksın. */
    fun phon(s: String): String {
        var x = norm(s).replace(" ", "")
        x = x.replace("wh", "v").replace("w", "v").replace("q", "k")
            .replace("x", "ks").replace("ph", "f").replace("ck", "k")
        val sb = StringBuilder()
        for (c in x) if (sb.isEmpty() || sb.last() != c) sb.append(c)
        return sb.toString()
    }

    fun lev(a: String, b: String): Int {
        if (a == b) return 0
        if (a.isEmpty()) return b.length
        if (b.isEmpty()) return a.length
        var prev = IntArray(b.length + 1) { it }
        var cur = IntArray(b.length + 1)
        for (i in 1..a.length) {
            cur[0] = i
            for (j in 1..b.length) {
                val cost = if (a[i - 1] == b[j - 1]) 0 else 1
                cur[j] = min(min(cur[j - 1] + 1, prev[j] + 1), prev[j - 1] + cost)
            }
            val t = prev; prev = cur; cur = t
        }
        return prev[b.length]
    }

    fun capitalize(s: String): String {
        if (s.isEmpty()) return s
        val first = s.substring(0, 1).uppercase(java.util.Locale("tr", "TR"))
        return first + s.substring(1)
    }

    // ---------------- Sayılar ----------------

    private val numMap: Map<String, Int> = mapOf(
        "sifir" to 0,
        "bir" to 1, "bire" to 1, "biri" to 1,
        "iki" to 2, "ikiye" to 2, "ikiyi" to 2,
        "uc" to 3, "uce" to 3, "ucu" to 3,
        "dort" to 4, "dorde" to 4, "dortu" to 4,
        "bes" to 5, "bese" to 5, "besi" to 5,
        "alti" to 6, "altiya" to 6, "altiyi" to 6,
        "yedi" to 7, "yediye" to 7, "yediyi" to 7,
        "sekiz" to 8, "sekize" to 8, "sekizi" to 8,
        "dokuz" to 9, "dokuza" to 9, "dokuzu" to 9,
        "on" to 10, "ona" to 10, "onu" to 10,
        "yirmi" to 20, "yirmiye" to 20,
        "otuz" to 30, "otuza" to 30,
        "kirk" to 40, "kirka" to 40,
        "elli" to 50, "elliye" to 50,
        "altmis" to 60, "yetmis" to 70, "seksen" to 80, "doksan" to 90,
        "yuz" to 100, "yuze" to 100
    )

    private fun numWord(t: String): Int? {
        if (t.isNotEmpty() && t.all { it.isDigit() } && t.length <= 4) return t.toInt()
        return numMap[t]
    }

    /** tokens[from] konumundan sayı oku. (değer, kaç kelime tüketildi) */
    fun parseNumber(tokens: List<String>, from: Int): Pair<Int, Int>? {
        if (from >= tokens.size) return null
        val a = numWord(tokens[from]) ?: return null
        if (a in 20..90 && a % 10 == 0 && from + 1 < tokens.size) {
            val b = numWord(tokens[from + 1])
            if (b != null && b in 1..9) return (a + b) to 2
        }
        if (a == 10 && from + 1 < tokens.size) {
            val b = numWord(tokens[from + 1])
            if (b != null && b in 1..9) return (a + b) to 2
        }
        return a to 1
    }

    fun firstNumber(tokens: List<String>): Int? {
        for (i in tokens.indices) {
            val p = parseNumber(tokens, i)
            if (p != null) return p.first
        }
        return null
    }

    private val units = arrayOf("", "bir", "iki", "üç", "dört", "beş", "altı", "yedi", "sekiz", "dokuz")
    private val tens = arrayOf("", "on", "yirmi", "otuz", "kırk", "elli", "altmış", "yetmiş", "seksen", "doksan")

    /** 0..100 arası sayıyı yazıya çevir (TTS daha doğal okusun diye). */
    fun words(n: Int): String {
        if (n == 0) return "sıfır"
        if (n >= 100) return "yüz"
        val t = n / 10
        val u = n % 10
        return (tens[t] + (if (t > 0 && u > 0) " " else "") + units[u]).trim()
    }

    fun clockWords(h: Int, m: Int): String =
        if (m == 0) words(h) else words(h) + " " + words(m)

    /** Telefon numarası için rakam kelimelerini rakama çevir. */
    fun digitWord(t: String): Char? = when (t) {
        "sifir" -> '0'; "bir" -> '1'; "iki" -> '2'; "uc" -> '3'; "dort" -> '4'
        "bes" -> '5'; "alti" -> '6'; "yedi" -> '7'; "sekiz" -> '8'; "dokuz" -> '9'
        else -> if (t.length == 1 && t[0].isDigit()) t[0] else null
    }

    fun maxOf3(a: Int, b: Int, c: Int) = max(a, max(b, c))
}
