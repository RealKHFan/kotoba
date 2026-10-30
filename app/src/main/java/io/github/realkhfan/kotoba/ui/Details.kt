@file:OptIn(ExperimentalLayoutApi::class)

package io.github.realkhfan.kotoba.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.FilledTonalButton
import io.github.realkhfan.kotoba.data.Audio
import androidx.compose.material3.AssistChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import io.github.realkhfan.kotoba.data.Example
import io.github.realkhfan.kotoba.data.Grammar
import io.github.realkhfan.kotoba.data.Item
import io.github.realkhfan.kotoba.data.Kana
import io.github.realkhfan.kotoba.data.Kanji
import io.github.realkhfan.kotoba.data.Library
import io.github.realkhfan.kotoba.data.Section
import io.github.realkhfan.kotoba.data.Sentence
import io.github.realkhfan.kotoba.data.Stage
import io.github.realkhfan.kotoba.data.Word
import io.github.realkhfan.kotoba.data.toHiragana
import io.github.realkhfan.kotoba.data.toKatakana
import io.github.realkhfan.kotoba.data.toRomaji

@Composable
fun BackButton(onClick: () -> Unit) {
    IconButton(onClick = onClick) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") }
}

@Composable
fun stageColor(stage: Stage): Color = when (stage) {
    Stage.NEW -> MaterialTheme.colorScheme.outlineVariant
    Stage.LEARNING -> MaterialTheme.colorScheme.tertiary
    Stage.KNOWN -> MaterialTheme.colorScheme.secondary
    Stage.MASTERED -> MaterialTheme.colorScheme.primary
}

@Composable
fun StageDot(stage: Stage) {
    Box(
        Modifier.size(12.dp).clip(CircleShape).background(stageColor(stage))
            .semantics { contentDescription = stage.label },
    )
}

/** Shown next to learned items in lists; new items show nothing. */
@Composable
fun StageLabel(stage: Stage) {
    Text(stage.label, style = MaterialTheme.typography.labelMedium, color = stageColor(stage))
}

/** Picks a text size that fits Japanese text of this length. */
@Composable
fun japaneseStyle(text: String): TextStyle = when {
    text.length <= 2 -> MaterialTheme.typography.displayLarge
    text.length <= 5 -> MaterialTheme.typography.displayMedium
    text.length <= 10 -> MaterialTheme.typography.headlineLarge
    else -> MaterialTheme.typography.headlineSmall
}

/** Kun readings mark the okurigana with a dot: た.べる is shown as た(べる). */
fun kunText(r: String) = if ('.' in r) r.replace(".", "(") + ")" else r

@Composable
fun SectionLabel(text: String, modifier: Modifier = Modifier) {
    Text(text, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary, modifier = modifier.padding(top = 20.dp, bottom = 8.dp))
}

@Composable
fun ExampleView(e: Example) {
    Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(e.japanese, style = MaterialTheme.typography.titleMedium)
            Text(e.reading, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(e.english, style = MaterialTheme.typography.bodyMedium)
        }
        SpeakButton(Audio.sentence(e.japanese), "Play the sentence")
    }
}

/** A Japanese heading with its play button on the right. */
@Composable
private fun SpokenRow(key: String?, content: @Composable () -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) { content() }
        SpeakButton(key)
    }
}

/** Stroke order for each kana or kanji in [text], with a button to practice writing it. */
@Composable
private fun StrokeSection(text: String, library: Library, write: ((String) -> Unit)?) {
    val chars = text.map { it.toString() }.filter { library.strokesOf(it) != null }
    if (chars.isEmpty()) return
    val count = chars.sumOf { library.strokesOf(it)!!.size }
    SectionLabel("Stroke order · $count ${if (count == 1) "stroke" else "strokes"}")
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
        chars.forEach { StrokeOrder(it, library.strokesOf(it)!!, Modifier.size(if (chars.size > 1) 110.dp else 150.dp)) }
        if (write != null) {
            FilledTonalButton(onClick = { write(text) }) {
                Icon(Icons.Filled.Edit, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text("Practice")
            }
        }
    }
}

/** Each chunk with its reading on top, wrapping like a sentence. */
@Composable
fun FuriganaSentence(s: Sentence, style: TextStyle = MaterialTheme.typography.headlineSmall) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        s.chunks.forEachIndexed { i, chunk ->
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(s.readings[i], style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(chunk, style = style)
            }
        }
    }
}

/** A short "Japanese — English" line used in quiz feedback. */
fun summary(item: Item): Pair<String, String> = when (item) {
    is Kana -> item.char to item.romaji
    is Word -> (if (item.hasKanji) "${item.written}（${item.reading}）" else item.written) to item.meaning
    is Kanji -> item.char to item.meaning
    is Sentence -> item.japanese to item.english
    is Grammar -> item.title to item.meaning
}

/**
 * The full card for an item, used on its own page and in lessons. [open] is null where links shouldn't
 * navigate; [write] opens writing practice for the given characters.
 */
@Composable
fun ItemDetail(item: Item, library: Library, stage: Stage, open: ((Item) -> Unit)?, write: ((String) -> Unit)? = null) {
    Column(Modifier.fillMaxWidth()) {
        when (item) {
            is Kana -> KanaDetail(item, library, open, write)
            is Word -> WordDetail(item, library, open)
            is Kanji -> KanjiDetail(item, library, open, write)
            is Sentence -> SentenceDetail(item, library, open)
            is Grammar -> GrammarDetail(item)
        }
        Spacer(Modifier.height(16.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            StageDot(stage)
            Spacer(Modifier.width(8.dp))
            Text("${stage.label} · JLPT N${item.level}", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun WordLinks(words: List<Word>, open: ((Item) -> Unit)?) {
    words.forEach { w ->
        val text = if (w.hasKanji) "${w.written}（${w.reading}）" else w.written
        Row(
            Modifier.fillMaxWidth().heightIn(min = 48.dp).then(if (open != null) Modifier.clickable { open(w) } else Modifier).padding(vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(text, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
            Spacer(Modifier.width(12.dp))
            Text(w.meaning, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
        }
    }
}

@Composable
private fun KanaDetail(k: Kana, library: Library, open: ((Item) -> Unit)?, write: ((String) -> Unit)?) {
    SpokenRow(Audio.of(k)) {
        Text(k.char, style = MaterialTheme.typography.displayLarge.copy(fontSize = MaterialTheme.typography.displayLarge.fontSize * 1.6f))
        Text(k.romaji, style = MaterialTheme.typography.headlineMedium, color = MaterialTheme.colorScheme.primary)
    }
    val other = if (k.section == Section.HIRAGANA) "Katakana: ${toKatakana(k.char)}" else "Hiragana: ${toHiragana(k.char)}"
    Text(other, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 8.dp))
    StrokeSection(k.char, library, write)
    val words = library.wordsStartingWith(k).take(5)
    if (words.isNotEmpty()) {
        SectionLabel("Words starting with ${k.char}")
        WordLinks(words, open)
    }
}

@Composable
private fun WordDetail(w: Word, library: Library, open: ((Item) -> Unit)?) {
    SpokenRow(Audio.of(w)) {
        if (w.hasKanji) Text(w.reading, style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(w.written, style = japaneseStyle(w.written))
        if (LocalSettings.current.romaji) Text(toRomaji(w.reading), style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.outline)
    }
    Text(w.meaning, style = MaterialTheme.typography.headlineSmall, modifier = Modifier.padding(top = 12.dp))
    Text(w.group, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 4.dp))
    val kanji = library.kanjiIn(w)
    if (kanji.isNotEmpty()) {
        SectionLabel("Kanji")
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            kanji.forEach { k ->
                val label = "${k.char}  ${k.meaning}"
                if (open != null) AssistChip(onClick = { open(k) }, label = { Text(label) })
                else Surface(shape = RoundedCornerShape(8.dp), color = MaterialTheme.colorScheme.surfaceContainerHigh, modifier = Modifier.padding(vertical = 8.dp)) {
                    Text(label, style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp))
                }
            }
        }
    }
    val examples = library.examplesFor(w).take(4)
    if (examples.isNotEmpty()) {
        SectionLabel("Examples")
        examples.forEach { ExampleView(it) }
    }
}

@Composable
private fun KanjiDetail(k: Kanji, library: Library, open: ((Item) -> Unit)?, write: ((String) -> Unit)?) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Surface(shape = RoundedCornerShape(20.dp), color = MaterialTheme.colorScheme.surfaceContainerHigh) {
            Text(k.char, style = MaterialTheme.typography.displayLarge.copy(fontSize = MaterialTheme.typography.displayLarge.fontSize * 1.5f), modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp))
        }
        Spacer(Modifier.width(20.dp))
        Column {
            Text(k.meaning, style = MaterialTheme.typography.headlineSmall)
            if (k.on.isNotEmpty()) Text("On: ${k.on.joinToString("、")}", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 8.dp))
            if (k.kun.isNotEmpty()) Text("Kun: ${k.kun.joinToString("、", transform = ::kunText)}", style = MaterialTheme.typography.titleMedium)
        }
    }
    StrokeSection(k.char, library, write)
    SectionLabel("Examples")
    k.examples.forEach { e ->
        Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(e.japanese, style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
            Column(Modifier.weight(1.5f)) {
                Text(e.reading, style = MaterialTheme.typography.bodyLarge)
                Text(e.english, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            SpeakButton(Audio.word(e.japanese, e.reading), "Play ${e.japanese}")
        }
    }
    val words = library.wordsWith(k).filter { w -> k.examples.none { it.japanese == w.written } }.take(8)
    if (words.isNotEmpty()) {
        SectionLabel("More words with ${k.char}")
        WordLinks(words, open)
    }
}

@Composable
private fun SentenceDetail(s: Sentence, library: Library, open: ((Item) -> Unit)?) {
    SpokenRow(Audio.of(s)) { FuriganaSentence(s) }
    Text(s.english, style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(top = 16.dp))
    Text(s.group, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 4.dp))
    val words = library.wordsIn(s)
    if (words.isNotEmpty()) {
        SectionLabel("Words")
        WordLinks(words, open)
    }
}

@Composable
private fun GrammarDetail(g: Grammar) {
    Text(g.title, style = MaterialTheme.typography.headlineMedium)
    Text(g.meaning, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 4.dp))
    Surface(color = MaterialTheme.colorScheme.secondaryContainer, shape = RoundedCornerShape(12.dp), modifier = Modifier.fillMaxWidth().padding(top = 16.dp)) {
        Text(g.form, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSecondaryContainer, modifier = Modifier.padding(16.dp))
    }
    g.notes.forEach { Text(it, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.padding(top = 12.dp)) }
    SectionLabel("Examples")
    g.examples.forEachIndexed { i, e ->
        if (i > 0) HorizontalDivider()
        ExampleView(e)
    }
}
