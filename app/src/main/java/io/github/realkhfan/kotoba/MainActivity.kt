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
import io.github.realkhfan.kotoba.ui.KotobaApp
import io.github.realkhfan.kotoba.ui.KotobaTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/** One copy per process, so a new activity never reads the progress file while the old one is still writing it. */
private object Store {
    @Volatile private var data: Pair<Library, Progress>? = null

    fun get(context: Context): Pair<Library, Progress> = data ?: synchronized(this) {
        data ?: run {
            val library = Library.load { name -> context.assets.open(name).bufferedReader().use { it.readText() } }
            val progress = Progress(File(context.filesDir, "progress.tsv")).apply { load() }
            (library to progress).also { data = it }
        }
    }
}

class MainActivity : ComponentActivity() {
    private var resumeTick by mutableIntStateOf(0)

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContent {
            KotobaTheme {
                val data by produceState<Pair<Library, Progress>?>(null) {
                    value = withContext(Dispatchers.IO) { Store.get(applicationContext) }
                }
                val loaded = data
                if (loaded == null) Surface(Modifier.fillMaxSize()) {} else KotobaApp(loaded.first, loaded.second, resumeTick)
            }
        }
    }

    override fun onResume() {
        super.onResume()
        resumeTick++
    }
}
