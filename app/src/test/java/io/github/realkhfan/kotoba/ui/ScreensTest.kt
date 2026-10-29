package io.github.realkhfan.kotoba.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import com.github.takahirom.roborazzi.captureRoboImage
import io.github.realkhfan.kotoba.data.Choice
import io.github.realkhfan.kotoba.data.Item
import io.github.realkhfan.kotoba.data.Order
import io.github.realkhfan.kotoba.data.Progress
import io.github.realkhfan.kotoba.data.Question
import io.github.realkhfan.kotoba.data.Quiz
import io.github.realkhfan.kotoba.data.Section
import io.github.realkhfan.kotoba.data.Session
import io.github.realkhfan.kotoba.data.testLibrary
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import kotlin.random.Random

/**
 * Screenshots of the main screens at Pixel 8 Pro size. Run with ./gradlew recordRoborazziDebug and look at them.
 * SDK 30 has no wallpaper colors, so they show the app's own palette.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [30], qualifiers = "w448dp-h997dp-xxhdpi")
class ScreensTest {
    @get:Rule val compose = createComposeRule()

    private val lib = testLibrary
    private val now = System.currentTimeMillis()
    private val hour = 3_600_000L

    /** Some of everything: mastered hiragana, due reviews, words at different levels. */
    private fun sample() = Progress(null).apply {
        val basic = lib.hiragana.take(46)
        learn(basic, now)
        basic.forEach { item -> repeat(7) { answer(item, true, now) } }
        learn(lib.hiragana.drop(46).take(25), now - 10 * hour)
        learn(lib.katakana.take(20), now - hour)
        learn(lib.words.take(60), now - hour)
        lib.words.take(30).forEach { item -> repeat(4) { answer(item, true, now) } }
        learn(lib.words.drop(60).take(10), now - 6 * hour)
        learn(lib.grammar.take(5), now - hour)
        learn(lib.kanji.take(14), now - 5 * hour)
    }

    private fun shot(name: String, dark: Boolean = false, content: @Composable () -> Unit) {
        compose.setContent { KotobaTheme(dark = dark) { content() } }
        compose.onRoot().captureRoboImage("src/test/screenshots/$name.png")
    }

    /** A seed that makes the quiz start with the kind of question we want to show. */
    private fun seedFor(items: List<Item>, wanted: (Question) -> Boolean) =
        (0..500).first { s -> Random(s).let { r -> Session(items, Quiz(lib, r), r, null).current!!.let(wanted) } }

    @Test fun home() = shot("01-home") { HomeScreen(lib, sample(), now) {} }

    @Test fun homeDark() = shot("02-home-dark", dark = true) { HomeScreen(lib, sample(), now) {} }

    @Config(fontScale = 2f)
    @Test fun homeLargeFont() = shot("13-home-large-font") { HomeScreen(lib, sample(), now) {} }

    @Config(fontScale = 2f)
    @Test fun hiraganaLargeFont() = shot("14-hiragana-large-font") { SectionScreen(Section.HIRAGANA, lib, sample(), now, {}, {}) }

    @Test fun words() = shot("03-words") { SectionScreen(Section.WORDS, lib, sample(), now, {}, {}) }

    @Test fun word() = shot("04-word") { DetailScreen(lib.words.first { it.written == "食べる" }, lib, sample(), {}, {}) }

    @Test fun hiragana() = shot("05-hiragana") { SectionScreen(Section.HIRAGANA, lib, sample(), now, {}, {}) }

    @Test fun kanji() = shot("06-kanji") { DetailScreen(lib.kanji.first { it.char == "日" }, lib, sample(), {}, {}) }

    @Test fun grammar() = shot("07-grammar") { DetailScreen(lib.grammar.first { it.id == "te-imasu" }, lib, sample(), {}, {}) }

    @Test fun grammarList() = shot("08-grammar-list") { SectionScreen(Section.GRAMMAR, lib, sample(), now, {}, {}) }

    @Test fun lesson() = shot("09-lesson") { LessonScreen(Section.WORDS, lib, sample()) {} }

    @Test fun wordQuiz() {
        val items = listOf(lib.words.first { it.written == "図書館" })
        val seed = seedFor(items) { it is Choice && it.ask == "What does it mean?" }
        shot("10-quiz-word") { QuizScreen("Reviews", items, lib, null, {}, {}, "", Random(seed)) }
    }

    @Test fun grammarQuizAnswered() {
        val g = lib.grammar.first { it.id == "te-imasu" }
        val seed = seedFor(listOf(g)) { it is Choice }
        val q = Random(seed).let { r -> Session(listOf(g), Quiz(lib, r), r, null).current as Choice }
        shot("11-quiz-grammar") { QuizScreen("Reviews", listOf(g), lib, null, {}, {}, "", Random(seed)) }
        compose.onNodeWithText(q.options[q.answer]).performClick()
        compose.onRoot().captureRoboImage("src/test/screenshots/11-quiz-grammar.png")
    }

    @Test fun sentenceOrder() {
        val s = lib.sentences.first { it.japanese == "電車で会社に行きます。" }
        val seed = seedFor(listOf(s)) { it is Order }
        val q = Random(seed).let { r -> Session(listOf(s), Quiz(lib, r), r, null).current as Order }
        shot("12-quiz-order") { QuizScreen("Reviews", listOf(s), lib, null, {}, {}, "", Random(seed)) }
        compose.onNodeWithText(s.chunks[0]).performClick()
        compose.onNodeWithText(s.chunks[1]).performClick()
        compose.onRoot().captureRoboImage("src/test/screenshots/12-quiz-order.png")
        check(q.tiles.size == s.chunks.size)
    }
}
