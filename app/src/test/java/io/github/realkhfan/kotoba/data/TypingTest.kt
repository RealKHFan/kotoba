package io.github.realkhfan.kotoba.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class TypingTest {
    @Test fun romajiToKana() {
        assertEquals("たべる", Romaji.toKana("taberu"))
        assertEquals("こんにちわ", Romaji.toKana("konnichiwa"))
        assertEquals("がっこう", Romaji.toKana("gakkou"))
        assertEquals("まっちゃ", Romaji.toKana("matcha"))
        assertEquals("しゃしん", Romaji.toKana("shashin", final = true))
        assertEquals("しゃしん", Romaji.toKana("syasin", final = true))
        assertEquals("きょう", Romaji.toKana("kyou"))
        assertEquals("こーひー", Romaji.toKana("ko-hi-"))
        assertEquals("ぱーてぃー", Romaji.toKana("pa-thi-"))
        assertEquals("ほん", Romaji.toKana("honn"))
        assertEquals("ほn", Romaji.toKana("hon"))
        assertEquals("ほん", Romaji.toKana("hon", final = true))
        assertEquals("きんえん", Romaji.toKana("kin'en", final = true))
        assertEquals("かんじ", Romaji.toKana("kanji"))
        assertEquals("おんな", Romaji.toKana("onna"))
        assertEquals("きy", Romaji.toKana("kiy"))
        assertEquals("日本ご", Romaji.toKana("日本go"))
        assertEquals("を", Romaji.toKana("wo"))
        assertEquals("ぁ", Romaji.toKana("xa"))
    }

    @Test fun typedAnswers() {
        val word = testLibrary.words.first { it.written == "食べる" }
        val q = Typed(word, "", "to eat", false, kana = true, accepted = listOf(word.reading, word.written))
        assertTrue(q.isRight("taberu"))
        assertTrue(q.isRight("たべる"))
        assertTrue(q.isRight("食べる"))
        assertTrue(q.isRight(" TABERU "))
        assertFalse(q.isRight("tabero"))
        assertFalse(q.isRight(""))
        val coffee = Typed(word, "", "coffee", false, kana = true, accepted = listOf("コーヒー"))
        assertTrue(coffee.isRight("ko-hi-"))
        assertTrue(coffee.isRight("koohii"))
        assertFalse(coffee.isRight("kohi"))
        assertEquals("きゃあ", Romaji.spellLong("きゃー"))
        val shi = testLibrary.hiragana.first { it.char == "し" }
        val kanaQ = Typed(shi, "", "し", true, kana = false, accepted = listOf(shi.romaji, shi.char))
        assertTrue(kanaQ.isRight("shi"))
        assertTrue(kanaQ.isRight("si"))
        assertFalse(kanaQ.isRight("su"))
    }

    @Test fun answerModeDecidesQuestionKind() {
        val words = testLibrary.words.filter { it.hasKanji }.take(60)
        val choose = Quiz(testLibrary, Random(3), Settings(answers = AnswerMode.CHOICE))
        assertTrue(words.none { choose.question(it) is Typed })
        val type = Quiz(testLibrary, Random(3), Settings(answers = AnswerMode.TYPE))
        val kinds = words.map { type.question(it) }
        assertTrue(kinds.any { it is Typed })
        // Meaning questions have no typed form, so they stay multiple choice.
        assertTrue(kinds.filterIsInstance<Choice>().all { it.ask == "What does it mean?" })
        // Only kana-only grammar answers can be typed.
        testLibrary.grammar.forEach { g ->
            val q = type.question(g)
            if (q is Typed) assertTrue(q.answer.all { it in 'ぁ'..'ゖ' || it in 'ァ'..'ヺ' || it == 'ー' })
        }
    }

    @Test fun readingsCanBeHidden() {
        val words = testLibrary.words.filter { it.hasKanji }
        val quiz = Quiz(testLibrary, Random(5), Settings(answers = AnswerMode.CHOICE, quizReadings = false))
        words.map { quiz.question(it) }.filterIsInstance<Choice>().forEach { assertEquals(null, it.reading) }
    }

    @Test fun lessonSizeFollowsSettings() {
        val s = Settings(wordsPerLesson = 15, itemsPerLesson = 3)
        assertEquals(15, s.lessonSize(Section.WORDS))
        assertEquals(3, s.lessonSize(Section.KANJI))
        assertEquals(1, s.lessonSize(Section.GRAMMAR))
        assertEquals(3, testLibrary.nextLesson(Section.KANJI, 3) { false }.size)
    }

    @Test fun audioKeys() {
        assertEquals(16, Audio.key("あ").length)
        assertFalse(Audio.word("箸", "はし") == Audio.word("橋", "はし"))
        assertEquals("明日手紙を書きます。", Audio.filled("明日手紙を＿＿。(書く)", "書きます"))
    }
}
