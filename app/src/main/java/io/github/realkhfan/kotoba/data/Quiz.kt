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

/** Put the shuffled chunks of a sentence back in order. */
data class Order(override val item: Sentence, val tiles: List<String>) : Question {
    fun isRight(picked: List<Int>) = picked.joinToString("") { tiles[it] } == item.japanese
}

/** True when two meanings share a sense ("why" and "why; how come"), so one can't be a wrong option for the other. */
fun sameMeaning(a: String, b: String): Boolean {
    fun senses(s: String) = s.lowercase().split(";").map(String::trim).toSet()
    return senses(a).intersect(senses(b)).isNotEmpty()
}

class Quiz(private val library: Library, private val random: Random) {

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

    private fun kana(k: Kana): Choice {
        val pool = library.items(k.section).filterIsInstance<Kana>()
        val clash = { o: Kana -> o.romaji == k.romaji }
        return if (random.nextBoolean()) {
            val (opts, i) = options(k.romaji, wrong(k, pool, Kana::romaji, clash))
            Choice(k, "How do you read it?", k.char, true, options = opts, optionsJapanese = false, answer = i)
        } else {
            val (opts, i) = options(k.char, wrong(k, pool, Kana::char, clash))
            Choice(k, "Which ${k.section.title.lowercase()} is it?", k.romaji, false, options = opts, optionsJapanese = true, answer = i)
        }
    }

    private fun word(w: Word): Choice {
        val clash = { o: Word -> sameMeaning(o.meaning, w.meaning) || o.reading == w.reading || o.written == w.written }
        return when (random.nextInt(if (w.hasKanji) 3 else 2)) {
            0 -> {
                val (opts, i) = options(w.meaning, wrong(w, library.words, Word::meaning, clash))
                Choice(w, "What does it mean?", w.written, true, reading = w.reading.takeIf { w.hasKanji }, options = opts, optionsJapanese = false, answer = i)
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

    private fun kanji(k: Kanji): Choice {
        if (random.nextBoolean()) {
            val (opts, i) = options(k.meaning, wrong(k, library.kanji, Kanji::meaning) { sameMeaning(it.meaning, k.meaning) })
            return Choice(k, "What does it mean?", k.char, true, options = opts, optionsJapanese = false, answer = i)
        }
        val e = k.examples.random(random)
        val wrong = library.kanji.filter { it.key != k.key }.flatMap { it.examples }.map { it.reading }
            .filter { it != e.reading }.distinct().shuffled(random).take(3)
        val (opts, i) = options(e.reading, wrong)
        return Choice(k, "How do you read this word?", e.japanese, true, hint = e.english, options = opts, optionsJapanese = true, answer = i)
    }

    private fun grammar(g: Grammar): Choice {
        val q = g.questions.random(random)
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
