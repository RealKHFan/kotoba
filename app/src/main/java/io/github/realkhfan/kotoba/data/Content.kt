package io.github.realkhfan.kotoba.data

enum class Section(val title: String, val japanese: String, val prefix: String, val lessonSize: Int) {
    HIRAGANA("Hiragana", "ひらがな", "h", 5),
    KATAKANA("Katakana", "カタカナ", "k", 5),
    WORDS("Words", "単語", "w", 10),
    SENTENCES("Sentences", "文", "s", 5),
    GRAMMAR("Grammar", "文法", "g", 1),
    KANJI("Kanji", "漢字", "j", 5),
}

/** Something that can be learned and reviewed. The key is stable across app updates. */
sealed interface Item {
    val key: String
    val section: Section
    /** Lesson group: a kana row, a word topic, ... */
    val group: String
    /** JLPT level: 5 or 4. */
    val level: Int
    val searchText: String
}

data class Kana(val char: String, val romaji: String, override val section: Section, override val group: String) : Item {
    override val key get() = "${section.prefix}:$char"
    override val level get() = 5
    override val searchText get() = "$char $romaji"
}

data class Word(
    val written: String,
    val reading: String,
    val meaning: String,
    override val level: Int,
    override val group: String,
) : Item {
    override val key get() = "w:$written|$reading"
    override val section get() = Section.WORDS
    override val searchText get() = "$written $reading ${toRomaji(reading)} $meaning"
    val hasKanji get() = written.any(::isKanji)
}

data class Example(val japanese: String, val reading: String, val english: String)

data class Kanji(
    val char: String,
    val meaning: String,
    val on: List<String>,
    val kun: List<String>,
    val examples: List<Example>,
    override val level: Int,
    override val group: String,
) : Item {
    override val key get() = "j:$char"
    override val section get() = Section.KANJI
    override val searchText get() =
        "$char $meaning ${on.joinToString(" ")} ${kun.joinToString(" ")} ${(on + kun).joinToString(" ") { toRomaji(it.replace(".", "")) }}"
}

data class Sentence(
    val chunks: List<String>,
    val readings: List<String>,
    val english: String,
    override val level: Int,
    override val group: String,
) : Item {
    val japanese get() = chunks.joinToString("")
    val reading get() = readings.joinToString("")
    override val key get() = "s:$japanese"
    override val section get() = Section.SENTENCES
    override val searchText get() = "$japanese $reading $english"
}

/** A fill-in-the-gap question. The sentence contains ＿＿ where the answer goes. */
data class Gap(val sentence: String, val english: String, val answer: String, val wrong: List<String>)

data class Grammar(
    val id: String,
    val title: String,
    val meaning: String,
    val form: String,
    val notes: List<String>,
    val examples: List<Example>,
    val questions: List<Gap>,
    override val level: Int,
) : Item {
    override val key get() = "g:$id"
    override val section get() = Section.GRAMMAR
    override val group get() = "N$level"
    override val searchText get() = "$title $meaning $form"
}

/** Parsers for the text files in assets. The files are ours, so a bad line is a bug and throws. */
object Parser {
    private class Header(val level: Int, val topic: String)

    private fun header(line: String): Header {
        val parts = line.removePrefix("## ").trim().split(' ', limit = 2)
        return Header(parts[0].toInt(), parts.getOrElse(1) { "N${parts[0]}" })
    }

    private fun isComment(line: String) = line.isEmpty() || (line.startsWith("#") && !line.startsWith("## "))

    /** Runs [row] for every content line with the current group header. */
    private fun <T> rows(text: String, row: (line: String, h: Header) -> T): List<T> {
        var h = Header(0, "")
        return text.lineSequence().map(String::trim).mapNotNull { line ->
            when {
                isComment(line) -> null
                line.startsWith("## ") -> { h = header(line); null }
                else -> row(line, h)
            }
        }.toList()
    }

    fun words(text: String): List<Word> = rows(text) { line, h ->
        val f = line.split('|')
        when (f.size) {
            2 -> Word(f[0], f[0], f[1], h.level, h.topic)
            3 -> Word(f[0], f[1], f[2], h.level, h.topic)
            else -> error("Bad word line: $line")
        }
    }

    fun kanji(text: String): List<Kanji> = rows(text) { line, h ->
        val f = line.split('|')
        require(f.size == 5) { "Bad kanji line: $line" }
        val examples = f[4].split("; ").map {
            val e = it.split(' ', limit = 3)
            require(e.size == 3) { "Bad kanji example: $it" }
            Example(e[0], e[1], e[2])
        }
        Kanji(f[0], f[1], readings(f[2]), readings(f[3]), examples, h.level, h.topic)
    }

    private fun readings(s: String) = s.split('、').filter(String::isNotEmpty)

    fun sentences(text: String): List<Sentence> = rows(text) { line, h ->
        val f = line.split('|')
        require(f.size == 3) { "Bad sentence line: $line" }
        val chunks = f[0].split(' ')
        val readings = f[1].split(' ')
        require(chunks.size == readings.size) { "Chunks and readings differ: $line" }
        Sentence(chunks, readings, f[2], h.level, h.topic)
    }

    fun grammar(text: String): List<Grammar> {
        val out = mutableListOf<Grammar>()
        var level = 0
        var id: String? = null
        val fields = mutableMapOf<String, String>()
        val notes = mutableListOf<String>()
        val examples = mutableListOf<Example>()
        val questions = mutableListOf<Gap>()
        fun flush() {
            val current = id ?: return
            out += Grammar(
                current, fields.getValue("title"), fields.getValue("meaning"), fields.getValue("form"),
                notes.toList(), examples.toList(), questions.toList(), level,
            )
            fields.clear(); notes.clear(); examples.clear(); questions.clear()
        }
        for (line in text.lineSequence().map(String::trim)) {
            when {
                line.startsWith("## ") -> level = header(line).level
                isComment(line) -> Unit
                line.startsWith("= ") -> { flush(); id = line.removePrefix("= ") }
                else -> {
                    val key = line.substringBefore(": ")
                    val value = line.substringAfter(": ")
                    val f = value.split('|')
                    when (key) {
                        "note" -> notes += value
                        "ex" -> { require(f.size == 3) { "Bad example: $line" }; examples += Example(f[0], f[1], f[2]) }
                        "q" -> { require(f.size == 6) { "Bad question: $line" }; questions += Gap(f[0], f[1], f[2], f.drop(3)) }
                        else -> fields[key] = value
                    }
                }
            }
        }
        flush()
        return out
    }
}

class Library(
    val words: List<Word>,
    val kanji: List<Kanji>,
    val sentences: List<Sentence>,
    val grammar: List<Grammar>,
    private val strokes: Map<String, List<Stroke>> = emptyMap(),
) {
    val hiragana = KanaChart.items(Section.HIRAGANA)
    val katakana = KanaChart.items(Section.KATAKANA)

    fun items(section: Section): List<Item> = when (section) {
        Section.HIRAGANA -> hiragana
        Section.KATAKANA -> katakana
        Section.WORDS -> words
        Section.SENTENCES -> sentences
        Section.GRAMMAR -> grammar
        Section.KANJI -> kanji
    }

    val all: List<Item> by lazy { Section.entries.flatMap(::items) }
    val byKey: Map<String, Item> by lazy { all.associateBy { it.key } }
    private val kanjiByChar by lazy { kanji.associateBy { it.char } }

    /** The next unlearned items, kept within one group so a lesson stays on one topic. */
    fun nextLesson(section: Section, size: Int = section.lessonSize, learned: (String) -> Boolean): List<Item> {
        val items = items(section)
        val first = items.indexOfFirst { !learned(it.key) }
        if (first < 0) return emptyList()
        val group = items[first].group
        return items.drop(first).takeWhile { it.group == group }.filter { !learned(it.key) }.take(size)
    }

    /** Stroke order for one kana or kanji, or null if there is none. */
    fun strokesOf(char: String): List<Stroke>? = strokes[char]

    fun kanjiIn(word: Word): List<Kanji> = word.written.mapNotNull { kanjiByChar[it.toString()] }.distinct()

    fun wordsWith(kanji: Kanji): List<Word> = words.filter { kanji.char in it.written }

    /**
     * Finds a word in running text. Verbs and adjectives are matched by their stem, so 食べる finds 食べました;
     * the stem must not touch another kanji, so 行く doesn't match 銀行 and 上 doesn't match 上手.
     * Kana words are only matched when they are at least three characters long.
     */
    internal fun finder(word: Word): Regex? {
        val w = word.written
        val kanjiClass = "[\\u3400-\\u9FFF々]"
        return when {
            !word.hasKanji -> if (w.length >= 3) Regex(Regex.escape(w)) else null
            w.endsWith("する") -> Regex("(?<!$kanjiClass)" + Regex.escape(w.dropLast(2)) + "(?!$kanjiClass)")
            w.last() in 'ぁ'..'ゖ' -> Regex("(?<!$kanjiClass)" + Regex.escape(w.dropLast(1)) + "[ぁ-ゖ]")
            else -> Regex("(?<!$kanjiClass)" + Regex.escape(w) + "(?!$kanjiClass)")
        }
    }

    private val finders = HashMap<Word, Regex?>()
    private fun finderFor(word: Word) = synchronized(finders) { finders.getOrPut(word) { finder(word) } }

    fun examplesFor(word: Word): List<Example> {
        val find = finderFor(word) ?: return emptyList()
        val fromSentences = sentences.filter { find.containsMatchIn(it.japanese) }.map { Example(it.japanese, it.reading, it.english) }
        val fromGrammar = grammar.flatMap { it.examples }.filter { find.containsMatchIn(it.japanese) }
        return (fromSentences + fromGrammar).distinctBy { it.japanese }
    }

    /** Words in a sentence that are written with kanji, longest first. */
    fun wordsIn(sentence: Sentence): List<Word> = words
        .filter { it.hasKanji && finderFor(it)?.containsMatchIn(sentence.japanese) == true }
        .sortedByDescending { it.written.length }.distinctBy { it.written }

    /** Katakana words keep their reading in katakana, so this finds the right script on its own. */
    fun wordsStartingWith(kana: Kana): List<Word> = words.filter { it.reading.startsWith(kana.char) }

    companion object {
        fun load(open: (String) -> String) = Library(
            Parser.words(open("words.txt")),
            Parser.kanji(open("kanji.txt")),
            Parser.sentences(open("sentences.txt")),
            Parser.grammar(open("grammar-n5.txt")) + Parser.grammar(open("grammar-n4.txt")),
            Strokes.parse(open("strokes.txt")),
        )
    }
}
