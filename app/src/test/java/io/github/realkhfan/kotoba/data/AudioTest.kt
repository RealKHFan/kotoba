package io.github.realkhfan.kotoba.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/** Every clip the app asks for must exist, so tools/audio.py and data/Audio.kt can't drift apart. */
class AudioTest {
    private val lib = testLibrary
    private val clips = File("src/main/assets/audio").list().orEmpty().map { it.removeSuffix(".ogg") }.toSet()

    @Test fun everyClipTheAppPlaysExists() {
        val wanted = buildList {
            lib.all.mapNotNull(Audio::of).let(::addAll)
            lib.kanji.flatMap { it.examples }.forEach { add(Audio.word(it.japanese, it.reading)) }
            lib.grammar.flatMap { it.examples }.forEach { add(Audio.sentence(it.japanese)) }
            lib.grammar.flatMap { it.questions }.forEach { add(Audio.gap(it.sentence, it.answer)) }
        }.toSet()
        val missing = wanted - clips
        assertTrue("${missing.size} clips missing", missing.isEmpty())
        assertEquals("clips nobody plays", emptySet<String>(), clips - wanted)
    }
}
