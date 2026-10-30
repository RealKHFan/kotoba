package io.github.realkhfan.kotoba.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class StrokesTest {
    private val lib = testLibrary

    @Test fun everyKanaAndKanjiHasStrokes() {
        lib.kanji.forEach { assertNotNull(it.char, lib.strokesOf(it.char)) }
        (lib.hiragana + lib.katakana).forEach { k -> k.char.forEach { assertNotNull(k.char, lib.strokesOf(it.toString())) } }
        assertEquals(4, lib.strokesOf("日")!!.size)
        assertEquals(16, lib.strokesOf("館")!!.size)
        assertEquals(3, lib.strokesOf("あ")!!.size)
    }

    @Test fun resampleKeepsEnds() {
        val s = listOf(Pt(0f, 0f), Pt(10f, 0f), Pt(10f, 10f))
        val r = Strokes.resample(s, 5)
        assertEquals(5, r.size)
        assertEquals(Pt(0f, 0f), r.first())
        assertEquals(10f, r.last().x, 0.01f)
        assertEquals(10f, r.last().y, 0.01f)
        assertEquals(10f, r[2].x, 0.01f)
    }

    @Test fun matcherAcceptsWobblyStrokesAndRejectsWrongOnes() {
        val random = Random(7)
        // A hand-drawn stroke: shifted a little, bending slowly, with a bit of jitter.
        fun wobble(s: Stroke) = s.mapIndexed { i, p ->
            val bend = 3f * kotlin.math.sin(i / 6f)
            Pt(p.x + 3f + bend + random.nextFloat() * 2f - 1f, p.y - 2f - bend + random.nextFloat() * 2f - 1f)
        }
        for (c in listOf("日", "言", "あ", "書", "ア")) {
            val strokes = lib.strokesOf(c)!!
            strokes.forEachIndexed { i, s ->
                assertTrue("$c stroke ${i + 1} itself", Strokes.matches(s, s))
                assertTrue("$c stroke ${i + 1} wobbly", Strokes.matches(wobble(s), s))
                if (Strokes.length(s) > 25f) assertFalse("$c stroke ${i + 1} backwards", Strokes.matches(s.reversed(), s))
                strokes.forEachIndexed { j, other ->
                    val far = Strokes.resample(other, 8).zip(Strokes.resample(s, 8)).maxOf { (a, b) -> kotlin.math.hypot(a.x - b.x, a.y - b.y) } > 25f
                    if (j != i && far) assertFalse("$c stroke ${j + 1} drawn for ${i + 1}", Strokes.matches(other, s))
                }
            }
        }
    }

    @Test fun aTapCountsForADot() {
        val dot = listOf("が", "文", "心", "六").flatMap { lib.strokesOf(it)!! }.first { Strokes.length(it) < 12f }
        assertTrue(Strokes.matches(listOf(dot[0]), dot))
        assertFalse(Strokes.matches(listOf(Pt(dot[0].x + 40f, dot[0].y + 40f)), dot))
    }
}
