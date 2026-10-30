package io.github.realkhfan.kotoba.ui

import android.content.Context
import android.media.AudioAttributes
import android.media.MediaPlayer
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.unit.dp
import io.github.realkhfan.kotoba.data.Settings
import java.io.IOException

/** Plays the bundled clips. Screens get it from [LocalSpeaker]; previews and tests use [Silent]. */
interface Speaker {
    fun has(key: String?): Boolean
    fun play(key: String?)

    object Silent : Speaker {
        override fun has(key: String?) = false
        override fun play(key: String?) {}
    }
}

val LocalSpeaker = compositionLocalOf<Speaker> { Speaker.Silent }
val LocalSettings = compositionLocalOf { Settings() }

class AssetSpeaker(private val context: Context) : Speaker {
    // Listed once when the app starts (off the main thread), so has() is a cheap lookup.
    private val clips: Set<String> = context.assets.list("audio").orEmpty().map { it.removeSuffix(".ogg") }.toSet()
    private var player: MediaPlayer? = null

    override fun has(key: String?) = key != null && key in clips

    override fun play(key: String?) {
        if (!has(key)) return
        stop()
        try {
            context.assets.openFd("audio/$key.ogg").use { fd ->
                player = MediaPlayer().apply {
                    setAudioAttributes(
                        AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA).setContentType(AudioAttributes.CONTENT_TYPE_SPEECH).build(),
                    )
                    setDataSource(fd.fileDescriptor, fd.startOffset, fd.length)
                    setOnPreparedListener { it.start() }
                    setOnCompletionListener { done -> done.release(); if (player === done) player = null }
                    setOnErrorListener { failed, _, _ -> failed.release(); if (player === failed) player = null; true }
                    prepareAsync()
                }
            }
        } catch (e: IOException) {
            stop()
        } catch (e: IllegalStateException) {
            stop()
        }
    }

    fun stop() {
        player?.release()
        player = null
    }
}

// Material "volume up" icon (Apache 2.0); the core icon set doesn't include it.
val SpeakerIcon: ImageVector = ImageVector.Builder("Speaker", 24.dp, 24.dp, 24f, 24f).addPath(
    PathParser().parsePathString(
        "M3,9v6h4l5,5V4L7,9H3zM16.5,12c0,-1.77 -1.02,-3.29 -2.5,-4.03v8.05c1.48,-0.73 2.5,-2.25 2.5,-4.02z" +
            "M14,3.23v2.06c2.89,0.86 5,3.54 5,6.71s-2.11,5.85 -5,6.71v2.06c4.01,-0.91 7,-4.49 7,-8.77s-2.99,-7.86 -7,-8.77z",
    ).toNodes(),
    fill = SolidColor(Color.Black),
).build()

/** A play button for one clip; shows nothing when the clip doesn't exist. */
@Composable
fun SpeakButton(key: String?, label: String = "Play") {
    val speaker = LocalSpeaker.current
    if (!speaker.has(key)) return
    IconButton(onClick = { speaker.play(key) }) { Icon(SpeakerIcon, contentDescription = label) }
}
