package io.github.realkhfan.kotoba.data

/**
 * Turns romaji into hiragana while the user types, so answers can be typed on any keyboard.
 * Accepts Hepburn and the common alternatives (si, ti, tu, hu, zi, sya, ...), "nn" or "n'" for ん,
 * doubled consonants for っ, "-" for ー and x/l for small kana. Kana typed with a Japanese keyboard pass through.
 */
object Romaji {
    private val table: Map<String, String> = buildMap {
        val vowels = "aiueo"
        fun row(c: String, kana: String) = vowels.forEachIndexed { i, v -> if (kana[i] != '-') put(c + v, kana[i].toString()) }
        row("", "あいうえお"); row("k", "かきくけこ"); row("s", "さしすせそ"); row("t", "たちつてと")
        row("n", "なにぬねの"); row("h", "はひふへほ"); row("m", "まみむめも"); row("y", "や-ゆ-よ")
        row("r", "らりるれろ"); row("w", "わ---を"); row("g", "がぎぐげご"); row("z", "ざじずぜぞ")
        row("d", "だぢづでど"); row("b", "ばびぶべぼ"); row("p", "ぱぴぷぺぽ"); row("l", "ぁぃぅぇぉ"); row("x", "ぁぃぅぇぉ")
        put("shi", "し"); put("chi", "ち"); put("tsu", "つ"); put("fu", "ふ"); put("ji", "じ")
        fun combo(c: String, base: String) {
            put(c + "a", base + "ゃ"); put(c + "u", base + "ゅ"); put(c + "o", base + "ょ")
        }
        combo("ky", "き"); combo("sh", "し"); combo("sy", "し"); combo("ch", "ち"); combo("ty", "ち"); combo("cy", "ち")
        combo("ny", "に"); combo("hy", "ひ"); combo("my", "み"); combo("ry", "り"); combo("gy", "ぎ")
        combo("j", "じ"); combo("jy", "じ"); combo("zy", "じ"); combo("by", "び"); combo("py", "ぴ"); combo("dy", "ぢ")
        put("she", "しぇ"); put("je", "じぇ"); put("che", "ちぇ")
        put("fa", "ふぁ"); put("fi", "ふぃ"); put("fe", "ふぇ"); put("fo", "ふぉ")
        put("thi", "てぃ"); put("dhi", "でぃ"); put("dhu", "でゅ"); put("wi", "うぃ"); put("we", "うぇ")
        put("va", "ゔぁ"); put("vi", "ゔぃ"); put("vu", "ゔ"); put("ve", "ゔぇ"); put("vo", "ゔぉ")
        put("xya", "ゃ"); put("xyu", "ゅ"); put("xyo", "ょ"); put("lya", "ゃ"); put("lyu", "ゅ"); put("lyo", "ょ")
        put("xtu", "っ"); put("ltu", "っ"); put("xtsu", "っ"); put("ltsu", "っ"); put("xwa", "ゎ")
        put("nn", "ん"); put("n'", "ん"); put("-", "ー")
    }
    private val longest = table.keys.maxOf { it.length }
    private val vowelOf: Map<Char, Char> = table.filter { (r, k) -> k.length == 1 && r.last() in "aiueo" }
        .entries.associate { (r, k) -> k[0] to "あいうえお"["aiueo".indexOf(r.last())] }

    /** [final] also turns a trailing "n" into ん; while typing it waits, because "n" may become な. */
    fun toKana(input: String, final: Boolean = false): String {
        val s = input.lowercase()
        val out = StringBuilder()
        var i = 0
        while (i < s.length) {
            val c = s[i]
            if (c !in 'a'..'z' && c != '-' && c != '\'') { out.append(input[i]); i++; continue }
            // A doubled consonant (kk, tt, tch) is a small っ.
            if (c in "bcdfghjkmpqrstvwxyz" && i + 1 < s.length && (s[i + 1] == c || (c == 't' && s[i + 1] == 'c'))) {
                out.append('っ'); i++; continue
            }
            // "nn" is ん; before a vowel the second n starts the next syllable (konnichiwa → こんにちわ).
            if (c == 'n' && i + 1 < s.length && s[i + 1] == 'n') {
                out.append('ん')
                i += if (i + 2 < s.length && s[i + 2] in "aiueoy") 1 else 2
                continue
            }
            // n before a consonant (not y) is ん.
            if (c == 'n' && i + 1 < s.length && s[i + 1] !in "aiueoy'") { out.append('ん'); i++; continue }
            val match = (minOf(longest, s.length - i) downTo 1).firstOrNull { table.containsKey(s.substring(i, i + it)) }
            if (match != null) {
                out.append(table.getValue(s.substring(i, i + match)))
                i += match
                continue
            }
            val rest = s.substring(i)
            if (!final && table.keys.any { it.startsWith(rest) }) { out.append(input.substring(i)); break } // still being typed
            out.append(if (final && rest == "n") "ん" else input[i].toString())
            i++
        }
        return out.toString()
    }

    /** Kana with hiragana and katakana treated the same, without spaces or end punctuation. */
    fun normalize(s: String) = toHiragana(s).filterNot { it.isWhitespace() || it in "。、.,!?！？" }

    /** ー written out as the vowel before it, so "koohii" counts for コーヒー like "ko-hi-" does. */
    fun spellLong(kana: String) = buildString { kana.forEach { c -> append(if (c == 'ー' && isNotEmpty()) vowelOf[last()] ?: c else c) } }
}
