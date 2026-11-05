package io.github.realkhfan.kotoba.data

import kotlin.random.Random

sealed interface Question {
    val item: Item
    /** A clip to play as the question; the prompt stays hidden until the question is answered. */
    val listen: String? get() = null
}

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
    override val listen: String? = null,
) : Question

/**
 * Type the answer. With [kana], romaji turns into kana while typing. Any of [accepted] counts,
 * typed as it is or as romaji (so "shi", "si" and し are all right for し). With [english], the
 * answer is an English meaning and small typos are forgiven.
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
    val english: Boolean = false,
    override val listen: String? = null,
) : Question {
    val answer get() = accepted.first()

    fun isRight(input: String): Boolean {
        val raw = input.trim()
        if (raw.isEmpty()) return false
        if (english) return accepted.any { Meaning.matches(raw, it) }
        val asKana = Romaji.spellLong(Romaji.normalize(Romaji.toKana(raw, final = true)))
        return accepted.any { it.equals(raw, ignoreCase = true) || Romaji.spellLong(Romaji.normalize(it)) == asKana }
    }
}

/** Put the shuffled chunks of a sentence back in order. */
data class Order(override val item: Sentence, val tiles: List<String>) : Question {
    fun isRight(picked: List<Int>) = picked.joinToString("") { tiles[it] } == item.japanese
}

/** Write [char] from memory on the writing pad, from its meaning or sound ([prompt]) and [hint]. */
data class Write(override val item: Item, val char: String, val ask: String, val prompt: String, val hint: String? = null) : Question

/** True when two meanings share a sense ("why" and "why; how come"), so one can't be a wrong option for the other. */
fun sameMeaning(a: String, b: String): Boolean {
    fun senses(s: String) = s.lowercase().split(";").map(String::trim).toSet()
    return senses(a).intersect(senses(b)).isNotEmpty()
}

/** Checks a typed English meaning against a meaning like "to go out with; to keep company with". */
object Meaning {
    private val fillers = setOf("to", "a", "an", "the")

    /** Lower case, letters and digits only, without a leading "to", "a" or "the". "a.m." is "am" and "10,000" is "10000". */
    fun normalize(s: String): String =
        s.lowercase().replace(".", "").replace(Regex("(?<=\\d),(?=\\d)"), "")
            .replace(Regex("[^a-z0-9' ]"), " ").replace("'", "").split(' ').filter(String::isNotEmpty)
            .dropWhile { it in fillers }.joinToString(" ")

    /** Every sense on its own, with and without the part in brackets: "(my) husband" gives "husband" and "my husband". */
    fun answers(meaning: String): Set<String> = (meaning.split(';', ',').flatMap { sense ->
        listOf(normalize(sense.replace(Regex("\\(.*?\\)"), " ")), normalize(sense))
    } + normalize(meaning)).filter(String::isNotEmpty).toSet()

    // Small words that don't change a meaning: "I'm sorry" is "sorry", "to be surprised" is "surprised", "(my) husband" is "husband".
    private val small = setOf("to", "a", "an", "the", "be", "is", "am", "are", "i", "im", "it", "its", "my", "your", "one", "ones", "someone", "someones", "something")

    private fun core(s: String) = s.split(' ').filter { it !in small }.joinToString(" ")

    // British spellings that differ from American ones in more than the endings handled in [spelling].
    private val british = mapOf(
        "grey" to "gray", "practise" to "practice", "practised" to "practiced", "practising" to "practicing",
        "licence" to "license", "defence" to "defense", "offence" to "offense", "programme" to "program",
        "programmes" to "programs", "cheque" to "check", "tyre" to "tire", "tyres" to "tires", "pyjamas" to "pajamas",
        "aeroplane" to "airplane", "aeroplanes" to "airplanes", "mum" to "mom", "mums" to "moms", "moustache" to "mustache",
        "storey" to "story", "storeys" to "stories", "plough" to "plow", "mould" to "mold", "enquire" to "inquire",
        "enquiry" to "inquiry", "enquiries" to "inquiries", "ageing" to "aging", "judgement" to "judgment",
        "jewellery" to "jewelry", "learnt" to "learned", "burnt" to "burned", "spelt" to "spelled", "dreamt" to "dreamed",
        "spoilt" to "spoiled", "maths" to "math", "whilst" to "while", "kerb" to "curb", "sceptical" to "skeptical",
    )

    // Plurals that mean something else than the singular, so "glasses" isn't "glass" and "goods" isn't "good".
    private val plurals = setOf(
        "news", "glasses", "clothes", "goods", "times", "means", "customs", "manners", "arms", "works", "contents",
        "savings", "papers", "sweets", "looks", "terms", "besides", "spectacles", "remains", "belongings", "spirits",
    )

    private val ize = Regex(
        "^(organ|real|recogn|apolog|memor|special|critic|emphas|util|econom|civil|minim|maxim|summar|categor|priorit|" +
            "final|standard|familiar|author|visual|local|custom|modern|sympath|harmon|global|normal|symbol|central|" +
            "commercial|capital|hospital|individual|neutral|natural|legal|ideal|immun|optim|personal|character)is(e|ed|es|ing|ation|ations|er|ers)$",
    )
    private val doubled = Regex(
        "^(travel|cancel|label|model|fuel|counsel|marvel|signal|quarrel|level|total|dial|equal|rival|tunnel|channel|panel|pencil|jewel)l(ed|er|ers|ing|ous|or|ors)$",
    )
    private val our = Regex("^([a-z]{3,})our(s|ed|ing|ite|ites|able|ably)?$")
    private val tre = Regex("^([a-z]{2,})([tb])re(s|d)?$")
    private val yse = Regex("^([a-z]{2,})yse(s|d)?$")
    private val logue = Regex("^([a-z]+)logue(s)?$")

    /** One word in American spelling: "colour" is "color", "centre" is "center", "organise" is "organize", "travelling" is "traveling". */
    private fun spelling(w: String): String {
        british[w]?.let { return it }
        fun Regex.swap(s: String, to: (List<String>) -> String) = matchEntire(s)?.let { to(it.groupValues) } ?: s
        var s = w
        s = ize.swap(s) { "${it[1]}iz${it[2]}" }
        s = doubled.swap(s) { "${it[1]}${it[2]}" }
        s = our.swap(s) { if (it[1] in setOf("det", "dev", "cont")) s else "${it[1]}or${it[2]}" }
        s = tre.swap(s) { "${it[1]}${it[2]}er${it[3]}" }
        s = yse.swap(s) { "${it[1]}yze${it[2]}" }
        s = logue.swap(s) { "${it[1]}log${it[2]}" }
        return s
    }

    /** One word without a plural ending: "cities" is "city", "boxes" is "box", "dogs" is "dog". */
    private fun singular(w: String): String = when {
        w in plurals || w.length < 4 -> w
        w.endsWith("ies") && w.length > 4 -> w.dropLast(3) + "y"
        Regex("(ch|sh|x|ss|z)es$").containsMatchIn(w) -> w.dropLast(2)
        w.endsWith("s") && !w.endsWith("ss") && !w.endsWith("us") && !w.endsWith("is") -> w.dropLast(1)
        else -> w
    }

    /** The form two answers are compared in: American spelling, no plurals and no spaces, so "co-worker" is "coworkers". */
    private fun fold(s: String) = s.split(' ').joinToString("") { singular(spelling(it)) }

    /** Right when it equals one sense, with or without small words; one typo is forgiven from five letters on, two from nine. */
    fun matches(input: String, meaning: String): Boolean {
        val typed = normalize(input)
        if (typed.isEmpty()) return false
        // A typo is forgiven, but not one that turns "goods" into "good".
        fun plural(a: String, b: String) = a in plurals && b == a.dropLast(1)
        fun close(a: String, b: String) = a.isNotEmpty() &&
            (a == b || (a.length >= 5 && distance(a, b) <= (if (a.length >= 9) 2 else 1) && !plural(a, b) && !plural(b, a)))
        val word = fold(typed)
        val small = fold(core(typed))
        return answers(meaning).any { close(fold(it), word) || close(fold(core(it)), small) }
    }

    private fun distance(a: String, b: String): Int {
        val d = IntArray(b.length + 1) { it }
        for (i in 1..a.length) {
            var prev = d[0]
            d[0] = i
            for (j in 1..b.length) {
                val keep = d[j]
                d[j] = minOf(d[j] + 1, d[j - 1] + 1, prev + if (a[i - 1] == b[j - 1]) 0 else 1)
                prev = keep
            }
        }
        return d[b.length]
    }
}

class Quiz(private val library: Library, private val random: Random, private val settings: Settings = Settings()) {

    private fun typing() = when (settings.answers) {
        AnswerMode.CHOICE -> false
        AnswerMode.TYPE -> true
        AnswerMode.MIX -> random.nextBoolean()
    }

    fun question(item: Item): Question = writing(item) ?: listening(item) ?: when (item) {
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

    /** A kanji or a single kana, written from memory, about one question in three when writing is on. */
    private fun writing(item: Item): Write? {
        if (!settings.writing) return null
        val char = when (item) {
            is Kanji -> item.char
            is Kana -> item.char.takeIf { it.length == 1 }
            else -> null
        } ?: return null
        if (library.strokesOf(char) == null || random.nextInt(3) != 0) return null
        return when (item) {
            is Kanji -> Write(item, char, "Write the kanji", item.meaning, (item.on + item.kun).take(4).joinToString("、") { it.replace(".", "") })
            else -> Write(item, char, "Write it in ${item.section.title.lowercase()}", (item as Kana).romaji)
        }
    }

    /** Hear a word, kana or sentence and say what it was, about one question in four when listening is on. */
    private fun listening(item: Item): Question? {
        if (!settings.listening) return null
        val clip = Audio.of(item) ?: return null
        if (random.nextInt(4) != 0) return null
        val type = typing()
        return when (item) {
            is Kana -> if (type) {
                Typed(item, "Type what you hear", item.char, true, kana = false, accepted = listOf(item.romaji, item.char), listen = clip)
            } else {
                val (opts, i) = options(item.char, wrong(item, library.items(item.section).filterIsInstance<Kana>(), Kana::char) { it.romaji == item.romaji })
                Choice(item, "What do you hear?", item.char, true, options = opts, optionsJapanese = true, answer = i, listen = clip)
            }
            is Word -> if (type) {
                Typed(item, "Type what you hear", item.written, true, reading = item.reading.takeIf { item.hasKanji }, kana = true,
                    accepted = listOf(item.reading, item.written), listen = clip)
            } else {
                val clash = { o: Word -> sameMeaning(o.meaning, item.meaning) || o.reading == item.reading }
                val (opts, i) = options(item.meaning, wrong(item, library.words, Word::meaning, clash))
                Choice(item, "What does it mean?", item.written, true, reading = item.reading.takeIf { item.hasKanji },
                    options = opts, optionsJapanese = false, answer = i, listen = clip)
            }
            is Sentence -> dictation(item).takeIf { type } ?: run {
                val (opts, i) = options(item.english, wrong(item, library.sentences, Sentence::english) { it.english == item.english })
                Choice(item, "What does it mean?", item.japanese, true, reading = item.reading, options = opts, optionsJapanese = false, answer = i, listen = clip)
            }
            else -> null
        }
    }

    private fun dictation(s: Sentence) = Typed(
        s, "Type what you hear", s.japanese, true, reading = s.reading, hint = s.english, kana = true,
        accepted = listOf(s.reading, s.japanese), listen = Audio.of(s),
    )

    private fun kana(k: Kana): Question {
        val pool = library.items(k.section).filterIsInstance<Kana>()
        val clash = { o: Kana -> o.romaji == k.romaji }
        // Typing the kana for a romaji would be done by the romaji converter, so typed kana questions always ask for the romaji.
        if (typing()) return Typed(k, "Type the romaji", k.char, true, kana = false, accepted = listOf(k.romaji, k.char))
        return if (random.nextBoolean()) {
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
        val reading = w.reading.takeIf { w.hasKanji && settings.quizReadings }
        if (typing()) return when (kind) {
            0 -> Typed(w, "What does it mean?", w.written, true, reading = reading, kana = false, accepted = library.meanings(w), english = true)
            1 -> Typed(w, "Type it in Japanese", w.meaning, false, kana = true, accepted = listOf(w.reading, w.written))
            else -> Typed(w, "Type the reading", w.written, true, kana = true, accepted = listOf(w.reading))
        }
        return when (kind) {
            0 -> {
                val (opts, i) = options(w.meaning, wrong(w, library.words, Word::meaning, clash))
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
        val type = typing()
        if (random.nextBoolean()) {
            if (type) return Typed(k, "What does it mean?", k.char, true, kana = false, accepted = library.meanings(k), english = true)
            val (opts, i) = options(k.meaning, wrong(k, library.kanji, Kanji::meaning) { sameMeaning(it.meaning, k.meaning) })
            return Choice(k, "What does it mean?", k.char, true, options = opts, optionsJapanese = false, answer = i)
        }
        val e = k.examples.random(random)
        if (type) return Typed(k, "Type the reading of this word", e.japanese, true, hint = e.english, kana = true, accepted = listOf(e.reading))
        val wrong = library.kanji.filter { it.key != k.key }.flatMap { it.examples }.map { it.reading }
            .filter { it != e.reading }.distinct().shuffled(random).take(3)
        val (opts, i) = options(e.reading, wrong)
        return Choice(k, "How do you read this word?", e.japanese, true, hint = e.english, options = opts, optionsJapanese = true, answer = i)
    }

    private fun grammar(g: Grammar): Question {
        val q = g.questions.random(random)
        if (typing()) {
            return Typed(g, "Type the missing part", q.sentence, true, hint = q.english, kana = true, accepted = listOfNotNull(q.answer, q.reading))
        }
        val (opts, i) = options(q.answer, q.wrong)
        return Choice(g, "Fill the gap", q.sentence, true, hint = q.english, options = opts, optionsJapanese = true, answer = i)
    }

    private fun sentence(s: Sentence): Question {
        if (typing()) {
            // A translation can't be checked by typing, so typed sentence questions ask for what is heard or how it is read.
            return if (settings.listening && random.nextBoolean()) dictation(s)
            else Typed(s, "Type how it is read", s.japanese, true, hint = s.english, kana = true, accepted = listOf(s.reading))
        }
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
 * answered right, asked the same way again (choices in a new order); the kind of question only
 * changes from one session to the next. Only the first answer per item is passed to [grade] (null in lessons).
 * An answer counts when the next question comes (or [commit]), so a typed answer the app got
 * wrong can still be [overrule]d.
 */
class Session(items: List<Item>, private val quiz: Quiz, private val random: Random, private val grade: ((Item, Boolean) -> Unit)?) {
    private val queue = ArrayDeque(items.shuffled(random))
    private val missed = HashSet<String>()
    private val retry = HashMap<String, Question>()
    private var pending: Boolean? = null
    val total = items.size
    var finished = 0
        private set
    var rightFirstTime = 0
        private set
    var current: Question? = queue.removeFirstOrNull()?.let(quiz::question)
        private set

    fun answer(correct: Boolean) {
        if (current != null) pending = correct
    }

    /** The user says the answer the app marked wrong was right (a meaning in other words, a typo). */
    fun overrule() {
        if (pending == false) pending = true
    }

    /** Counts the answer to the current question, once. */
    fun commit() {
        val item = current?.item ?: return
        val correct = pending ?: return
        pending = null
        if (item.key !in missed) {
            grade?.invoke(item, correct)
            if (correct) rightFirstTime++
        }
        if (correct) {
            finished++
        } else {
            missed += item.key
            current?.let { retry[item.key] = again(it) }
            queue.addLast(item)
        }
    }

    fun next() {
        commit()
        current = queue.removeFirstOrNull()?.let { retry.remove(it.key) ?: quiz.question(it) }
    }

    private fun again(q: Question): Question = when (q) {
        is Choice -> {
            val options = q.options.shuffled(random)
            q.copy(options = options, answer = options.indexOf(q.options[q.answer]))
        }
        is Order -> q.copy(tiles = q.tiles.shuffled(random))
        else -> q
    }
}
