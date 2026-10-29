@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)

package io.github.realkhfan.kotoba.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import io.github.realkhfan.kotoba.data.Choice
import io.github.realkhfan.kotoba.data.Grammar
import io.github.realkhfan.kotoba.data.Item
import io.github.realkhfan.kotoba.data.Kanji
import io.github.realkhfan.kotoba.data.Library
import io.github.realkhfan.kotoba.data.Order
import io.github.realkhfan.kotoba.data.Progress
import io.github.realkhfan.kotoba.data.Question
import io.github.realkhfan.kotoba.data.Quiz
import io.github.realkhfan.kotoba.data.Section
import io.github.realkhfan.kotoba.data.Session
import io.github.realkhfan.kotoba.data.Stage
import kotlin.random.Random

private fun noun(section: Section) = when (section) {
    Section.HIRAGANA, Section.KATAKANA, Section.KANJI, Section.GRAMMAR -> section.title.lowercase()
    Section.WORDS -> "words"
    Section.SENTENCES -> "sentences"
}

@Composable
private fun CloseButton(onClick: () -> Unit) {
    IconButton(onClick = onClick) { Icon(Icons.Filled.Close, contentDescription = "Close") }
}

@Composable
fun LessonScreen(section: Section, library: Library, progress: Progress, close: () -> Unit) {
    val items = remember { library.nextLesson(section, progress::isLearned) }
    if (items.isEmpty()) {
        LaunchedEffect(Unit) { close() }
        return
    }
    var page by rememberSaveable { mutableIntStateOf(0) }
    var quizzing by rememberSaveable { mutableStateOf(false) }
    if (quizzing) {
        QuizScreen(
            title = "Lesson quiz", items = items, library = library, grade = null, close = close,
            onFinished = { progress.learn(items, System.currentTimeMillis()) },
            doneText = "You learned ${items.size} new ${noun(section)}. They come back for review in 4 hours.",
        )
        return
    }
    val last = page == items.lastIndex
    Scaffold(
        topBar = { TopAppBar(title = { Text("New ${noun(section)}") }, navigationIcon = { CloseButton(close) }) },
        bottomBar = {
            Row(Modifier.fillMaxWidth().navigationBarsPadding().padding(16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedButton(onClick = { page-- }, enabled = page > 0, modifier = Modifier.weight(1f)) { Text("Back") }
                Button(onClick = { if (last) quizzing = true else page++ }, modifier = Modifier.weight(1f)) { Text(if (last) "Start quiz" else "Next") }
            }
        },
    ) { padding ->
        key(page) {
            Column(Modifier.padding(padding).fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 12.dp)) {
                Text("${page + 1} of ${items.size}", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.height(12.dp))
                ItemDetail(items[page], library, Stage.NEW, open = null)
            }
        }
    }
}

/** [section] null reviews everything that is due. */
@Composable
fun ReviewScreen(section: Section?, library: Library, progress: Progress, now: Long, close: () -> Unit) {
    val items = remember { progress.due(section?.let(library::items) ?: library.all, now) }
    if (items.isEmpty()) {
        LaunchedEffect(Unit) { close() }
        return
    }
    QuizScreen(
        title = section?.let { "Review ${it.title.lowercase()}" } ?: "Reviews", items = items, library = library,
        grade = { item, right -> progress.answer(item, right, System.currentTimeMillis()) },
        close = close, onFinished = {}, doneText = "Items you got right wait longer before they come back. Misses come back sooner.",
    )
}

@Composable
fun QuizScreen(
    title: String,
    items: List<Item>,
    library: Library,
    grade: ((Item, Boolean) -> Unit)?,
    close: () -> Unit,
    onFinished: () -> Unit,
    doneText: String,
    random: Random = Random.Default,
) {
    val session = remember { Session(items, Quiz(library, random), random, grade) }
    var question by remember { mutableStateOf(session.current) }
    var result by remember { mutableStateOf<Boolean?>(null) }
    Scaffold(topBar = { TopAppBar(title = { Text(title) }, navigationIcon = { CloseButton(close) }) }) { padding ->
        Column(Modifier.padding(padding).fillMaxSize()) {
            LinearProgressIndicator(progress = { session.finished.toFloat() / session.total }, modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp))
            val q = question
            if (q == null) {
                Done(session, doneText, close)
                return@Column
            }
            Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(20.dp)) {
                key(q) {
                    when (q) {
                        is Choice -> ChoiceView(q, result) { right -> session.answer(right); result = right }
                        is Order -> OrderView(q, result) { right -> session.answer(right); result = right }
                    }
                }
                result?.let { Feedback(q, it) }
            }
            if (result != null) {
                Button(
                    onClick = {
                        session.next()
                        question = session.current
                        result = null
                        if (question == null) onFinished()
                    },
                    modifier = Modifier.fillMaxWidth().navigationBarsPadding().padding(16.dp),
                ) { Text("Continue") }
            }
        }
    }
}

@Composable
private fun Done(session: Session, text: String, close: () -> Unit) {
    Column(Modifier.fillMaxSize().padding(24.dp), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
        Text("おつかれさま！", style = MaterialTheme.typography.headlineLarge)
        Text("Well done", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(
            "${session.rightFirstTime} of ${session.total} right the first time.",
            style = MaterialTheme.typography.titleLarge, textAlign = TextAlign.Center, modifier = Modifier.padding(top = 24.dp),
        )
        Text(text, style = MaterialTheme.typography.bodyLarge, textAlign = TextAlign.Center, modifier = Modifier.padding(top = 8.dp))
        Button(onClick = close, modifier = Modifier.padding(top = 32.dp)) { Text("Done") }
    }
}

@Composable
private fun AskLabel(text: String) {
    Text(text.uppercase(), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
}

@Composable
private fun ChoiceView(q: Choice, result: Boolean?, onAnswer: (Boolean) -> Unit) {
    var picked by remember { mutableStateOf<Int?>(null) }
    AskLabel(q.ask)
    Spacer(Modifier.height(16.dp))
    q.reading?.let { Text(it, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurfaceVariant) }
    Text(q.prompt, style = if (q.promptJapanese) japaneseStyle(q.prompt) else MaterialTheme.typography.headlineMedium)
    q.hint?.let { Text(it, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 8.dp)) }
    Spacer(Modifier.height(24.dp))
    q.options.forEachIndexed { i, option ->
        val state = when {
            result == null -> null
            i == q.answer -> true
            i == picked -> false
            else -> null
        }
        OptionButton(option, q.optionsJapanese, state, enabled = result == null) {
            picked = i
            onAnswer(i == q.answer)
        }
        Spacer(Modifier.height(10.dp))
    }
}

/** [state]: true = the right answer, false = the wrong pick, null = neither. */
@Composable
private fun OptionButton(text: String, japanese: Boolean, state: Boolean?, enabled: Boolean, onClick: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    val (container, content) = when (state) {
        true -> colors.primaryContainer to colors.onPrimaryContainer
        false -> colors.errorContainer to colors.onErrorContainer
        null -> colors.surface to colors.onSurface
    }
    Surface(
        onClick = onClick, enabled = enabled, shape = RoundedCornerShape(14.dp), color = container, contentColor = content,
        border = BorderStroke(1.dp, if (state == null) colors.outlineVariant else container),
        modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp).semantics {
            state?.let { stateDescription = if (it) "Right answer" else "Wrong answer" }
        },
    ) {
        Row(Modifier.padding(horizontal = 16.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(
                text, modifier = Modifier.weight(1f),
                style = if (japanese) MaterialTheme.typography.titleLarge else MaterialTheme.typography.bodyLarge,
            )
            if (state != null) {
                Spacer(Modifier.width(8.dp))
                Icon(if (state) Icons.Filled.Check else Icons.Filled.Close, contentDescription = null, modifier = Modifier.size(20.dp))
            }
        }
    }
}

@Composable
private fun OrderView(q: Order, result: Boolean?, onAnswer: (Boolean) -> Unit) {
    var picked by remember { mutableStateOf(listOf<Int>()) }
    AskLabel("Put the words in order")
    Spacer(Modifier.height(16.dp))
    Text(q.item.english, style = MaterialTheme.typography.headlineSmall)
    Spacer(Modifier.height(20.dp))
    Surface(
        shape = RoundedCornerShape(14.dp), border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        modifier = Modifier.fillMaxWidth().heightIn(min = 88.dp),
    ) {
        FlowRow(Modifier.padding(12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            picked.forEach { i -> Tile(q.tiles[i], enabled = result == null) { picked = picked - i } }
        }
    }
    Spacer(Modifier.height(20.dp))
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        q.tiles.forEachIndexed { i, tile ->
            // A used tile keeps its place, so the others don't jump around.
            if (i in picked) Tile(tile, enabled = false, faded = true) {} else Tile(tile, enabled = result == null) { picked = picked + i }
        }
    }
    if (result == null) {
        Button(onClick = { onAnswer(q.isRight(picked)) }, enabled = picked.size == q.tiles.size, modifier = Modifier.padding(top = 24.dp).fillMaxWidth()) {
            Text("Check")
        }
    }
}

@Composable
private fun Tile(text: String, enabled: Boolean, faded: Boolean = false, onClick: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    Surface(
        onClick = onClick, enabled = enabled, shape = RoundedCornerShape(10.dp),
        color = if (faded) colors.surfaceContainerHighest else colors.surfaceContainerLow,
        contentColor = if (faded) colors.surfaceContainerHighest else colors.onSurface,
        border = BorderStroke(1.dp, colors.outlineVariant),
        // A used tile is only a placeholder, so TalkBack skips it.
        modifier = Modifier.heightIn(min = 48.dp).then(if (faded) Modifier.clearAndSetSemantics {} else Modifier),
    ) {
        Text(text, style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp))
    }
}

@Composable
private fun Feedback(q: Question, right: Boolean) {
    val colors = MaterialTheme.colorScheme
    Surface(
        color = if (right) colors.primaryContainer else colors.errorContainer,
        contentColor = if (right) colors.onPrimaryContainer else colors.onErrorContainer,
        shape = RoundedCornerShape(14.dp), modifier = Modifier.fillMaxWidth().padding(top = 20.dp),
    ) {
        Column(Modifier.padding(16.dp)) {
            Text(if (right) "Right!" else "Not quite", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(8.dp))
            val item = q.item
            when {
                q is Order -> {
                    FuriganaSentence(q.item, MaterialTheme.typography.titleLarge)
                    Text(q.item.english, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.padding(top = 8.dp))
                }
                q is Choice && item is Grammar -> {
                    Text(q.prompt.replace("＿＿", q.options[q.answer]), style = MaterialTheme.typography.titleLarge)
                    q.hint?.let { Text(it, style = MaterialTheme.typography.bodyLarge) }
                    Text("${item.title} · ${item.meaning}", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(top = 8.dp))
                }
                q is Choice && item is Kanji && q.prompt != item.char -> {
                    Text("${q.prompt}（${q.options[q.answer]}）", style = MaterialTheme.typography.titleLarge)
                    q.hint?.let { Text(it, style = MaterialTheme.typography.bodyLarge) }
                    Text("${item.char} · ${item.meaning}", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(top = 8.dp))
                }
                else -> {
                    val (ja, en) = summary(item)
                    Text(ja, style = MaterialTheme.typography.titleLarge)
                    Text(en, style = MaterialTheme.typography.bodyLarge)
                }
            }
        }
    }
}
