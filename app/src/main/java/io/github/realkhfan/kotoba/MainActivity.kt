package io.github.realkhfan.kotoba

import android.content.Context
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import io.github.realkhfan.kotoba.data.Library
import io.github.realkhfan.kotoba.data.Progress
import io.github.realkhfan.kotoba.data.SettingsStore
import io.github.realkhfan.kotoba.ui.AssetSpeaker
import io.github.realkhfan.kotoba.ui.KotobaApp
import io.github.realkhfan.kotoba.ui.KotobaTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

private class AppData(val library: Library, val progress: Progress, val settings: SettingsStore, val speaker: AssetSpeaker)

/** One copy per process, so a new activity never reads the progress file while the old one is still writing it. */
private object Store {
    @Volatile private var data: AppData? = null

    fun get(context: Context): AppData = data ?: synchronized(this) {
        data ?: AppData(
            Library.load { name -> context.assets.open(name).bufferedReader().use { it.readText() } },
            Progress(File(context.filesDir, "progress.tsv")).apply { load() },
            SettingsStore(context.getSharedPreferences("settings", Context.MODE_PRIVATE)),
            AssetSpeaker(context),
        ).also { data = it }
    }

    fun stopSound() = data?.speaker?.stop()
}

class MainActivity : ComponentActivity() {
    private var resumeTick by mutableIntStateOf(0)

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContent {
            val data by produceState<AppData?>(null) { value = withContext(Dispatchers.IO) { Store.get(applicationContext) } }
            val loaded = data
            if (loaded == null) KotobaTheme { Surface(Modifier.fillMaxSize()) {} }
            else KotobaApp(loaded.library, loaded.progress, loaded.settings, loaded.speaker, BuildConfig.VERSION_NAME, resumeTick)
        }
    }

    override fun onStop() {
        super.onStop()
        if (!isChangingConfigurations) Store.stopSound()
    }

    override fun onResume() {
        super.onResume()
        resumeTick++
    }
}
