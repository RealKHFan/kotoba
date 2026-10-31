package io.github.realkhfan.kotoba.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.runtime.toMutableStateList
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.github.realkhfan.kotoba.data.Backup
import io.github.realkhfan.kotoba.data.Item
import io.github.realkhfan.kotoba.data.Library
import io.github.realkhfan.kotoba.data.Progress
import io.github.realkhfan.kotoba.data.Section
import io.github.realkhfan.kotoba.data.SettingsStore
import io.github.realkhfan.kotoba.data.ThemeMode
import io.github.realkhfan.kotoba.data.Srs

/**
 * Screens are routes on a small back stack: "home", "section:WORDS", "item:<key>", "lesson:WORDS",
 * "review:WORDS" or "review:all", "write:<characters>" and "settings". The stack and each screen's saved state survive process death.
 */
@Composable
fun KotobaApp(
    library: Library,
    progress: Progress,
    settings: SettingsStore,
    speaker: Speaker = Speaker.Silent,
    backup: Backup? = null,
    version: String = "",
    resumeTick: Int = 0,
) {
    val dark = when (settings.value.theme) {
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
    }
    KotobaTheme(dark) {
        CompositionLocalProvider(LocalSettings provides settings.value, LocalSpeaker provides speaker) {
            Screens(library, progress, settings, backup, version, resumeTick)
        }
    }
}

@Composable
private fun Screens(library: Library, progress: Progress, settings: SettingsStore, backup: Backup?, version: String, resumeTick: Int) {
    val stack = rememberSaveable(saver = listSaver<SnapshotStateList<String>, String>({ it.toList() }, { it.toMutableStateList() })) {
        mutableStateListOf("home")
    }
    val states = rememberSaveableStateHolder()
    val route = stack.last()
    val stateKey = "${stack.lastIndex}|$route"
    // Due counts are worked out against the time the screen was opened or the app came back.
    val now = remember(stateKey, resumeTick) { System.currentTimeMillis() }
    val open = { r: String -> stack.add(r); Unit }
    val back = {
        if (stack.size > 1) {
            states.removeState(stateKey)
            stack.removeAt(stack.lastIndex)
        }
    }
    BackHandler(enabled = stack.size > 1, onBack = back)
    val name = route.substringBefore(':')
    val arg = route.substringAfter(':', "")
    states.SaveableStateProvider(stateKey) {
        when (name) {
            "section" -> SectionScreen(Section.valueOf(arg), library, progress, now, open, back)
            "item" -> library.byKey[arg]?.let { DetailScreen(it, library, progress, open, back) } ?: LaunchedEffect(route) { back() }
            "lesson" -> LessonScreen(Section.valueOf(arg), library, progress, open, back)
            "write" -> WriteScreen(arg, library, back)
            "settings" -> SettingsScreen(settings, progress, version, backup, back)
            "review" -> ReviewScreen(Section.entries.firstOrNull { it.name == arg }, library, progress, now, back)
            else -> {
                HomeScreen(library, progress, now, backup?.failing == true, open)
                if (backup != null && backup.folder == null && !backup.asked) BackupQuestion(backup, progress)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DetailScreen(item: Item, library: Library, progress: Progress, open: (String) -> Unit, back: () -> Unit) {
    Scaffold(topBar = { TopAppBar(title = { Text(item.section.title) }, navigationIcon = { BackButton(back) }) }) { padding ->
        Column(Modifier.padding(padding).fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 12.dp)) {
            ItemDetail(item, library, Srs.stage(progress[item.key]), open = { open("item:${it.key}") }, write = { open("write:$it") })
        }
    }
}
