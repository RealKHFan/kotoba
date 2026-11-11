package io.github.realkhfan.kotoba.data

import android.net.Uri
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.io.File

/** Export and Import: one file through the content resolver, the way a file picked in Android's picker is used. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [30])
class BackupFileTest {
    @get:Rule val tmp = TemporaryFolder()
    private val app = RuntimeEnvironment.getApplication()
    private val backup = Backup(app, app.getSharedPreferences("backup-file-test", 0))

    @Test fun anExportedFileImportsTheSameProgressAndSettings() {
        val p = Progress(null) { it.run() }
        val items = testLibrary.hiragana.take(3)
        p.learn(items, 1000)
        p.answer(items[0], false, 2000)
        val settings = Settings(wordsPerLesson = Settings.wordChoices.last(), writingHelp = false)
        val file = Uri.fromFile(File(tmp.root, "kotoba-progress-2026-11-11.txt"))
        assertTrue(backup.export(file, Backup.contents(p.text(), settings)))
        val found = backup.readFile(file)!!
        assertEquals(p.snapshot(), found.states)
        assertEquals(1, found.days.size)
        assertEquals(settings, Settings.from(found.settings, Settings()))
    }

    @Test fun anythingElseIsNoBackup() {
        assertNull(backup.readFile(Uri.fromFile(File(tmp.root, "notes.txt").apply { writeText("shopping list") })))
        assertNull(backup.readFile(Uri.fromFile(File(tmp.root, "missing.txt"))))
        val big = File(tmp.root, "big.txt").apply { writeText(Progress.encode(emptyMap()) + "x".repeat(5 * 1024 * 1024)) }
        assertNull("too big to be a progress file", backup.readFile(Uri.fromFile(big)))
        assertFalse(backup.export(Uri.fromFile(File(tmp.root, "no/such/folder/x.txt")), "text"))
    }
}
