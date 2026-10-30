package io.github.realkhfan.kotoba.data

import kotlin.random.Random

sealed interface Question { val item: Item }

/** Multiple choice. [reading] is shown small above a Japanese [prompt]; [hint] below it. */
data class Choice(
    override val item: Item,
    val ask: String,
    val prompt: String,
    val promptJapanese: Boolean,
    val reading: String? = null,
    val hint: String? = null,
    val options: List<String>,
    val optionsJapanese: Boolean,
    val answer: Int,
) : Question

/**
 * Type the answer. With [kana], romaji turns into kana while typing. Any of [accepted] counts,
 * typed as it is or as romaji (so "shi", "si" and し are all right for し).
 */
data class Typed(
    override val item: Item,
    val ask: String,
    val prompt: String,
    val promptJapanese: Boolean,
    val reading: String? = null,
    val hint: String? = null,
    val kana: Boolean,
    val accepted: List<String>,
) : Question {
    val answer get() = accepted.first()

    fun isRight(input: String): Boolean {
        val raw = input.trim()
        val asKana = Romaji.spellLong(Romaji.normalize(Romaji.toKana(raw, final = true)))
        return raw.isNotEmpty() && accepted.any { it.equals(raw, ignoreCase = true) || Romaji.spellLong(Romaji.normalize(it)) == asKana }
    }
}

/** Put the shuffled chunks of a sentence back in order. */
data class Order(override val item: Sentence, val tiles: List<String>) : Question {
    fun isRight(picked: List<Int>) = picked.joinToString("") { tiles[it] } == item.japanese
}

/** True when two meanings share a sense ("why" and "why; how come"), so one can't be a wrong option for the other. */
fun sameMeaning(a: String, b: String): Boolean {
    fun senses(s: String) = s.lowercase().split(";").map(String::trim).toSet()
    return senses(a).intersect(senses(b)).isNotEmpty()
}

class Quiz(private val library: Library, private val random: Random, private val settings: Settings = Settings()) {

    private fun typing() = when (settings.answers) {
        AnswerMode.CHOICE -> false
        AnswerMode.TYPE -> true
        AnswerMode.MIX -> random.nextBoolean()
    }

    private fun isKana(s: String) = s.all { it in 'ぁ'..'ゖ' || it in 'ァ'..'ヺ' || it == 'ー' }

    fun question(item: Item): Question = when (item) {
        is Kana -> kana(item)
        is Word -> word(item)
        is Kanji -> kanji(item)
        is Grammar -> grammar(item)
        is Sentence -> sentence(item)
    }

    /** Three wrong options from [pool]: same group first, never one that [clashes] with the item. */
    private fun <T : Item> wrong(item: T, pool: List<T>, label: (T) -> String, clashes: (T) -> Boolean): List<String> {
        val right = label(item)
        val (same, other) = pool.filter { it.key != item.key && !clashes(it) }.partition { it.group == item.group }
        return (same.shuffled(random) + other.shuffled(random)).map(label).filter { it != right }.distinct().take(3)
    }

    private fun options(right: String, wrong: List<String>): Pair<List<String>, Int> {
        val all = (wrong + right).shuffled(random)
        return all to all.indexOf(right)
    }

    private fun kana(k: Kana): Question {
        val pool = library.items(k.section).filterIsInstance<Kana>()
        val clash = { o: Kana -> o.romaji == k.romaji }
        val readIt = random.nextBoolean()
        if (readIt && typing()) return Typed(k, "Type the romaji", k.char, true, kana = false, accepted = listOf(k.romaji, k.char))
        return if (readIt) {
            val (opts, i) = options(k.romaji, wrong(k, pool, Kana::romaji, clash))
            Choice(k, "How do you read it?", k.char, true, options = opts, optionsJapanese = false, answer = i)
        } else {
            val (opts, i) = options(k.char, wrong(k, pool, Kana::char, clash))
            Choice(k, "Which ${k.section.title.lowercase()} is it?", k.romaji, false, options = opts, optionsJapanese = true, answer = i)
        }
    }

    private fun word(w: Word): Question {
        val clash = { o: Word -> sameMeaning(o.meaning, w.meaning) || o.reading == w.reading || o.written == w.written }
        val kind = random.nextInt(if (w.hasKanji) 3 else 2)
        if (kind > 0 && typing()) {
            return if (kind == 1) Typed(w, "Type it in Japanese", w.meaning, false, kana = true, accepted = listOf(w.reading, w.written))
            else Typed(w, "Type the reading", w.written, true, kana = true, accepted = listOf(w.reading))
        }
        return when (kind) {
            0 -> {
                val (opts, i) = options(w.meaning, wrong(w, library.words, Word::meaning, clash))
                val reading = w.reading.takeIf { w.hasKanji && settings.quizReadings }
                Choice(w, "What does it mean?", w.written, true, reading = reading, options = opts, optionsJapanese = false, answer = i)
            }
            1 -> {
                val (opts, i) = options(w.reading, wrong(w, library.words, Word::reading, clash))
                Choice(w, "How do you say it?", w.meaning, false, options = opts, optionsJapanese = true, answer = i)
            }
            else -> {
                val (opts, i) = options(w.reading, wrong(w, library.words.filter { it.hasKanji }, Word::reading, clash))
                Choice(w, "How do you read it?", w.written, true, options = opts, optionsJapanese = true, answer = i)
            }
        }
    }

    private fun kanji(k: Kanji): Question {
        if (random.nextBoolean()) {
            val (opts, i) = options(k.meaning, wrong(k, library.kanji, Kanji::meaning) { sameMeaning(it.meaning, k.meaning) })
            return Choice(k, "What does it mean?", k.char, true, options = opts, optionsJapanese = false, answer = i)
        }
        val e = k.examples.random(random)
        if (typing()) return Typed(k, "Type the reading of this word", e.japanese, true, hint = e.english, kana = true, accepted = listOf(e.reading))
        val wrong = library.kanji.filter { it.key != k.key }.flatMap { it.examples }.map { it.reading }
            .filter { it != e.reading }.distinct().shuffled(random).take(3)
        val (opts, i) = options(e.reading, wrong)
        return Choice(k, "How do you read this word?", e.japanese, true, hint = e.english, options = opts, optionsJapanese = true, answer = i)
    }

    private fun grammar(g: Grammar): Question {
        val q = g.questions.random(random)
        if (isKana(q.answer) && typing()) return Typed(g, "Type the missing part", q.sentence, true, hint = q.english, kana = true, accepted = listOf(q.answer))
        val (opts, i) = options(q.answer, q.wrong)
        return Choice(g, "Fill the gap", q.sentence, true, hint = q.english, options = opts, optionsJapanese = true, answer = i)
    }

    private fun sentence(s: Sentence): Question {
        if (s.chunks.size >= 3 && random.nextBoolean()) {
            var tiles = s.chunks.shuffled(random)
            repeat(5) { if (tiles == s.chunks) tiles = s.chunks.shuffled(random) }
            return Order(s, tiles)
        }
        val (opts, i) = options(s.english, wrong(s, library.sentences, Sentence::english) { it.english == s.english })
        return Choice(s, "What does it mean?", s.japanese, true, reading = s.reading, options = opts, optionsJapanese = false, answer = i)
    }
}

/**
 * One run through [items]. A wrong answer sends the item to the back of the queue until it is
 * answered right; only the first answer per item is passed to [grade] (null in lessons).
 */
class Session(items: List<Item>, private val quiz: Quiz, random: Random, private val grade: ((Item, Boolean) -> Unit)?) {
    private val queue = ArrayDeque(items.shuffled(random))
    private val missed = HashSet<String>()
    val total = items.size
    var finished = 0
        private set
    var rightFirstTime = 0
        private set
    var current: Question? = queue.removeFirstOrNull()?.let(quiz::question)
        private set

    fun answer(correct: Boolean) {
        val item = current?.item ?: return
        if (item.key !in missed) {
            grade?.invoke(item, correct)
            if (correct) rightFirstTime++
        }
        if (correct) finished++ else { missed += item.key; queue.addLast(item) }
    }

    fun next() {
        current = queue.removeFirstOrNull()?.let(quiz::question)
    }
}
