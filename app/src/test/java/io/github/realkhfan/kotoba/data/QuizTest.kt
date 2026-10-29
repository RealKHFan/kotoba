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
}
