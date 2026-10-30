package io.github.realkhfan.kotoba.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import io.github.realkhfan.kotoba.data.Grammar
import io.github.realkhfan.kotoba.data.Item
import io.github.realkhfan.kotoba.data.Kana
import io.github.realkhfan.kotoba.data.KanaChart
import io.github.realkhfan.kotoba.data.Kanji
import io.github.realkhfan.kotoba.data.Library
import io.github.realkhfan.kotoba.data.Progress
import io.github.realkhfan.kotoba.data.Section
import io.github.realkhfan.kotoba.data.Sentence
import io.github.realkhfan.kotoba.data.Srs
import io.github.realkhfan.kotoba.data.Stage
import io.github.realkhfan.kotoba.data.Word

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SectionScreen(section: Section, library: Library, progress: Progress, now: Long, open: (String) -> Unit, back: () -> Unit) {
    val items = library.items(section)
    val stats = progress.stats(items, now)
    val lesson = library.nextLesson(section, LocalSettings.current.lessonSize(section), progress::isLearned)
    var query by rememberSaveable { mutableStateOf("") }
    val isKana = section == Section.HIRAGANA || section == Section.KATAKANA
    Scaffold(topBar = { TopAppBar(title = { Text("${section.title}  ${section.japanese}") }, navigationIcon = { BackButton(back) }) }) { padding ->
        LazyColumn(
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = padding.calculateTopPadding(), bottom = padding.calculateBottomPadding() + 24.dp),
        ) {
            item {
                Column(Modifier.padding(vertical = 8.dp)) {
                    Text(
                        "%,d of %,d learned · %,d mastered".format(stats.learned, stats.total, stats.mastered),
                        style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Row(Modifier.padding(top = 12.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Button(onClick = { open("lesson:${section.name}") }, enabled = lesson.isNotEmpty(), modifier = Modifier.weight(1f)) {
                            Text(if (lesson.isEmpty()) "All learned" else "Learn ${lesson.size} new")
                        }
                        FilledTonalButton(onClick = { open("review:${section.name}") }, enabled = stats.due > 0, modifier = Modifier.weight(1f)) {
                            Text("Review ${stats.due}")
                        }
                    }
                    if (lesson.isNotEmpty()) {
                        val next = lesson.first().let { if (it is Grammar) it.title else groupTitle(it) }
                        Text("Next lesson: $next", style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 6.dp))
                    }
                }
            }
            if (isKana) kanaChart(section, library, progress, open) else {
                item {
                    OutlinedTextField(
                        value = query, onValueChange = { query = it }, singleLine = true,
                        placeholder = { Text(if (section == Section.WORDS) "Search words, readings or meanings" else "Search ${section.title.lowercase()}") },
                        leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                        trailingIcon = {
                            if (query.isNotEmpty()) IconButton(onClick = { query = "" }) { Icon(Icons.Filled.Clear, contentDescription = "Clear search") }
                        },
                        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                    )
                }
                itemList(items, query, progress, open)
            }
        }
    }
}

/** "N5 · Food & drink", or just "N5" for grammar. */
fun groupTitle(item: Item): String = when (item) {
    is Kana -> "${KanaChart.rows(item.section).first { it.name == item.group }.cells.firstNotNullOf { it }.first} row"
    else -> if (item.group == "N${item.level}") item.group else "N${item.level} · ${item.group}"
}

private fun LazyListScope.itemList(items: List<Item>, query: String, progress: Progress, open: (String) -> Unit) {
    val q = query.trim()
    val shown = if (q.isEmpty()) items else items.filter { it.searchText.contains(q, ignoreCase = true) }
    if (shown.isEmpty()) item { Text("Nothing found.", modifier = Modifier.padding(vertical = 24.dp)) }
    shown.groupBy { it.level to it.group }.forEach { (_, group) ->
        item(key = "group:" + group.first().key) { SectionLabel(groupTitle(group.first())) }
        items(group, key = { it.key }) { item -> ItemRow(item, Srs.stage(progress[item.key])) { open("item:${item.key}") } }
    }
}

@Composable
private fun ItemRow(item: Item, stage: Stage, onClick: () -> Unit) {
    val (overline, headline, supporting) = when (item) {
        is Word -> Triple(item.reading.takeIf { item.hasKanji }, item.written, item.meaning)
        is Kanji -> Triple(null, "${item.char}   ${item.meaning}", (item.on + item.kun.map(::kunText)).joinToString("、"))
        is Grammar -> Triple(null, item.title, item.meaning)
        is Sentence -> Triple(null, item.japanese, item.english)
        is Kana -> Triple(null, item.char, item.romaji)
    }
    ListItem(
        overlineContent = overline?.let { { Text(it) } },
        headlineContent = { Text(headline, style = MaterialTheme.typography.titleLarge) },
        supportingContent = { Text(supporting) },
        trailingContent = if (stage == Stage.NEW) null else ({ StageLabel(stage) }),
        modifier = Modifier.clickable(onClick = onClick),
    )
}

private val kanaNotes = mapOf(
    Section.HIRAGANA to listOf(
        "Hiragana writes grammar endings and Japanese words without kanji. As particles, は, へ and を are read \"wa\", \"e\" and \"o\".",
        "A small っ doubles the next sound: きって (kitte). A small ゃ, ゅ or ょ joins the sound before it: きゃ (kya).",
        "゛ and ゜ change the sound: か → が, は → ば → ぱ.",
    ),
    Section.KATAKANA to listOf(
        "Katakana writes foreign words and names, like コーヒー (coffee) and ドイツ (Germany).",
        "ー makes a vowel long: コーヒー is koohii. Small ァ, ィ, ェ and ォ make sounds Japanese doesn't have: ファ (fa), ティ (ti).",
    ),
)

private fun LazyListScope.kanaChart(section: Section, library: Library, progress: Progress, open: (String) -> Unit) {
    item {
        Card(Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                kanaNotes.getValue(section).forEach { Text(it, style = MaterialTheme.typography.bodyMedium) }
            }
        }
    }
    val kana = (if (section == Section.HIRAGANA) library.hiragana else library.katakana).associateBy { it.char }
    KanaChart.rows(section).groupBy { it.part }.forEach { (part, rows) ->
        item(key = "part:${part.name}") { SectionLabel(part.title) }
        items(rows, key = { "row:${it.name}" }) { row ->
            val columns = if (part == KanaChart.Part.COMBOS) 3 else 5
            Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                (row.cells + List(columns - row.cells.size) { null }).forEach { cell ->
                    val item = cell?.let { kana.getValue(it.first) }
                    if (item == null) Spacer(Modifier.weight(1f))
                    else KanaCell(item, Srs.stage(progress[item.key]), Modifier.weight(1f)) { open("item:${item.key}") }
                }
            }
        }
    }
}

@Composable
private fun KanaCell(k: Kana, stage: Stage, modifier: Modifier, onClick: () -> Unit) {
    val color = when (stage) {
        Stage.NEW -> MaterialTheme.colorScheme.surfaceContainerHigh
        Stage.LEARNING -> MaterialTheme.colorScheme.tertiaryContainer
        Stage.KNOWN -> MaterialTheme.colorScheme.secondaryContainer
        Stage.MASTERED -> MaterialTheme.colorScheme.primaryContainer
    }
    Surface(
        onClick = onClick, color = color, shape = RoundedCornerShape(12.dp),
        modifier = modifier.heightIn(min = 64.dp).semantics { stateDescription = stage.label },
    ) {
        Column(Modifier.padding(vertical = 8.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
            Text(k.char, style = MaterialTheme.typography.headlineMedium)
            if (LocalSettings.current.romaji) Text(k.romaji, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
