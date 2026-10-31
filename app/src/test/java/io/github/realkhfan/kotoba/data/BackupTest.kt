package io.github.realkhfan.kotoba.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.ByteArrayInputStream
import java.io.File

class BackupTest {
    @get:Rule val tmp = TemporaryFolder()

    @Test fun everySaveIsCopiedButAnEmptyResetIsNot() {
        val copies = mutableListOf<String>()
        val p = Progress(File(tmp.root, "progress.tsv")) { it.run() }.apply { copyTo = { copies += it } }
        val items = testLibrary.hiragana.take(2)
        p.learn(items, 1000)
        p.answer(items[0], true, 2000)
        assertEquals(2, copies.size)
        assertEquals(p.snapshot(), Progress.decode(copies.last()))
        p.reset()
        assertEquals("the backup keeps the progress from before the reset", 2, copies.size)
        p.copyNow()
        assertEquals(2, copies.size)
    }

    @Test fun restoringReplacesEverythingAndIsSaved() {
        val file = File(tmp.root, "progress.tsv")
        val p = Progress(file) { it.run() }
        p.learn(testLibrary.hiragana.take(3), 1000)
        val backup = mapOf(testLibrary.words[0].key to SrsState(5, 99L))
        p.replaceAll(backup)
        assertEquals(backup, p.snapshot())
        assertEquals(backup, Progress(file) { it.run() }.apply { load() }.snapshot())
    }

    @Test fun onlyKotobaProgressFilesAreRead() {
        val text = Progress.encode(mapOf("h:あ" to SrsState(2, 100L)))
        assertEquals(mapOf("h:あ" to SrsState(2, 100L)), Backup.parse(text.toByteArray()))
        assertNull(Backup.parse("shopping list\nh:あ\t2\t100\n".toByteArray()))
        assertNull(Backup.parse(null))
        assertTrue(Backup.parse("# kotoba progress 1\n".toByteArray())!!.isEmpty())
    }

    @Test fun hugeFilesAreNotRead() {
        assertEquals(5, Backup.readLimited(ByteArrayInputStream(ByteArray(5)))!!.size)
        assertNull(Backup.readLimited(ByteArrayInputStream(ByteArray(5 * 1024 * 1024))))
    }
}
