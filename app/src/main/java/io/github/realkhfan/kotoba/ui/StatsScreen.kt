@file:OptIn(ExperimentalMaterial3Api::class)

package io.github.realkhfan.kotoba.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import io.github.realkhfan.kotoba.data.Library
import io.github.realkhfan.kotoba.data.Progress
import io.github.realkhfan.kotoba.data.Section
import java.time.format.TextStyle
import java.util.Locale

/** A small bar chart. Each bar reads out as one line for TalkBack. */
@Composable
private fun Bars(values: List<Int>, labels: List<String>, color: Color, describe: (value: Int, label: String) -> String) {
    val max = (values.maxOrNull() ?: 0).coerceAtLeast(1)
    Row(Modifier.fillMaxWidth().height(150.dp).padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        values.forEachIndexed { i, v ->
            Column(
                Modifier.weight(1f).fillMaxHeight().semantics(mergeDescendants = true) { contentDescription = describe(v, labels[i]) },
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(if (v > 0) "$v" else "", style = MaterialTheme.typography.labelSmall, maxLines = 1)
                Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.BottomCenter) {
                    Box(
                        Modifier.fillMaxWidth().fillMaxHeight((v.toFloat() / max).coerceAtLeast(0.02f))
                            .background(if (v > 0) color else MaterialTheme.colorScheme.surfaceContainerHighest, RoundedCornerShape(4.dp)),
                    )
                }
                Text(labels[i], style = MaterialTheme.typography.labelSmall, maxLines = 1)
            }
        }
    }
}

@Composable
fun StatsScreen(library: Library, progress: Progress, now: Long, open: (String) -> Unit, back: () -> Unit) {
    val today = progress.date(now)
    val streak = progress.streak(now)
    val todayStats = progress.day(today)
    val recent = (0L until 30L).map { progress.day(today.minusDays(it)) }
    val reviews = recent.sumOf { it.reviews }
    val right = recent.sumOf { it.right }
    val colors = MaterialTheme.colorScheme
    Scaffold(topBar = { TopAppBar(title = { Text("Stats") }, navigationIcon = { BackButton(back) }) }) { padding ->
        Column(Modifier.padding(padding).fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 8.dp)) {
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(20.dp)) {
                    Text(
                        when (streak) { 0 -> "No streak yet"; 1 -> "1 day in a row"; else -> "$streak days in a row" },
                        style = MaterialTheme.typography.headlineSmall,
                    )
                    Text(
                        if (todayStats.active) "Today: ${todayStats.reviews} reviews, ${todayStats.learned} new"
                        else "A lesson or a few reviews today keep the streak going.",
                        style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(top = 4.dp),
                    )
                    if (reviews > 0) {
                        Text(
                            "Last 30 days: ${right * 100 / reviews}% of $reviews reviews right the first time",
                            style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(top = 4.dp),
                        )
                    }
                }
            }

            SectionLabel("Reviews coming up")
            val forecast = progress.forecast(now)
            val dayNames = forecast.indices.map { if (it == 0) "Today" else today.plusDays(it.toLong()).dayOfWeek.getDisplayName(TextStyle.SHORT, Locale.getDefault()) }
            Bars(forecast, dayNames, colors.primary) { v, label -> "$label: $v reviews" }

            SectionLabel("Last 14 days")
            val last = (13 downTo 0).map { today.minusDays(it.toLong()) }
            Bars(last.map { progress.day(it).reviews + progress.day(it).learned }, last.map { "${it.dayOfMonth}" }, colors.tertiary) { v, label ->
                "Day $label: $v reviews and new items"
            }

            SectionLabel("Sections")
            Section.entries.forEach { section ->
                val s = progress.stats(library.items(section), now)
                Column(Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(section.title, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                        Text("${s.learned} / ${s.total} · ${s.mastered} mastered", style = MaterialTheme.typography.bodyMedium, color = colors.onSurfaceVariant)
                    }
                    LinearProgressIndicator(progress = { s.learned.toFloat() / s.total }, modifier = Modifier.fillMaxWidth().padding(top = 6.dp))
                }
            }

            val hard = progress.hard(library.all)
            SectionLabel(if (hard.isEmpty()) "Hard items" else "Hard items (${hard.size})")
            if (hard.isEmpty()) {
                Text("Nothing here. Items you miss three times or more show up here.", style = MaterialTheme.typography.bodyMedium)
            }
            hard.take(50).forEach { item ->
                val (ja, en) = summary(item)
                Row(
                    Modifier.fillMaxWidth().heightIn(min = 48.dp).clickable { open("item:${item.key}") }.padding(vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(ja, style = japaneseStyle(ja).copy(fontSize = MaterialTheme.typography.titleLarge.fontSize), modifier = Modifier.weight(0.4f))
                    Text(en, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(0.6f))
                }
            }
            Box(Modifier.height(24.dp))
        }
    }
}
