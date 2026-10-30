package io.github.realkhfan.kotoba.data

import java.security.MessageDigest

/**
 * Names of the bundled sound clips (assets/audio/<key>.ogg, made by tools/audio.py).
 * A key is the start of the SHA-1 of what is said, so the app and the script agree without a list.
 * Words are keyed with their reading too, because 箸 and 橋 sound different.
 */
object Audio {
    fun key(text: String): String =
        MessageDigest.getInstance("SHA-1").digest(text.toByteArray()).joinToString("") { "%02x".format(it) }.take(16)

    fun word(written: String, reading: String) = key("$written|$reading")

    fun sentence(japanese: String) = key(japanese)

    /** The clip for an item on its own, or null for kanji and grammar points (their examples have clips). */
    fun of(item: Item): String? = when (item) {
        is Word -> word(item.written, item.reading)
        is Kana -> key(item.char)
        is Sentence -> sentence(item.japanese)
        is Kanji, is Grammar -> null
    }

    /** A gap sentence with the answer filled in, without the hint in brackets: what gets said after the answer. */
    fun filled(sentence: String, answer: String) = sentence.replace("＿＿", answer).substringBefore("(").trim()

    fun gap(sentence: String, answer: String) = sentence(filled(sentence, answer))
}
