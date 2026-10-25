package com.mecnun.assistant

/** Uyandırma kelimesi tespiti ("mecnun", "mecnum", "mec nun" ...). */
object Wake {

    /** (başlangıç indeksi, kaç kelime) ya da null */
    fun find(tokens: List<String>): Pair<Int, Int>? {
        val wake = Tr.norm(Prefs.wakeWord).replace(" ", "")
        val tol = Prefs.wakeTolerance
        for (i in tokens.indices) {
            if (match(tokens[i], wake, tol)) return i to 1
            if (i + 1 < tokens.size && match(tokens[i] + tokens[i + 1], wake, tol)) return i to 2
        }
        return null
    }

    private fun match(t: String, wake: String, tol: Int): Boolean {
        if (t == wake) return true
        if (tol == 0) return false
        if (t.length < 4) return false
        // "mecnunu", "mecnuna" gibi ekli halleri
        if (t.startsWith(wake) && t.length - wake.length <= 2) return true
        return Tr.lev(t, wake) <= tol
    }
}
