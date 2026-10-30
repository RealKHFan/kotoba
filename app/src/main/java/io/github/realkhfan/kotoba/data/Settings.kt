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
) {
    /** Words and kanji/sentences follow the settings; a grammar point or a kana row stays one lesson. */
    fun lessonSize(section: Section) = when (section) {
        Section.WORDS -> wordsPerLesson
        Section.KANJI, Section.SENTENCES -> itemsPerLesson
        else -> section.lessonSize
    }

    companion object {
        val wordChoices = listOf(5, 10, 15, 20)
        val itemChoices = listOf(3, 5, 10)
        val reviewChoices = listOf(20, 50, 100, 0)
    }
}

/** Keeps [Settings] in SharedPreferences; null prefs keep them in memory (tests). */
class SettingsStore(private val prefs: SharedPreferences?) {
    var value by mutableStateOf(load())
        private set

    fun update(change: (Settings) -> Settings) {
        value = change(value)
        val s = value
        prefs?.edit {
            putString("theme", s.theme.name); putInt("wordsPerLesson", s.wordsPerLesson); putInt("itemsPerLesson", s.itemsPerLesson)
            putString("answers", s.answers.name); putBoolean("romaji", s.romaji); putBoolean("quizReadings", s.quizReadings)
            putBoolean("autoplay", s.autoplay); putInt("reviewsPerSession", s.reviewsPerSession)
        }
    }

    private fun load(): Settings {
        val p = prefs ?: return Settings()
        val d = Settings()
        fun int(key: String, default: Int, allowed: List<Int>) = p.getInt(key, default).takeIf { it in allowed } ?: default
        return Settings(
            theme = ThemeMode.entries.firstOrNull { it.name == p.getString("theme", null) } ?: d.theme,
            wordsPerLesson = int("wordsPerLesson", d.wordsPerLesson, Settings.wordChoices),
            itemsPerLesson = int("itemsPerLesson", d.itemsPerLesson, Settings.itemChoices),
            answers = AnswerMode.entries.firstOrNull { it.name == p.getString("answers", null) } ?: d.answers,
            romaji = p.getBoolean("romaji", d.romaji),
            quizReadings = p.getBoolean("quizReadings", d.quizReadings),
            autoplay = p.getBoolean("autoplay", d.autoplay),
            reviewsPerSession = int("reviewsPerSession", d.reviewsPerSession, Settings.reviewChoices),
        )
    }
}
