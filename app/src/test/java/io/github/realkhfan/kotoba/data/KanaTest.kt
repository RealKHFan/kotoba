package io.github.realkhfan.kotoba.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class KanaTest {
    @Test fun romaji() {
        assertEquals("kitte", toRomaji("きって"))
        assertEquals("matcha", toRomaji("まっちゃ"))
        assertEquals("shashin", toRomaji("しゃしん"))
        assertEquals("koohii", toRomaji("コーヒー"))
        assertEquals("fan", toRomaji("ファン"))
        assertEquals("gakkou", toRomaji("がっこう"))
        assertEquals("kyou", toRomaji("きょう"))
        assertEquals("ok?", toRomaji("ok?"))
    }

    @Test fun scripts() {
        assertEquals("カタカナ", toKatakana("かたかな"))
        assertEquals("ひらがな", toHiragana("ヒラガナ"))
        assertEquals("ヴ", toKatakana("ゔ"))
        assertTrue(isKanji('漢'))
        assertTrue(isKanji('々'))
        assertFalse(isKanji('か'))
    }

    @Test fun chartHasGapsWhereTheyBelong() {
        val ya = KanaChart.rows.first { it.name == "ya" }
        assertEquals(listOf("や", null, "ゆ", null, "よ"), ya.cells.map { it?.first })
        assertTrue(KanaChart.rows(Section.HIRAGANA).none { it.part == KanaChart.Part.EXTENDED })
        assertEquals("フィ", KanaChart.rows(Section.KATAKANA).first { it.name == "fa" }.cells[1]!!.first)
    }
}
