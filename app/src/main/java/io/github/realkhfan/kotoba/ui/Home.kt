package io.github.realkhfan.kotoba.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.ui.Alignment
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.github.realkhfan.kotoba.data.Library
import io.github.realkhfan.kotoba.data.Progress
import io.github.realkhfan.kotoba.data.Section
import io.github.realkhfan.kotoba.data.Stats

fun waitText(ms: Long): String {
    val minutes = (ms + 59_999) / 60_000
    return when {
        minutes < 60 -> "$minutes min"
        minutes < 48 * 60 -> "${(minutes + 59) / 60} h"
        else -> "${(minutes + 1439) / 1440} days"
    }
}

@Composable
fun HomeScreen(library: Library, progress: Progress, now: Long, backupFailing: Boolean = false, open: (String) -> Unit) {
    val all = progress.stats(library.all, now)
    Scaffold { padding ->
        LazyColumn(
            contentPadding = PaddingValues(
                start = 16.dp, end = 16.dp,
                top = padding.calculateTopPadding() + 24.dp, bottom = padding.calculateBottomPadding() + 24.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                Row(Modifier.padding(start = 4.dp, bottom = 8.dp), verticalAlignment = Alignment.Top) {
                    Column(Modifier.weight(1f)) {
                        Text("Kotoba", style = MaterialTheme.typography.headlineLarge)
                        Text("言葉 · a little Japanese every day", style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    IconButton(onClick = { open("settings") }) { Icon(Icons.Filled.Settings, contentDescription = "Settings") }
                }
            }
            if (backupFailing) item {
                Card(
                    onClick = { open("settings") }, modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
                ) {
                    Text(
                        "Backup failed: Kotoba can't reach its backup folder. Tap to choose it again.",
                        style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(16.dp),
                    )
                }
            }
            item { ReviewCard(all, progress.nextDue(library.all, now)?.let { it - now }) { open("review:all") } }
            item {
                Row(Modifier.height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    SectionCard(Section.HIRAGANA, library, progress, now, Modifier.weight(1f).fillMaxHeight(), open)
                    SectionCard(Section.KATAKANA, library, progress, now, Modifier.weight(1f).fillMaxHeight(), open)
                }
            }
            item { SectionCard(Section.WORDS, library, progress, now, Modifier.fillMaxWidth(), open, "Everyday words for JLPT N5 and N4") }
            item { SectionCard(Section.GRAMMAR, library, progress, now, Modifier.fillMaxWidth(), open, "Patterns explained, with examples and gap quizzes") }
            item {
                Row(Modifier.height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    SectionCard(Section.KANJI, library, progress, now, Modifier.weight(1f).fillMaxHeight(), open)
                    SectionCard(Section.SENTENCES, library, progress, now, Modifier.weight(1f).fillMaxHeight(), open)
                }
            }
        }
    }
}

@Composable
private fun ReviewCard(stats: Stats, nextIn: Long?, onReview: () -> Unit) {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer)) {
        Column(Modifier.fillMaxWidth().padding(20.dp)) {
            when {
                stats.due > 0 -> {
                    Text(if (stats.due == 1) "1 review waiting" else "${stats.due} reviews waiting", style = MaterialTheme.typography.titleLarge)
                    Text("Each right answer moves an item up a level.", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(top = 4.dp))
                    Button(onClick = onReview, modifier = Modifier.padding(top = 16.dp)) { Text("Start reviews") }
                }
                nextIn != null -> {
                    Text("All caught up", style = MaterialTheme.typography.titleLarge)
                    Text("Next review in ${waitText(nextIn)}. Time for a new lesson?", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(top = 4.dp))
                }
                else -> {
                    Text("Start with a lesson", style = MaterialTheme.typography.titleLarge)
                    Text("Pick a section below. What you learn comes back here for review.", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(top = 4.dp))
                }
            }
        }
    }
}

@Composable
private fun SectionCard(
    section: Section,
    library: Library,
    progress: Progress,
    now: Long,
    modifier: Modifier,
    open: (String) -> Unit,
    description: String? = null,
) {
    val stats = progress.stats(library.items(section), now)
    val container = when (section) {
        Section.WORDS -> MaterialTheme.colorScheme.primaryContainer
        Section.GRAMMAR -> MaterialTheme.colorScheme.secondaryContainer
        else -> MaterialTheme.colorScheme.surfaceContainerHigh
    }
    Card(onClick = { open("section:${section.name}") }, modifier = modifier, colors = CardDefaults.cardColors(containerColor = container)) {
        Column(Modifier.padding(16.dp)) {
            Text(section.japanese, style = if (description != null) MaterialTheme.typography.headlineMedium else MaterialTheme.typography.titleLarge)
            Text(section.title, style = MaterialTheme.typography.titleMedium)
            description?.let { Text(it, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(top = 4.dp)) }
            Text(
                "%,d / %,d learned".format(stats.learned, stats.total) + if (stats.due > 0) " · ${stats.due} due" else "",
                style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 12.dp, bottom = 6.dp),
            )
            LinearProgressIndicator(
                progress = { if (stats.total == 0) 0f else stats.learned.toFloat() / stats.total },
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}
