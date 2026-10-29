package io.github.realkhfan.kotoba.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class SrsTest {
    private val hour = 3_600_000L

    @Test fun levelsGoUpAndDown() {
        val start = Srs.learned(0)
        assertEquals(SrsState(1, 4 * hour), start)
        val up = Srs.answer(start, true, 10 * hour)
        assertEquals(SrsState(2, 18 * hour), up)
        assertEquals(1, Srs.answer(up, false, 0).level)
        assertEquals(4, Srs.answer(SrsState(6, 0), false, 0).level)
        assertEquals(Srs.MAX_LEVEL, Srs.answer(SrsState(Srs.MAX_LEVEL, 0), true, 0).level)
    }

    @Test fun stages() {
        assertEquals(Stage.NEW, Srs.stage(null))
        assertEquals(Stage.LEARNING, Srs.stage(SrsState(4, 0)))
        assertEquals(Stage.KNOWN, Srs.stage(SrsState(5, 0)))
        assertEquals(Stage.MASTERED, Srs.stage(SrsState(8, 0)))
    }

    @Test fun encodeDecodeRoundTrip() {
        val states = mapOf("w:食べる|たべる" to SrsState(3, 123L), "h:あ" to SrsState(9, 456L))
        assertEquals(states, Progress.decode(Progress.encode(states)))
    }

    @Test fun decodeSkipsDamagedLines() {
        val text = "# kotoba progress 1\nh:あ\t2\t100\nbroken line\nh:い\t99\t5\nh:う\tx\t5\nh:え\t1\t7\n"
        assertEquals(mapOf("h:あ" to SrsState(2, 100), "h:え" to SrsState(1, 7)), Progress.decode(text))
    }

    @get:Rule val tmp = TemporaryFolder()

    @Test fun progressIsSavedAndLoaded() {
        val file = File(tmp.root, "progress.tsv")
        val items = testLibrary.hiragana.take(3)
        Progress(file) { it.run() }.apply {
            learn(items, 1000)
            answer(items[0], true, 2000)
        }
        val reloaded = Progress(file) { it.run() }.apply { load() }
        assertEquals(2, reloaded[items[0].key]?.level)
        assertEquals(1, reloaded[items[1].key]?.level)
        assertNull(reloaded[testLibrary.hiragana[5].key])
        assertTrue(File(tmp.root, "progress.tsv.tmp").exists().not())
    }

    @Test fun statsAndDue() {
        val p = Progress(null)
        val items = testLibrary.hiragana.take(4)
        p.learn(items.take(2), 0)
        p.answer(items[0], true, 0) // level 2, due in 8 h
        val stats = p.stats(items, 5 * hour)
        assertEquals(Stats(4, 2, 1, 0), stats)
        assertEquals(listOf(items[1]), p.due(items, 5 * hour))
        assertEquals(8 * hour, p.nextDue(items, 5 * hour))
    }
}
