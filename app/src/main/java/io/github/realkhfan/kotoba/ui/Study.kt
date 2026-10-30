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
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.OutlinedTextField
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.imePadding
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation
import io.github.realkhfan.kotoba.data.Audio
import io.github.realkhfan.kotoba.data.Romaji
import io.github.realkhfan.kotoba.data.Typed
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
fun LessonScreen(section: Section, library: Library, progress: Progress, open: (String) -> Unit, close: () -> Unit) {
    val settings = LocalSettings.current
    val speaker = LocalSpeaker.current
    val items = remember { library.nextLesson(section, settings.lessonSize(section), progress::isLearned) }
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
    LaunchedEffect(page) { if (settings.autoplay) speaker.play(Audio.of(items[page])) }
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
                ItemDetail(items[page], library, Stage.NEW, open = null, write = { open("write:$it") })
            }
        }
    }
}

/** [section] null reviews everything that is due. */
@Composable
fun ReviewScreen(section: Section?, library: Library, progress: Progress, now: Long, close: () -> Unit) {
    val limit = LocalSettings.current.reviewsPerSession
    // Oldest reviews first when the session is limited.
    val items = remember {
        progress.due(section?.let(library::items) ?: library.all, now).sortedBy { progress[it.key]?.due }
            .let { if (limit > 0) it.take(limit) else it }
    }
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
    val settings = LocalSettings.current
    val speaker = LocalSpeaker.current
    val session = remember { Session(items, Quiz(library, random, settings), random, grade) }
    var question by remember { mutableStateOf(session.current) }
    var result by remember { mutableStateOf<Boolean?>(null) }
    var typed by remember { mutableStateOf("") }
    // Counts questions, so a missed item asked again right away starts with a fresh view (an equal question would keep its old state).
    var asked by remember { mutableIntStateOf(0) }
    fun answered(q: Question, right: Boolean) {
        session.answer(right)
        result = right
        if (settings.autoplay) speaker.play(audioFor(q))
    }
    Scaffold(topBar = { TopAppBar(title = { Text(title) }, navigationIcon = { CloseButton(close) }) }) { padding ->
        // The keyboard pushes the content up instead of covering the answer field.
        Column(Modifier.padding(padding).consumeWindowInsets(padding).imePadding().fillMaxSize()) {
            LinearProgressIndicator(progress = { session.finished.toFloat() / session.total }, modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp))
            val q = question
            if (q == null) {
                Done(session, doneText, close)
                return@Column
            }
            Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(20.dp)) {
                key(asked) {
                    when (q) {
                        is Choice -> ChoiceView(q, result) { right -> answered(q, right) }
                        is Order -> OrderView(q, result) { right -> answered(q, right) }
                        is Typed -> TypedView(q, result) { right, text -> typed = text; answered(q, right) }
                    }
                }
                result?.let { Feedback(q, it, typed) }
            }
            if (result != null) {
                Button(
                    onClick = {
                        session.next()
                        asked++
                        question = session.current
                        result = null
                        if (question == null) onFinished()
                    },
                    modifier = Modifier.fillMaxWidth().padding(16.dp),
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

private fun Question.prompt() = when (this) {
    is Choice -> prompt
    is Typed -> prompt
    is Order -> item.english
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

/** Shows typed romaji as kana while the field keeps the letters, so the keyboard isn't disturbed. */
private object KanaTransformation : VisualTransformation {
    override fun filter(text: AnnotatedString): TransformedText {
        val raw = text.text
        val out = Romaji.toKana(raw)
        return TransformedText(AnnotatedString(out), object : OffsetMapping {
            override fun originalToTransformed(offset: Int) = Romaji.toKana(raw.take(offset)).length.coerceIn(0, out.length)
            override fun transformedToOriginal(offset: Int) =
                if (offset >= out.length) raw.length else (0..raw.length).first { Romaji.toKana(raw.take(it)).length >= offset }
        })
    }
}

@Composable
private fun TypedView(q: Typed, result: Boolean?, onAnswer: (Boolean, String) -> Unit) {
    var text by rememberSaveable { mutableStateOf("") }
    val focus = remember { FocusRequester() }
    val keyboard = LocalSoftwareKeyboardController.current
    LaunchedEffect(Unit) { focus.requestFocus() }
    fun submit() {
        if (text.isBlank() || result != null) return
        keyboard?.hide()
        onAnswer(q.isRight(text), text)
    }
    AskLabel(q.ask)
    Spacer(Modifier.height(16.dp))
    q.reading?.let { Text(it, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurfaceVariant) }
    Text(q.prompt, style = if (q.promptJapanese) japaneseStyle(q.prompt) else MaterialTheme.typography.headlineMedium)
    q.hint?.let { Text(it, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 8.dp)) }
    Spacer(Modifier.height(24.dp))
    OutlinedTextField(
        value = text, onValueChange = { text = it }, enabled = result == null, singleLine = true, isError = result == false,
        textStyle = MaterialTheme.typography.headlineSmall,
        placeholder = { Text(if (q.kana) "Type in romaji or kana" else "Type the romaji") },
        visualTransformation = if (q.kana) KanaTransformation else VisualTransformation.None,
        keyboardOptions = KeyboardOptions(
            capitalization = KeyboardCapitalization.None, autoCorrectEnabled = false,
            keyboardType = if (q.kana) KeyboardType.Text else KeyboardType.Ascii, imeAction = ImeAction.Done,
        ),
        keyboardActions = KeyboardActions(onDone = { submit() }),
        modifier = Modifier.fillMaxWidth().focusRequester(focus),
    )
    if (result == null) {
        Button(onClick = { submit() }, enabled = text.isNotBlank(), modifier = Modifier.padding(top = 16.dp).fillMaxWidth()) { Text("Check") }
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

/** The clip that goes with a question's answer. */
fun audioFor(q: Question): String? = when (val item = q.item) {
    is Grammar -> when (q) {
        is Choice -> Audio.gap(q.prompt, q.options[q.answer])
        is Typed -> Audio.gap(q.prompt, q.answer)
        is Order -> null
    }
    is Kanji -> when {
        q is Choice && q.prompt != item.char -> Audio.word(q.prompt, q.options[q.answer])
        q is Typed -> Audio.word(q.prompt, q.answer)
        else -> null
    }
    else -> Audio.of(item)
}

@Composable
private fun Feedback(q: Question, right: Boolean, typed: String) {
    val colors = MaterialTheme.colorScheme
    Surface(
        color = if (right) colors.primaryContainer else colors.errorContainer,
        contentColor = if (right) colors.onPrimaryContainer else colors.onErrorContainer,
        shape = RoundedCornerShape(14.dp), modifier = Modifier.fillMaxWidth().padding(top = 20.dp),
    ) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(if (right) "Right!" else "Not quite", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                SpeakButton(audioFor(q))
            }
            if (q is Typed && !right && typed.isNotBlank()) {
                Text("You typed ${if (q.kana) Romaji.toKana(typed, final = true) else typed}", style = MaterialTheme.typography.bodyMedium)
            }
            Spacer(Modifier.height(8.dp))
            val item = q.item
            when {
                q is Order -> {
                    FuriganaSentence(q.item, MaterialTheme.typography.titleLarge)
                    Text(q.item.english, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.padding(top = 8.dp))
                }
                item is Grammar && q !is Order -> {
                    val answer = if (q is Choice) q.options[q.answer] else (q as Typed).answer
                    val hint = if (q is Choice) q.hint else (q as Typed).hint
                    Text(Audio.filled(q.prompt(), answer), style = MaterialTheme.typography.titleLarge)
                    hint?.let { Text(it, style = MaterialTheme.typography.bodyLarge) }
                    Text("${item.title} · ${item.meaning}", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(top = 8.dp))
                }
                item is Kanji && q.prompt() != item.char -> {
                    val answer = if (q is Choice) q.options[q.answer] else (q as Typed).answer
                    Text("${q.prompt()}（$answer）", style = MaterialTheme.typography.titleLarge)
                    (if (q is Choice) q.hint else (q as Typed).hint)?.let { Text(it, style = MaterialTheme.typography.bodyLarge) }
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
