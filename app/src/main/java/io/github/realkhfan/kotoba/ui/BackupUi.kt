package io.github.realkhfan.kotoba.ui

import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import io.github.realkhfan.kotoba.data.Backup
import io.github.realkhfan.kotoba.data.Found
import io.github.realkhfan.kotoba.data.Progress
import io.github.realkhfan.kotoba.data.Settings
import io.github.realkhfan.kotoba.data.SettingsStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.DateFormat
import java.time.LocalDate
import java.util.Date

fun dateText(ms: Long): String = DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT).format(Date(ms))

class BackupActions(val choose: () -> Unit, val restore: () -> Unit, val exportFile: () -> Unit, val importFile: () -> Unit)

/**
 * Picking a folder and restoring from it, and exporting to or importing from a single file. A folder that
 * already holds a different backup (a reinstall, a new phone) is only used after the user says which
 * progress to keep, so a backup is never overwritten without asking; an import also asks first.
 */
@Composable
fun rememberBackupActions(backup: Backup, progress: Progress, settings: SettingsStore?): BackupActions {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    // A backup waiting for an answer. tree null: restoring from the folder already in use.
    var offer by remember { mutableStateOf<Pair<Uri?, Found>?>(null) }
    fun toast(text: String) = Toast.makeText(context, text, Toast.LENGTH_LONG).show()
    fun restore(found: Found) {
        progress.replaceAll(found.states, found.days)
        if (found.settings.isNotEmpty()) settings?.let { it.replace(Settings.from(found.settings, it.value)) }
    }
    fun start(tree: Uri, found: Found?) = scope.launch {
        if (!withContext(Dispatchers.IO) { backup.use(tree) }) {
            toast("Kotoba can't save in that folder. Please choose another one.")
            return@launch
        }
        if (found != null) restore(found) else progress.copyNow()
    }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { tree ->
        if (tree != null) scope.launch {
            val found = withContext(Dispatchers.IO) { backup.read(tree) }
            if (found == null || found.states.isEmpty() || found.states == progress.snapshot()) start(tree, null) else offer = tree to found
        }
    }
    val saver = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/plain")) { file ->
        if (file != null) scope.launch {
            val text = progress.text().let { t -> settings?.let { Backup.contents(t, it.value) } ?: t }
            val ok = withContext(Dispatchers.IO) { backup.export(file, text) }
            toast(if (ok) "Exported ${progress.learnedCount} learned items and your settings." else "Kotoba can't save a file there. Please choose another place.")
        }
    }
    val opener = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { file ->
        if (file != null) scope.launch {
            val found = withContext(Dispatchers.IO) { backup.readFile(file) }
            when {
                found == null -> toast("That file isn't a Kotoba backup.")
                found.states.isEmpty() -> toast("There is no progress in that file.")
                else -> offer = null to found
            }
        }
    }
    offer?.let { (tree, found) ->
        val picking = tree != null
        val saved = if (found.savedAt > 0) ", saved ${dateText(found.savedAt)}" else ""
        AlertDialog(
            onDismissRequest = { offer = null },
            title = { Text("Restore your progress?") },
            text = {
                Text(
                    "“${found.name}” has a Kotoba backup with ${found.states.size} learned items$saved. " +
                        "This phone has ${progress.learnedCount}.\n\n" +
                        if (picking) "Restore puts the backup's progress and settings on this phone. Keep puts this phone's progress into the backup." else "Restoring replaces this phone's progress and settings with the backup.",
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    offer = null
                    if (tree != null) start(tree, found) else restore(found)
                }) { Text("Restore") }
            },
            dismissButton = {
                TextButton(onClick = {
                    offer = null
                    if (tree != null) start(tree, null)
                }) { Text(if (picking) "Keep this phone's" else "Cancel") }
            },
        )
    }
    return BackupActions(
        choose = { picker.launch(backup.folder) },
        restore = {
            val tree = backup.folder
            if (tree != null) scope.launch {
                val found = withContext(Dispatchers.IO) { backup.read(tree) }
                if (found == null || found.states.isEmpty()) toast("There is no backup in this folder yet.")
                else offer = null to found
            }
        },
        exportFile = {
            if (progress.learnedCount == 0) toast("Nothing to export yet. Learn a lesson first.")
            else saver.launch("kotoba-progress-${LocalDate.now()}.txt")
        },
        // Also unknown types: some providers don't know a .txt is text.
        importFile = { opener.launch(arrayOf("text/plain", "application/octet-stream")) },
    )
}

/** Asked once on start until a folder is chosen or the user says not now. */
@Composable
fun BackupQuestion(backup: Backup, progress: Progress, settings: SettingsStore?) {
    val actions = rememberBackupActions(backup, progress, settings)
    AlertDialog(
        onDismissRequest = {},
        title = { Text("Keep a backup?") },
        text = {
            Text(
                "Choose a folder, and Kotoba saves your progress there after every lesson and review, " +
                    "as ${Backup.FILE_NAME}. Back that folder up the way you like, for example with Syncthing or by copying it to a computer.\n\n" +
                    "If the folder already has a backup, you can restore it.",
            )
        },
        confirmButton = { TextButton(onClick = actions.choose) { Text("Choose folder") } },
        dismissButton = { TextButton(onClick = backup::notNow) { Text("Not now") } },
    )
}

@Composable
fun BackupSettings(backup: Backup, progress: Progress, settings: SettingsStore?) {
    val actions = rememberBackupActions(backup, progress, settings)
    val folder = backup.folder
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    if (folder == null) {
        Text(
            "Save your progress to a folder you choose, after every change, so you can back it up yourself " +
                "(for example with Syncthing or by copying it to a computer).",
            style = MaterialTheme.typography.bodyMedium,
        )
        Button(onClick = actions.choose, modifier = Modifier.padding(top = 8.dp)) { Text("Choose folder") }
    } else {
        Text("Saving your progress and settings to “${backup.folderName}” as ${Backup.FILE_NAME} after every change.", style = MaterialTheme.typography.bodyMedium)
        Text(
            when {
                backup.failing -> "The last backup failed. The folder may be gone or Kotoba lost access to it. Choose it again."
                backup.savedAt > 0 -> "Last saved ${dateText(backup.savedAt)}."
                else -> "Saved after your next lesson or review."
            },
            style = MaterialTheme.typography.bodyMedium,
            color = if (backup.failing) MaterialTheme.colorScheme.error else muted,
            modifier = Modifier.padding(top = 4.dp),
        )
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 8.dp)) {
            OutlinedButton(onClick = actions.choose) { Text("Change folder") }
            OutlinedButton(onClick = actions.restore) { Text("Restore") }
            TextButton(onClick = backup::stop) { Text("Stop") }
        }
    }
    Text(
        "Export saves your progress and settings once, as a file wherever you like. Import puts them back from such a file, " +
            "for example on a new phone.",
        style = MaterialTheme.typography.bodyMedium,
        modifier = Modifier.padding(top = 16.dp),
    )
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 8.dp)) {
        OutlinedButton(onClick = actions.exportFile) { Text("Export") }
        OutlinedButton(onClick = actions.importFile) { Text("Import") }
    }
}
