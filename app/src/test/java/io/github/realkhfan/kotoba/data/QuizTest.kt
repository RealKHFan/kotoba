package io.github.realkhfan.kotoba.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class QuizTest {
    private val lib = testLibrary

    @Test fun everyItemGetsAFairQuestion() {
        val quiz = Quiz(lib, Random(1))
        repeat(3) {
            lib.all.forEach { item ->
                when (val q = quiz.question(item)) {
                    is Choice -> {
                        assertEquals("${item.key} ${q.options}", 4, q.options.toSet().size)
                        assertTrue(q.answer in q.options.indices)
                        if (item is Word && q.ask == "What does it mean?") {
                            q.options.filterIndexed { i, _ -> i != q.answer }.forEach { assertFalse("$item / $it", sameMeaning(it, item.meaning)) }
                        }
                    }
                    is Typed -> assertTrue("${item.key} ${q.answer}", q.isRight(q.answer))
                    is Write -> assertTrue(lib.strokesOf(q.char) != null && q.char.length == 1)
                    is Order -> {
                        assertEquals(item, q.item)
                        assertTrue(q.isRight(q.tiles.indices.sortedBy { q.item.chunks.indexOf(q.tiles[it]) }) || q.item.chunks.toSet().size < q.item.chunks.size)
                    }
                }
            }
        }
    }

    @Test fun meaningsThatShareASenseClash() {
        assertTrue(sameMeaning("why", "why; how come"))
        assertTrue(sameMeaning("this evening; tonight", "tonight"))
        assertFalse(sameMeaning("hot (weather)", "hot (to the touch)"))
    }

    @Test fun wrongAnswersComeBackAndAreGradedOnce() {
        val graded = mutableListOf<Pair<String, Boolean>>()
        val items = lib.hiragana.take(2)
        val session = Session(items, Quiz(lib, Random(2)), Random(2)) { item, right -> graded += item.key to right }
        val first = session.current!!.item
        session.answer(false); session.next()
        val second = session.current!!.item
        session.answer(true); session.next()
        assertEquals(first, session.current!!.item) // the missed one again
        session.answer(true); session.next()
        assertEquals(null, session.current)
        assertEquals(listOf(first.key to false, second.key to true), graded)
        assertEquals(2, session.finished)
        assertEquals(1, session.rightFirstTime)
    }

    @Test fun aMissedItemComesBackAsTheSameQuestion() {
        repeat(20) { seed ->
            val session = Session(lib.words.take(1), Quiz(lib, Random(seed)), Random(seed), null)
            val first = session.current!!
            session.answer(false); session.next()
            val again = session.current!!
            assertEquals(first::class, again::class)
            when (first) {
                is Choice -> { again as Choice; assertEquals(first.ask, again.ask); assertEquals(first.prompt, again.prompt); assertEquals(first.listen, again.listen)
                    assertEquals(first.options[first.answer], again.options[again.answer]); assertEquals(first.options.toSet(), again.options.toSet()) }
                else -> assertEquals(first, again)
            }
        }
    }

    @Test fun aWrongTypedAnswerCanBeOverruled() {
        val graded = mutableListOf<Boolean>()
        val session = Session(lib.words.take(1), Quiz(lib, Random(4)), Random(4)) { _, right -> graded += right }
        session.answer(false)
        session.overrule()
        session.next()
        assertEquals(listOf(true), graded)
        assertEquals(null, session.current) // counted as right, so it doesn't come back
        session.commit()
        assertEquals(1, graded.size) // counted once
    }

    @Test fun typedMeaningsForgiveSmallThings() {
        assertTrue(Meaning.matches("eat", "to eat"))
        assertTrue(Meaning.matches("To Eat!", "to eat"))
        assertTrue(Meaning.matches("husband", "(my) husband"))
        assertTrue(Meaning.matches("my husband", "(my) husband"))
        assertTrue(Meaning.matches("juvenile", "boy; juvenile"))
        assertTrue(Meaning.matches("beatiful", "beautiful"))
        assertTrue(Meaning.matches("im sorry", "I'm sorry"))
        assertTrue(Meaning.matches("Sorry", "excuse me; I'm sorry"))
        assertTrue(Meaning.matches("I am sorry", "excuse me; I'm sorry"))
        assertTrue(Meaning.matches("surprised", "to be surprised"))
        assertFalse(Meaning.matches("excuse", "excuse me; I'm sorry"))
        assertFalse(Meaning.matches("car", "cat"))
        assertFalse(Meaning.matches("drink", "to eat"))
        assertFalse(Meaning.matches("", "to eat"))
    }

    @Test fun typeModeTypesEverything() {
        val quiz = Quiz(lib, Random(9), Settings(answers = AnswerMode.TYPE))
        repeat(2) {
            lib.all.forEach { item ->
                val q = quiz.question(item)
                assertTrue("${item.key} gave ${q::class.simpleName}", q is Typed || q is Write)
                if (q is Typed) assertTrue("${item.key} ${q.answer}", q.isRight(q.answer))
            }
        }
        // Grammar answers with kanji can be typed as their reading.
        val g = lib.grammar.first { it.id == "potential" }
        val typed = Quiz(lib, Random(1), Settings(answers = AnswerMode.TYPE, writing = false, listening = false))
        val kiru = (0..200).map { typed.question(g) as Typed }.first { "着られます" in it.accepted }
        assertTrue(kiru.isRight("kiraremasu"))
        assertTrue(kiru.isRight("着られます"))
    }

    @Test fun listeningAndWritingFollowTheSettings() {
        val off = Quiz(lib, Random(5), Settings(listening = false, writing = false))
        lib.all.forEach { assertTrue(off.question(it).let { q -> q.listen == null && q !is Write }) }
        val on = Quiz(lib, Random(5))
        val qs = (lib.kanji + lib.words + lib.hiragana).flatMap { item -> List(3) { on.question(item) } }
        assertTrue(qs.any { it is Write })
        assertTrue(qs.any { it.listen != null })
        qs.filter { it.listen != null }.forEach { assertEquals(Audio.of(it.item), it.listen) }
    }
}
