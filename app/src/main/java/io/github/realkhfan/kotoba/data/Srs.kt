package io.github.realkhfan.kotoba.data

import androidx.compose.runtime.mutableStateMapOf
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.util.concurrent.Executor
import java.util.concurrent.Executors

/** Spaced-repetition state of one learned item: level 1..9 and when it is due again. */
data class SrsState(val level: Int, val due: Long)

enum class Stage(val label: String) { NEW("New"), LEARNING("Learning"), KNOWN("Known"), MASTERED("Mastered") }

object Srs {
    const val MAX_LEVEL = 9
    private const val HOUR = 3_600_000L
    // Wait after reaching each level: 4 h, 8 h, 1 d, 2 d, 4 d, 1 w, 2 w, 1 month, 4 months.
    private val waitHours = longArrayOf(0, 4, 8, 24, 48, 96, 168, 336, 720, 2880)

    fun learned(now: Long) = SrsState(1, now + waitHours[1] * HOUR)

    /** A right answer moves up one level; a wrong one drops two (never below 1). */
    fun answer(s: SrsState, correct: Boolean, now: Long): SrsState {
        val level = if (correct) minOf(s.level + 1, MAX_LEVEL) else maxOf(1, s.level - 2)
        return SrsState(level, now + waitHours[level] * HOUR)
    }

    fun stage(s: SrsState?): Stage = when {
        s == null -> Stage.NEW
        s.level <= 4 -> Stage.LEARNING
        s.level <= 7 -> Stage.KNOWN
        else -> Stage.MASTERED
    }
}

data class Stats(val total: Int, val learned: Int, val due: Int, val mastered: Int)

/**
 * Learning progress, kept in one small text file. Changes are written in the background,
 * through a temp file and an atomic rename, so a crash can't leave half a file.
 * [file] null keeps everything in memory (tests and previews).
 */
class Progress(private val file: File?, private val io: Executor = Executors.newSingleThreadExecutor()) {
    private val states = mutableStateMapOf<String, SrsState>()

    operator fun get(key: String): SrsState? = states[key]
    fun isLearned(key: String) = key in states

    fun load() {
        val f = file?.takeIf { it.exists() } ?: return
        try {
            states.putAll(decode(f.readText()))
        } catch (e: IOException) {
            // Keep the unreadable file aside instead of overwriting it on the next save.
            f.renameTo(File(f.parentFile, f.name + ".unreadable"))
        }
    }

    fun learn(items: List<Item>, now: Long) {
        items.forEach { states[it.key] = Srs.learned(now) }
        save()
    }

    fun answer(item: Item, correct: Boolean, now: Long) {
        val s = states[item.key] ?: return
        states[item.key] = Srs.answer(s, correct, now)
        save()
    }

    fun due(items: List<Item>, now: Long): List<Item> = items.filter { item -> states[item.key]?.let { it.due <= now } == true }

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

    private fun save() {
        val f = file ?: return
        val text = encode(states.toMap())
        io.execute { writeAtomic(f, text) }
    }

    companion object {
        private const val HEADER = "# kotoba progress 1"

        fun encode(states: Map<String, SrsState>): String =
            (listOf(HEADER) + states.entries.sortedBy { it.key }.map { "${it.key}\t${it.value.level}\t${it.value.due}" })
                .joinToString("\n", postfix = "\n")

        /** Skips lines it can't read, so one damaged line doesn't lose the rest. */
        fun decode(text: String): Map<String, SrsState> = text.lineSequence().mapNotNull { line ->
            val f = line.split('\t')
            if (line.startsWith("#") || f.size != 3) return@mapNotNull null
            val level = f[1].toIntOrNull()?.takeIf { it in 1..Srs.MAX_LEVEL } ?: return@mapNotNull null
            val due = f[2].toLongOrNull() ?: return@mapNotNull null
            f[0] to SrsState(level, due)
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
