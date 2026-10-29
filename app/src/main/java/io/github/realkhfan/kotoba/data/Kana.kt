package io.github.realkhfan.kotoba.data

/** The hiragana and katakana charts. Katakana is hiragana shifted by 0x60 in Unicode. */
object KanaChart {
    enum class Part(val title: String) {
        BASIC("Basic sounds"),
        MARKS("With ゛ and ゜"),
        COMBOS("Combinations"),
        EXTENDED("Katakana only"),
    }

    /** One chart row. Cells are (hiragana, romaji); null is an empty cell (や row, わ row). */
    data class Row(val name: String, val part: Part, val cells: List<Pair<String, String>?>)

    // Part, row name, cells. "-" is an empty cell.
    private const val SPEC = """
B a あ:a い:i う:u え:e お:o
B ka か:ka き:ki く:ku け:ke こ:ko
B sa さ:sa し:shi す:su せ:se そ:so
B ta た:ta ち:chi つ:tsu て:te と:to
B na な:na に:ni ぬ:nu ね:ne の:no
B ha は:ha ひ:hi ふ:fu へ:he ほ:ho
B ma ま:ma み:mi む:mu め:me も:mo
B ya や:ya - ゆ:yu - よ:yo
B ra ら:ra り:ri る:ru れ:re ろ:ro
B wa わ:wa - - - を:wo
B n ん:n
M ga が:ga ぎ:gi ぐ:gu げ:ge ご:go
M za ざ:za じ:ji ず:zu ぜ:ze ぞ:zo
M da だ:da ぢ:ji づ:zu で:de ど:do
M ba ば:ba び:bi ぶ:bu べ:be ぼ:bo
M pa ぱ:pa ぴ:pi ぷ:pu ぺ:pe ぽ:po
C kya きゃ:kya きゅ:kyu きょ:kyo
C sha しゃ:sha しゅ:shu しょ:sho
C cha ちゃ:cha ちゅ:chu ちょ:cho
C nya にゃ:nya にゅ:nyu にょ:nyo
C hya ひゃ:hya ひゅ:hyu ひょ:hyo
C mya みゃ:mya みゅ:myu みょ:myo
C rya りゃ:rya りゅ:ryu りょ:ryo
C gya ぎゃ:gya ぎゅ:gyu ぎょ:gyo
C ja じゃ:ja じゅ:ju じょ:jo
C bya びゃ:bya びゅ:byu びょ:byo
C pya ぴゃ:pya ぴゅ:pyu ぴょ:pyo
E fa ふぁ:fa ふぃ:fi ふぇ:fe ふぉ:fo
E ti てぃ:ti でぃ:di でゅ:dyu
E wi うぃ:wi うぇ:we うぉ:wo
E she しぇ:she じぇ:je ちぇ:che
E vu ゔ:vu
"""

    val rows: List<Row> = SPEC.trim().lines().map { line ->
        val f = line.split(' ')
        val part = Part.entries.first { it.name[0] == f[0][0] }
        Row(f[1], part, f.drop(2).map { c -> if (c == "-") null else c.substringBefore(':') to c.substringAfter(':') })
    }

    /** Rows for one script: hiragana has no katakana-only rows. Cells are converted to the script. */
    fun rows(section: Section): List<Row> = when (section) {
        Section.HIRAGANA -> rows.filter { it.part != Part.EXTENDED }
        Section.KATAKANA -> rows.map { r -> r.copy(cells = r.cells.map { c -> c?.let { toKatakana(it.first) to it.second } }) }
        else -> emptyList()
    }

    fun items(section: Section): List<Kana> =
        rows(section).flatMap { r -> r.cells.filterNotNull().map { (k, romaji) -> Kana(k, romaji, section, r.name) } }

    internal val romaji: Map<String, String> = buildMap {
        rows.forEach { r -> r.cells.filterNotNull().forEach { (k, ro) -> putIfAbsent(k, ro) } }
        // Small kana on their own, and the long-vowel mark handled in toRomaji.
        listOf("ぁ" to "a", "ぃ" to "i", "ぅ" to "u", "ぇ" to "e", "ぉ" to "o", "ゃ" to "ya", "ゅ" to "yu", "ょ" to "yo", "ゎ" to "wa")
            .forEach { (k, ro) -> put(k, ro) }
    }
}

fun toKatakana(s: String) = String(CharArray(s.length) { i -> s[i].let { if (it in 'ぁ'..'ゖ') it + 0x60 else it } })

fun toHiragana(s: String) = String(CharArray(s.length) { i -> s[i].let { if (it in 'ァ'..'ヶ') it - 0x60 else it } })

fun isKanji(c: Char) = c in '一'..'鿿' || c in '㐀'..'䶿' || c == '々'

/** Hepburn-style romaji for hiragana or katakana text; other characters pass through. */
fun toRomaji(text: String): String {
    val s = toHiragana(text)
    val out = StringBuilder()
    var double = false
    var i = 0
    while (i < s.length) {
        val c = s[i]
        if (c == 'っ') { double = true; i++; continue }
        if (c == 'ー') { out.lastOrNull()?.takeIf { it in "aeiou" }?.let(out::append); i++; continue }
        val pair = if (i + 1 < s.length) KanaChart.romaji[s.substring(i, i + 2)] else null
        val r = pair ?: KanaChart.romaji[c.toString()]
        if (r == null) { out.append(c); double = false; i++; continue }
        if (double) out.append(if (r.startsWith("ch")) 't' else r[0])
        double = false
        out.append(r)
        i += if (pair != null) 2 else 1
    }
    return out.toString()
}
