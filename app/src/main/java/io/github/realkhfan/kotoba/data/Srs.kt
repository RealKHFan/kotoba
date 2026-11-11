package io.github.realkhfan.kotoba.data

import androidx.compose.runtime.mutableStateMapOf
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.util.concurrent.Executor
import java.util.concurrent.Executors

/** Spaced-repetition state of one learned item: level 1..9, when it is due again, and how often it was missed in reviews. */
data class SrsState(val level: Int, val due: Long, val misses: Int = 0)

enum class Stage(val label: String) { NEW("New"), LEARNING("Learning"), KNOWN("Known"), MASTERED("Mastered") }

object Srs {
    const val MAX_LEVEL = 9
    private const val HOUR = 3_600_000L
    // Wait after reaching each level: 4 h, 8 h, 1 d, 2 d, 4 d, 1 w, 2 w, 1 month, 4 months.
    private val waitHours = longArrayOf(0, 4, 8, 24, 48, 96, 168, 336, 720, 2880)

    fun learned(now: Long) = SrsState(1, now + waitHours[1] * HOUR)

    /** A right answer moves up one level; a wrong one drops two (never below 1) and counts as a miss. */
    fun answer(s: SrsState, correct: Boolean, now: Long): SrsState {
        val level = if (correct) minOf(s.level + 1, MAX_LEVEL) else maxOf(1, s.level - 2)
        return SrsState(level, now + waitHours[level] * HOUR, if (correct) s.misses else s.misses + 1)
    }

    fun stage(s: SrsState?): Stage = when {
        s == null -> Stage.NEW
        s.level <= 4 -> Stage.LEARNING
        s.level <= 7 -> Stage.KNOWN
        else -> Stage.MASTERED
    }

    /** Missed three times or more and not known yet: worth extra practice. Reaching Known takes it off the list. */
    fun isHard(s: SrsState?) = s != null && s.misses >= 3 && stage(s) == Stage.LEARNING
}

data class Stats(val total: Int, val learned: Int, val due: Int, val mastered: Int)

/** What happened on one day: reviews answered (and how many right the first time), new items learned. */
data class Day(val reviews: Int = 0, val right: Int = 0, val learned: Int = 0) {
    val active get() = reviews + learned > 0
}

/**
 * Learning progress, kept in one small text file. Changes are written in the background,
 * through a temp file and an atomic rename, so a crash can't leave half a file.
 * [file] null keeps everything in memory (tests and previews).
 */
class Progress(
    private val file: File?,
    private val zone: ZoneId = ZoneId.systemDefault(),
    private val io: Executor = Executors.newSingleThreadExecutor(),
) {
    private val states = mutableStateMapOf<String, SrsState>()
    private val days = mutableStateMapOf<String, Day>()

    /** Called on the writer thread with the file's text after each save, to keep a copy elsewhere (see [Backup]). */
    @Volatile var copyTo: ((String) -> Unit)? = null

    operator fun get(key: String): SrsState? = states[key]
    fun isLearned(key: String) = key in states
    val learnedCount get() = states.size
    fun snapshot(): Map<String, SrsState> = states.toMap()

    fun load() {
        val f = file?.takeIf { it.exists() } ?: return
        try {
            val text = f.readText()
            states.putAll(decode(text))
            days.putAll(decodeDays(text))
        } catch (e: IOException) {
            // Keep the unreadable file aside instead of overwriting it on the next save.
            f.renameTo(File(f.parentFile, f.name + ".unreadable"))
        }
    }

    fun date(time: Long): LocalDate = Instant.ofEpochMilli(time).atZone(zone).toLocalDate()
    fun day(date: LocalDate): Day = days[date.toString()] ?: Day()
    private fun record(now: Long, change: (Day) -> Day) {
        val key = date(now).toString()
        days[key] = change(days[key] ?: Day())
    }

    fun learn(items: List<Item>, now: Long) {
        items.forEach { states[it.key] = Srs.learned(now) }
        record(now) { it.copy(learned = it.learned + items.size) }
        save()
    }

    fun answer(item: Item, correct: Boolean, now: Long) {
        val s = states[item.key] ?: return
        states[item.key] = Srs.answer(s, correct, now)
        record(now) { it.copy(reviews = it.reviews + 1, right = it.right + if (correct) 1 else 0) }
        save()
    }

    /** Forgets everything learned (the day log too). */
    fun reset() {
        states.clear()
        days.clear()
        save()
    }

    /** Replaces everything with what a backup holds. */
    fun replaceAll(restored: Map<String, SrsState>, restoredDays: Map<String, Day> = emptyMap()) {
        states.clear()
        states.putAll(restored)
        days.clear()
        days.putAll(restoredDays)
        save()
    }

    /** The progress file's text as it is now. */
    fun text(): String = encode(states.toMap(), days.toMap())

    /** Copies the current progress now, e.g. into a newly chosen backup folder or after a settings change. */
    fun copyNow() {
        val text = text()
        if (states.isNotEmpty()) io.execute { copyTo?.invoke(text) }
    }

    fun due(items: List<Item>, now: Long): List<Item> = items.filter { item -> states[item.key]?.let { it.due <= now } == true }

    fun hard(items: List<Item>): List<Item> = items.filter { Srs.isHard(states[it.key]) }

    fun stats(items: List<Item>, now: Long): Stats {
        var learned = 0; var due = 0; var mastered = 0
        for (item in items) {
            val s = states[item.key] ?: continue
            learned++
            if (s.due <= now) due++
            if (Srs.stage(s) == Stage.MASTERED) mastered++
        }
        return Stats(items.size, learned, due, mastered)
    }

    /** The earliest due time still in the future among [items], or null. */
    fun nextDue(items: List<Item>, now: Long): Long? = items.mapNotNull { states[it.key]?.due }.filter { it > now }.minOrNull()

    /** Reviews due on each of the next [count] days; today includes everything already due. */
    fun forecast(now: Long, count: Int = 7): List<Int> {
        val today = date(now)
        val out = IntArray(count)
        for (s in states.values) {
            val offset = (date(s.due).toEpochDay() - today.toEpochDay()).toInt().coerceAtLeast(0)
            if (offset < count) out[offset]++
        }
        return out.toList()
    }

    /** Days in a row with a lesson or review, counting today only once something was done today. */
    fun streak(now: Long): Int {
        var d = date(now)
        if (!day(d).active) d = d.minusDays(1)
        var n = 0
        while (day(d).active) { n++; d = d.minusDays(1) }
        return n
    }

    private fun save() {
        val f = file ?: return
        val text = text()
        // Nothing learned (after a reset) isn't copied, so the backup can still undo a reset until the next lesson.
        val copy = states.isNotEmpty()
        io.execute {
            writeAtomic(f, text)
            if (copy) copyTo?.invoke(text)
        }
    }

    companion object {
        const val HEADER_PREFIX = "# kotoba progress"
        private const val HEADER = "$HEADER_PREFIX 2"
        private const val DAY = "@day"

        /** One line per item (key, level, due, misses), then one "@day" line per day with activity. */
        fun encode(states: Map<String, SrsState>, days: Map<String, Day> = emptyMap()): String =
            (listOf(HEADER) +
                states.entries.sortedBy { it.key }.map { (k, s) -> "$k\t${s.level}\t${s.due}\t${s.misses}" } +
                days.entries.filter { it.value.active }.sortedBy { it.key }.map { (d, v) -> "$DAY\t$d\t${v.reviews}\t${v.right}\t${v.learned}" })
                .joinToString("\n", postfix = "\n")

        /** Skips lines it can't read, so one damaged line doesn't lose the rest. Version 1 lines have no misses column. */
        fun decode(text: String): Map<String, SrsState> = text.lineSequence().mapNotNull { line ->
            val f = line.split('\t')
            if (line.startsWith("#") || line.startsWith("@") || f.size !in 3..4) return@mapNotNull null
            val level = f[1].toIntOrNull()?.takeIf { it in 1..Srs.MAX_LEVEL } ?: return@mapNotNull null
            val due = f[2].toLongOrNull() ?: return@mapNotNull null
            val misses = f.getOrNull(3)?.toIntOrNull()?.coerceAtLeast(0) ?: 0
            f[0] to SrsState(level, due, misses)
        }.toMap()

        fun decodeDays(text: String): Map<String, Day> = text.lineSequence().mapNotNull { line ->
            val f = line.split('\t')
            if (f.size != 5 || f[0] != DAY) return@mapNotNull null
            val date = runCatching { LocalDate.parse(f[1]) }.getOrNull() ?: return@mapNotNull null
            val n = f.drop(2).map { it.toIntOrNull()?.takeIf { v -> v >= 0 } ?: return@mapNotNull null }
            date.toString() to Day(n[0], n[1], n[2])
        }.toMap()

        fun writeAtomic(file: File, text: String) {
            val tmp = File(file.parentFile, file.name + ".tmp")
            try {
                FileOutputStream(tmp).use { it.write(text.toByteArray()); it.fd.sync() }
                Files.move(tmp.toPath(), file.toPath(), StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE)
            } catch (e: IOException) {
                tmp.delete() // the old file stays as it was
            }
        }
    }
}
