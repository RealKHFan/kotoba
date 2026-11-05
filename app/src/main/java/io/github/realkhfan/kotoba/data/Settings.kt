package io.github.realkhfan.kotoba.data

import android.content.SharedPreferences
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.edit

enum class ThemeMode(val label: String) { SYSTEM("Phone"), LIGHT("Light"), DARK("Dark") }

enum class AnswerMode(val label: String) { MIX("Mix"), CHOICE("Choose"), TYPE("Type") }

/** Everything the user can change. 0 reviews per session means all of them. */
data class Settings(
    val theme: ThemeMode = ThemeMode.SYSTEM,
    val wordsPerLesson: Int = 10,
    val itemsPerLesson: Int = 5,
    val answers: AnswerMode = AnswerMode.MIX,
    val romaji: Boolean = true,
    val quizReadings: Boolean = true,
    val autoplay: Boolean = true,
    val reviewsPerSession: Int = 0,
    val listening: Boolean = true,
    val writing: Boolean = true,
) {
    /** Words and kanji/sentences follow the settings; a grammar point or a kana row stays one lesson. */
    fun lessonSize(section: Section) = when (section) {
        Section.WORDS -> wordsPerLesson
        Section.KANJI, Section.SENTENCES -> itemsPerLesson
        else -> section.lessonSize
    }

    /** As text, for the backup file. */
    fun toMap(): Map<String, String> = mapOf(
        "theme" to theme.name, "wordsPerLesson" to "$wordsPerLesson", "itemsPerLesson" to "$itemsPerLesson",
        "answers" to answers.name, "romaji" to "$romaji", "quizReadings" to "$quizReadings", "autoplay" to "$autoplay",
        "reviewsPerSession" to "$reviewsPerSession", "listening" to "$listening", "writing" to "$writing",
    )

    companion object {
        val wordChoices = listOf(5, 10, 15, 20)
        val itemChoices = listOf(3, 5, 10)
        val reviewChoices = listOf(20, 50, 100, 0)

        /** Reads settings from text values (preferences or a backup); anything missing or odd keeps its default. */
        fun from(values: Map<String, String>, base: Settings = Settings()): Settings {
            fun int(key: String, default: Int, allowed: List<Int>) = values[key]?.toIntOrNull()?.takeIf { it in allowed } ?: default
            fun bool(key: String, default: Boolean) = values[key]?.toBooleanStrictOrNull() ?: default
            return Settings(
                theme = ThemeMode.entries.firstOrNull { it.name == values["theme"] } ?: base.theme,
                wordsPerLesson = int("wordsPerLesson", base.wordsPerLesson, wordChoices),
                itemsPerLesson = int("itemsPerLesson", base.itemsPerLesson, itemChoices),
                answers = AnswerMode.entries.firstOrNull { it.name == values["answers"] } ?: base.answers,
                romaji = bool("romaji", base.romaji),
                quizReadings = bool("quizReadings", base.quizReadings),
                autoplay = bool("autoplay", base.autoplay),
                reviewsPerSession = int("reviewsPerSession", base.reviewsPerSession, reviewChoices),
                listening = bool("listening", base.listening),
                writing = bool("writing", base.writing),
            )
        }
    }
}

/** Keeps [Settings] in SharedPreferences; null prefs keep them in memory (tests). */
class SettingsStore(private val prefs: SharedPreferences?) {
    var value by mutableStateOf(prefs?.let { p -> Settings.from(p.all.mapValues { it.value.toString() }) } ?: Settings())
        private set

    /** Called after every change, e.g. to update the backup. */
    var onChange: (() -> Unit)? = null

    fun update(change: (Settings) -> Settings) = replace(change(value))

    fun replace(s: Settings) {
        value = s
        prefs?.edit {
            putString("theme", s.theme.name); putInt("wordsPerLesson", s.wordsPerLesson); putInt("itemsPerLesson", s.itemsPerLesson)
            putString("answers", s.answers.name); putBoolean("romaji", s.romaji); putBoolean("quizReadings", s.quizReadings)
            putBoolean("autoplay", s.autoplay); putInt("reviewsPerSession", s.reviewsPerSession)
            putBoolean("listening", s.listening); putBoolean("writing", s.writing)
        }
        onChange?.invoke()
    }
}
