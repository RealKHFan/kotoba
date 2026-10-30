@file:OptIn(ExperimentalMaterial3Api::class)

package io.github.realkhfan.kotoba.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke as StrokeStyle
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.realkhfan.kotoba.data.Library
import io.github.realkhfan.kotoba.data.Pt
import io.github.realkhfan.kotoba.data.Stroke
import io.github.realkhfan.kotoba.data.Strokes
import kotlin.math.hypot

private fun DrawScope.guide(color: Color) {
    val dash = PathEffect.dashPathEffect(floatArrayOf(12f, 12f))
    drawLine(color, Offset(size.width / 2, 0f), Offset(size.width / 2, size.height), 2f, pathEffect = dash)
    drawLine(color, Offset(0f, size.height / 2), Offset(size.width, size.height / 2), 2f, pathEffect = dash)
}

/** Draws the first [fraction] of a stroke given in grid units. */
private fun DrawScope.stroke(s: Stroke, color: Color, fraction: Float = 1f, width: Float = 4.2f) {
    val scale = size.minDimension / Strokes.GRID
    val count = (s.size * fraction).toInt().coerceIn(1, s.size)
    if (count == 1) {
        drawCircle(color, width * scale / 2, Offset(s[0].x * scale, s[0].y * scale))
        return
    }
    val path = Path().apply {
        moveTo(s[0].x * scale, s[0].y * scale)
        for (i in 1 until count) lineTo(s[i].x * scale, s[i].y * scale)
    }
    drawPath(path, color, style = StrokeStyle(width * scale, cap = StrokeCap.Round, join = StrokeJoin.Round))
}

/** Where to put a stroke's number: just before its start, against its direction. */
private fun numberSpot(s: Stroke): Pt {
    val a = s.first()
    val b = s[minOf(4, s.lastIndex)]
    val d = hypot(b.x - a.x, b.y - a.y).takeIf { it > 0f } ?: 1f
    return Pt((a.x - (b.x - a.x) / d * 7f).coerceIn(5f, 104f), (a.y - (b.y - a.y) / d * 7f).coerceIn(5f, 104f))
}

/** The character drawn stroke by stroke with numbers. Tap to play it again. */
@Composable
fun StrokeOrder(char: String, strokes: List<Stroke>, modifier: Modifier = Modifier) {
    var run by remember { mutableIntStateOf(0) }
    val progress = remember(strokes) { Animatable(0f) }
    LaunchedEffect(strokes, run) {
        progress.snapTo(0f)
        progress.animateTo(strokes.size.toFloat(), tween(strokes.size * 650, easing = LinearEasing))
    }
    val colors = MaterialTheme.colorScheme
    val measurer = rememberTextMeasurer()
    val numberStyle = TextStyle(fontSize = 11.sp, color = colors.primary)
    Surface(
        onClick = { run++ }, shape = RoundedCornerShape(16.dp), color = colors.surfaceContainerLow,
        modifier = modifier.aspectRatio(1f).semantics { contentDescription = "Stroke order of $char, ${strokes.size} strokes. Tap to play again." },
    ) {
        Canvas(Modifier.fillMaxSize().padding(6.dp)) {
            guide(colors.outlineVariant)
            strokes.forEach { stroke(it, colors.surfaceContainerHighest) }
            val p = progress.value
            val scale = size.minDimension / Strokes.GRID
            strokes.forEachIndexed { i, s ->
                val f = (p - i).coerceIn(0f, 1f)
                if (f > 0f) stroke(s, if (f < 1f) colors.primary else colors.onSurface, f)
            }
            strokes.forEachIndexed { i, s ->
                if (p <= i) return@forEachIndexed
                val spot = numberSpot(s)
                val text = measurer.measure((i + 1).toString(), numberStyle)
                drawText(text, topLeft = Offset(spot.x * scale - text.size.width / 2f, spot.y * scale - text.size.height / 2f))
            }
        }
    }
}

/**
 * Draw the character with a finger. Each stroke is checked against the expected one; a miss
 * shows where it starts, and after three misses the stroke is filled in so you can go on.
 */
@Composable
fun WritingPad(strokes: List<Stroke>, trace: Boolean, onFinished: (mistakes: Int) -> Unit, modifier: Modifier = Modifier) {
    var done by remember(strokes) { mutableIntStateOf(0) }
    var misses by remember(strokes) { mutableIntStateOf(0) }
    var mistakes by remember(strokes) { mutableIntStateOf(0) }
    val drawing = remember(strokes) { mutableStateListOf<Pt>() }
    val colors = MaterialTheme.colorScheme
    fun finishStroke(points: List<Pt>) {
        drawing.clear()
        if (done >= strokes.size) return
        if (Strokes.matches(points, strokes[done])) {
            done++; misses = 0
        } else {
            mistakes++; misses++
            if (misses >= 3) { done++; misses = 0 }
        }
        if (done == strokes.size) onFinished(mistakes)
    }
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Surface(
            shape = RoundedCornerShape(16.dp), color = colors.surfaceContainerLow,
            modifier = Modifier.fillMaxWidth().aspectRatio(1f)
                .semantics { contentDescription = "Writing area. Stroke ${minOf(done + 1, strokes.size)} of ${strokes.size}." },
        ) {
            Canvas(
                Modifier.fillMaxSize().pointerInput(strokes) {
                    awaitEachGesture {
                        val scale = size.width.coerceAtMost(size.height) / Strokes.GRID
                        fun grid(o: Offset) = Pt(o.x / scale, o.y / scale)
                        val down = awaitFirstDown()
                        val points = mutableListOf(grid(down.position))
                        drawing.clear(); drawing += points
                        while (true) {
                            val event = awaitPointerEvent()
                            val change = event.changes.firstOrNull { it.id == down.id } ?: break
                            if (!change.pressed) break
                            change.consume()
                            grid(change.position).let { points += it; drawing += it }
                        }
                        finishStroke(points)
                    }
                },
            ) {
                guide(colors.outlineVariant)
                if (trace) strokes.forEach { stroke(it, colors.surfaceContainerHighest) }
                strokes.take(done).forEach { stroke(it, colors.onSurface) }
                if (done < strokes.size && misses > 0) {
                    // Show where the next stroke goes: its start, and after two misses the whole stroke.
                    if (misses >= 2) stroke(strokes[done], colors.primary.copy(alpha = 0.35f))
                    val s = strokes[done][0]
                    val scale = size.minDimension / Strokes.GRID
                    drawCircle(colors.primary, 3.5f * scale, Offset(s.x * scale, s.y * scale))
                }
                if (drawing.isNotEmpty()) stroke(drawing.toList(), colors.tertiary, width = 3.6f)
            }
        }
        Spacer(Modifier.height(12.dp))
        val status = when {
            done == strokes.size -> if (mistakes == 0) "Perfect!" else "Done, with $mistakes ${if (mistakes == 1) "miss" else "misses"}."
            misses > 0 -> "Not quite. Stroke ${done + 1} starts at the dot."
            else -> "Draw stroke ${done + 1} of ${strokes.size}."
        }
        Text(status, style = MaterialTheme.typography.titleMedium)
    }
}

/** Practice writing each kana or kanji in [text], one after the other. */
@Composable
fun WriteScreen(text: String, library: Library, close: () -> Unit) {
    val chars = remember(text) { text.map { it.toString() }.filter { library.strokesOf(it) != null } }
    var index by rememberSaveable { mutableIntStateOf(0) }
    var trace by rememberSaveable { mutableStateOf(true) }
    var run by rememberSaveable { mutableIntStateOf(0) }
    var finishedAt by remember { mutableStateOf<Pair<Int, Int>?>(null) }
    if (chars.isEmpty()) {
        LaunchedEffect(Unit) { close() }
        return
    }
    val char = chars[index.coerceIn(chars.indices)]
    val finished = finishedAt == (index to run)
    val strokes = library.strokesOf(char).orEmpty()
    Scaffold(topBar = { TopAppBar(title = { Text("Write $text") }, navigationIcon = { BackButton(close) }) }) { padding ->
        Column(
            Modifier.padding(padding).fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp), modifier = Modifier.fillMaxWidth()) {
                StrokeOrder(char, strokes, Modifier.size(112.dp))
                Column(Modifier.weight(1f)) {
                    if (chars.size > 1) Text("${index + 1} of ${chars.size}", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                    Text("${strokes.size} ${if (strokes.size == 1) "stroke" else "strokes"}", style = MaterialTheme.typography.titleMedium)
                    Text("Tap the small picture to watch the order again.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth().padding(top = 12.dp)) {
                SegmentedButton(trace, { trace = true; run++ }, SegmentedButtonDefaults.itemShape(0, 2)) { Text("Trace") }
                SegmentedButton(!trace, { trace = false; run++ }, SegmentedButtonDefaults.itemShape(1, 2)) { Text("From memory") }
            }
            Spacer(Modifier.height(16.dp))
            key(char, run) {
                WritingPad(strokes, trace, onFinished = { finishedAt = index to run }, modifier = Modifier.widthIn(max = 480.dp))
            }
            Spacer(Modifier.height(16.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedButton(onClick = { run++ }) { Text("Start over") }
                if (finished && index < chars.lastIndex) Button(onClick = { index++; run++ }) { Text("Next: ${chars[index + 1]}") }
            }
        }
    }
}
