@file:OptIn(ExperimentalMaterial3Api::class)

package io.github.realkhfan.kotoba.ui

import android.content.ActivityNotFoundException
import android.content.Intent
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import io.github.realkhfan.kotoba.data.AnswerMode
import io.github.realkhfan.kotoba.data.Progress
import io.github.realkhfan.kotoba.data.Settings
import io.github.realkhfan.kotoba.data.SettingsStore
import io.github.realkhfan.kotoba.data.ThemeMode

@Composable
private fun <T> Choices(title: String, note: String?, options: List<T>, selected: T, label: (T) -> String, onPick: (T) -> Unit) {
    Column(Modifier.fillMaxWidth().padding(vertical = 10.dp)) {
        Text(title, style = MaterialTheme.typography.titleMedium)
        note?.let { Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth().padding(top = 8.dp)) {
            options.forEachIndexed { i, option ->
                SegmentedButton(option == selected, { onPick(option) }, SegmentedButtonDefaults.itemShape(i, options.size)) { Text(label(option)) }
            }
        }
    }
}

@Composable
private fun Toggle(title: String, note: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        Modifier.fillMaxWidth().heightIn(min = 56.dp).toggleable(checked, role = Role.Switch, onValueChange = onChange).padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Text(note, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Spacer(Modifier.width(16.dp))
        Switch(checked, onCheckedChange = null)
    }
}

@Composable
fun SettingsScreen(store: SettingsStore, progress: Progress, version: String, back: () -> Unit) {
    val s = store.value
    var confirmReset by rememberSaveable { mutableStateOf(false) }
    val context = LocalContext.current
    Scaffold(topBar = { TopAppBar(title = { Text("Settings") }, navigationIcon = { BackButton(back) }) }) { padding ->
        Column(Modifier.padding(padding).fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 8.dp)) {
            SectionLabel("Look")
            Choices("Theme", null, ThemeMode.entries, s.theme, ThemeMode::label) { v -> store.update { it.copy(theme = v) } }
            Toggle("Show romaji", "Latin letters under words and in the kana charts", s.romaji) { v -> store.update { it.copy(romaji = v) } }

            SectionLabel("Lessons")
            Choices("New words per lesson", null, Settings.wordChoices, s.wordsPerLesson, Int::toString) { v -> store.update { it.copy(wordsPerLesson = v) } }
            Choices("New kanji and sentences per lesson", null, Settings.itemChoices, s.itemsPerLesson, Int::toString) { v -> store.update { it.copy(itemsPerLesson = v) } }

            SectionLabel("Quizzes")
            Choices(
                "Answers", "Type answers in romaji or with a Japanese keyboard; meanings are always chosen.",
                AnswerMode.entries, s.answers, AnswerMode::label,
            ) { v -> store.update { it.copy(answers = v) } }
            Toggle("Readings above kanji", "Show how a word is read when you're asked for its meaning", s.quizReadings) { v -> store.update { it.copy(quizReadings = v) } }
            Choices("Reviews per session", null, Settings.reviewChoices, s.reviewsPerSession, { if (it == 0) "All" else it.toString() }) { v ->
                store.update { it.copy(reviewsPerSession = v) }
            }

            SectionLabel("Sound")
            Toggle("Play automatically", "Say new words in lessons, and the answer after each question", s.autoplay) { v -> store.update { it.copy(autoplay = v) } }

            SectionLabel("Progress")
            Text("Kotoba keeps your progress in one small file on this phone. Android backup can include it.", style = MaterialTheme.typography.bodyMedium)
            OutlinedButton(onClick = { confirmReset = true }, modifier = Modifier.padding(top = 8.dp)) { Text("Reset progress") }

            SectionLabel("About")
            Text("Kotoba $version", style = MaterialTheme.typography.titleMedium)
            Text(
                "Free and open source (GPL-3.0). No internet, no tracking, no Google.\n\n" +
                    "Stroke order: KanjiVG by Ulrich Apel and contributors, CC BY-SA 3.0, converted to point lists for Kotoba.\n" +
                    "Voice: words and sentences made with Style-Bert-VITS2 and a JVNV voice model, CC BY-SA 4.0; " +
                    "kana with Open JTalk and its HTS voice, Modified BSD.\n" +
                    "Readings checked with JMdict and KANJIDIC2 (EDRDG, CC BY-SA 4.0).\n" +
                    "Icon: 言 from Noto Serif CJK JP, SIL Open Font License.",
                style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(top = 4.dp),
            )
            TextButton(onClick = {
                try {
                    context.startActivity(Intent(Intent.ACTION_VIEW, "https://github.com/RealKHFan/kotoba".toUri()))
                } catch (e: ActivityNotFoundException) {
                    // No browser installed; nothing else to do.
                }
            }) { Text("Source code on GitHub") }
            HorizontalDivider(Modifier.padding(vertical = 12.dp))
        }
    }
    if (confirmReset) {
        AlertDialog(
            onDismissRequest = { confirmReset = false },
            title = { Text("Reset progress?") },
            text = { Text("Everything you have learned and all review times are deleted. This can't be undone.") },
            confirmButton = { TextButton(onClick = { progress.reset(); confirmReset = false }) { Text("Reset") } },
            dismissButton = { TextButton(onClick = { confirmReset = false }) { Text("Cancel") } },
        )
    }
}
