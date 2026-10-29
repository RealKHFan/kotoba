package io.github.realkhfan.kotoba.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/** Loads the real asset files, so a broken line in them fails the build. */
val testLibrary: Library by lazy { Library.load { File("src/main/assets/$it").readText() } }

class ContentTest {
    private val lib = testLibrary

    @Test fun everySectionHasContent() {
        assertTrue(lib.words.size > 1000)
        assertTrue(lib.kanji.size > 240)
        assertTrue(lib.sentences.size > 150)
        assertTrue(lib.grammar.size > 100)
        assertEquals(46 + 25 + 33, lib.hiragana.size)
        assertEquals(lib.hiragana.size + 14, lib.katakana.size)
    }

    @Test fun keysAreUnique() {
        val keys = lib.all.map { it.key }
        assertEquals(keys.size, keys.toSet().size)
    }

    @Test fun readingsAreKana() {
        val kana = Regex("[ぁ-ゖァ-ヺー]+")
        lib.words.forEach { assertTrue("${it.written} ${it.reading}", kana.matches(it.reading)) }
        lib.kanji.forEach { k -> k.examples.forEach { assertTrue("${k.char} ${it.reading}", kana.matches(it.reading)) } }
    }

    @Test fun grammarPointsAreComplete() {
        lib.grammar.forEach { g ->
            assertTrue(g.id, g.notes.isNotEmpty() && g.examples.size >= 2 && g.questions.size >= 2)
            g.questions.forEach { q ->
                assertEquals(g.id, 1, Regex("＿＿").findAll(q.sentence).count())
                assertEquals(g.id, 4, (q.wrong + q.answer).toSet().size)
            }
        }
    }

    @Test fun sentenceChunksMatchReadings() {
        lib.sentences.forEach { assertEquals(it.japanese, it.chunks.size, it.readings.size) }
    }

    @Test fun kanjiExamplesUseTheirKanji() {
        lib.kanji.forEach { k -> k.examples.forEach { assertTrue("${k.char} ${it.japanese}", k.char in it.japanese) } }
    }

    @Test fun lessonStaysInOneGroupAndSkipsLearned() {
        val first = lib.nextLesson(Section.WORDS) { false }
        assertEquals(10, first.size)
        assertTrue(first.all { it.group == first[0].group })
        val learned = first.map { it.key }.toSet()
        val second = lib.nextLesson(Section.WORDS) { it in learned }
        assertTrue(second.none { it.key in learned })
        assertEquals(1, lib.nextLesson(Section.GRAMMAR) { false }.size)
        assertEquals(listOf("あ", "い", "う", "え", "お"), lib.nextLesson(Section.HIRAGANA) { false }.map { (it as Kana).char })
        assertTrue(lib.nextLesson(Section.KANJI) { true }.isEmpty())
    }

    @Test fun parserReadsKanaOnlyWordsAndHeaders() {
        val words = Parser.words("# comment\n## 4 Test topic\nこれ|this\n食べる|たべる|to eat\n")
        assertEquals(listOf(Word("これ", "これ", "this", 4, "Test topic"), Word("食べる", "たべる", "to eat", 4, "Test topic")), words)
    }

    @Test fun findsWordsInRunningText() {
        fun word(w: String) = lib.words.first { it.written == w }
        assertTrue(lib.finder(word("食べる"))!!.containsMatchIn("朝ご飯を食べました。"))
        assertTrue(lib.finder(word("勉強する"))!!.containsMatchIn("日本語を勉強しています。"))
        assertTrue(lib.finder(word("高い"))!!.containsMatchIn("高かったです。"))
        assertFalse(lib.finder(word("行く"))!!.containsMatchIn("銀行で働いています。"))
        assertFalse(lib.finder(word("上"))!!.containsMatchIn("母は料理が上手です。"))
        assertFalse(lib.finder(word("花"))!!.containsMatchIn("花見に行きました。"))
        assertEquals(null, lib.finder(word("これ")))
    }

    @Test fun crossReferences() {
        val taberu = lib.words.first { it.written == "食べる" }
        assertEquals(listOf("食"), lib.kanjiIn(taberu).map { it.char })
        assertTrue(lib.examplesFor(taberu).isNotEmpty())
        val a = lib.katakana.first { it.char == "ア" }
        assertTrue(lib.wordsStartingWith(a).all { it.written.startsWith("ア") })
    }
}
