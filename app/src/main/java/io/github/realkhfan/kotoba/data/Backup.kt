package io.github.realkhfan.kotoba.data

import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.net.Uri
import android.provider.DocumentsContract
import android.provider.DocumentsContract.Document
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.edit
import androidx.core.net.toUri
import java.io.ByteArrayOutputStream
import java.io.InputStream

/** A backup found in a folder: what it holds and when it was written. */
data class Found(val states: Map<String, SrsState>, val savedAt: Long, val folderName: String)

/**
 * Keeps a copy of the progress file in a folder the user picked with the system picker (no storage
 * permission needed), rewritten after every change, so they can back that folder up however they like.
 * Reads and writes touch storage: call them off the main thread.
 */
class Backup(private val context: Context, private val prefs: SharedPreferences) {
    /** The picked folder (a document tree), or null. */
    var folder: Uri? by mutableStateOf(prefs.getString(FOLDER, null)?.toUri())
        private set
    var folderName by mutableStateOf(prefs.getString(NAME, null).orEmpty())
        private set
    var savedAt by mutableLongStateOf(prefs.getLong(SAVED, 0L))
        private set

    /** True when the last copy failed: the folder was deleted, the card removed, or access was lost. */
    var failing by mutableStateOf(false)
        private set

    /** The user has chosen a folder or said "Not now" to the question on first start. */
    var asked by mutableStateOf(prefs.getBoolean(ASKED, false))
        private set

    @Volatile private var file: Uri? = null

    init {
        // Access to a folder doesn't survive a reinstall or a move to a new phone (where Android backup may
        // bring the setting back): forget the folder then, so the app asks for it again.
        val f = folder
        if (f != null && context.contentResolver.persistedUriPermissions.none { it.uri == f && it.isWritePermission }) {
            folder = null
            asked = false
            prefs.edit { remove(FOLDER); remove(NAME); putBoolean(ASKED, false) }
        }
    }

    fun notNow() {
        asked = true
        prefs.edit { putBoolean(ASKED, true) }
    }

    /** The backup in [tree], or null if there is none or it isn't a Kotoba progress file. */
    fun read(tree: Uri): Found? = try {
        findFile(tree)?.let { (uri, modified) ->
            context.contentResolver.openInputStream(uri)?.use { parse(readLimited(it)) }?.let { Found(it, modified, nameOf(tree)) }
        }
    } catch (e: Exception) { // a provider can fail in many ways; there just is no backup to offer then
        null
    }

    /** Starts copying into [tree] and keeps access to it across restarts. False if the folder can't be used. */
    fun use(tree: Uri): Boolean {
        val flags = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
        try {
            context.contentResolver.takePersistableUriPermission(tree, flags)
        } catch (e: SecurityException) {
            return false // the provider doesn't let apps keep access; the caller says so
        }
        folder?.takeIf { it != tree }?.let { release(it) }
        val name = nameOf(tree)
        file = null
        folder = tree
        folderName = name
        asked = true
        failing = false
        prefs.edit { putString(FOLDER, tree.toString()); putString(NAME, name); putBoolean(ASKED, true) }
        return true
    }

    /** Stops copying. The file already in the folder stays there. */
    fun stop() {
        folder?.let { release(it) }
        folder = null
        file = null
        failing = false
        prefs.edit { remove(FOLDER); remove(NAME) }
    }

    /** Writes [text] as the backup file. Called on the progress writer thread after each save. */
    fun write(text: String) {
        val tree = folder ?: return
        // A cached file may have been deleted by the user; look it up again once before giving up.
        val ok = writeTo(tree, text, cached = true) || writeTo(tree, text, cached = false)
        failing = !ok
        if (ok) {
            savedAt = System.currentTimeMillis()
            prefs.edit { putLong(SAVED, savedAt) }
        }
    }

    private fun writeTo(tree: Uri, text: String, cached: Boolean): Boolean = try {
        val target = file.takeIf { cached } ?: findFile(tree)?.first ?: DocumentsContract.createDocument(
            context.contentResolver, DocumentsContract.buildDocumentUriUsingTree(tree, DocumentsContract.getTreeDocumentId(tree)),
            "text/plain", FILE_NAME,
        )
        // shortcut: the copy is rewritten in place (most providers can't rename atomically); a crash mid-write leaves
        // a short copy until the next save, while the phone's own file stays whole. Revisit if copies get large.
        context.contentResolver.openOutputStream(target!!, "wt")!!.use { it.write(text.toByteArray()) }
        file = target
        true
    } catch (e: Exception) { // SecurityException, FileNotFoundException, IllegalArgumentException, NPE from a missing provider ...
        file = null
        false
    }

    private fun findFile(tree: Uri): Pair<Uri, Long>? {
        val children = DocumentsContract.buildChildDocumentsUriUsingTree(tree, DocumentsContract.getTreeDocumentId(tree))
        val columns = arrayOf(Document.COLUMN_DOCUMENT_ID, Document.COLUMN_DISPLAY_NAME, Document.COLUMN_LAST_MODIFIED)
        context.contentResolver.query(children, columns, null, null, null)?.use { c ->
            while (c.moveToNext()) {
                if (c.getString(1) == FILE_NAME) return DocumentsContract.buildDocumentUriUsingTree(tree, c.getString(0)) to c.getLong(2)
            }
        }
        return null
    }

    private fun nameOf(tree: Uri): String = try {
        val doc = DocumentsContract.buildDocumentUriUsingTree(tree, DocumentsContract.getTreeDocumentId(tree))
        context.contentResolver.query(doc, arrayOf(Document.COLUMN_DISPLAY_NAME), null, null, null)
            ?.use { c -> if (c.moveToFirst()) c.getString(0) else null }
    } catch (e: Exception) {
        null
    } ?: "the chosen folder"

    private fun release(tree: Uri) {
        try {
            context.contentResolver.releasePersistableUriPermission(tree, Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
        } catch (e: SecurityException) {
            // Already gone.
        }
    }

    companion object {
        const val FILE_NAME = "kotoba-progress.txt"
        private const val FOLDER = "folder"
        private const val NAME = "folderName"
        private const val SAVED = "savedAt"
        private const val ASKED = "asked"
        private const val LIMIT = 4 * 1024 * 1024 // a full copy is about 100 KB

        /** Reads at most [LIMIT] bytes; null for anything bigger, which can't be a progress file. */
        fun readLimited(input: InputStream): ByteArray? {
            val out = ByteArrayOutputStream()
            val buffer = ByteArray(8192)
            while (true) {
                val n = input.read(buffer)
                if (n < 0) return out.toByteArray()
                if (out.size() + n > LIMIT) return null
                out.write(buffer, 0, n)
            }
        }

        /** The progress in a backup file, or null if it isn't one (it must start with the progress header). */
        fun parse(bytes: ByteArray?): Map<String, SrsState>? {
            val text = bytes?.toString(Charsets.UTF_8) ?: return null
            return if (text.startsWith(Progress.HEADER_PREFIX)) Progress.decode(text) else null
        }
    }
}
