package io.github.realkhfan.kotoba.ui

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.intl.LocaleList

private val LightColors = lightColorScheme(
    primary = Color(0xFFB3261E), onPrimary = Color.White,
    primaryContainer = Color(0xFFFFDAD5), onPrimaryContainer = Color(0xFF410001),
    secondary = Color(0xFF6B4F9E), secondaryContainer = Color(0xFFEBDDFF), onSecondaryContainer = Color(0xFF250059),
    tertiary = Color(0xFF7D5700), tertiaryContainer = Color(0xFFFFDEA6), onTertiaryContainer = Color(0xFF271900),
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFFFFB4A9), onPrimary = Color(0xFF690002),
    primaryContainer = Color(0xFF930006), onPrimaryContainer = Color(0xFFFFDAD5),
    secondary = Color(0xFFD3BBFF), secondaryContainer = Color(0xFF53377F), onSecondaryContainer = Color(0xFFEBDDFF),
    tertiary = Color(0xFFF8BD48), tertiaryContainer = Color(0xFF5F4100), onTertiaryContainer = Color(0xFFFFDEA6),
)

// Kanji look different in Chinese and Japanese fonts; the "ja" locale makes Android pick the Japanese glyphs
// even when the phone is set to English or German.
private val japanese = LocaleList("ja")
private fun TextStyle.ja() = copy(localeList = japanese)

private val KotobaTypography = Typography().run {
    Typography(
        displayLarge = displayLarge.ja(), displayMedium = displayMedium.ja(), displaySmall = displaySmall.ja(),
        headlineLarge = headlineLarge.ja(), headlineMedium = headlineMedium.ja(), headlineSmall = headlineSmall.ja(),
        titleLarge = titleLarge.ja(), titleMedium = titleMedium.ja(), titleSmall = titleSmall.ja(),
        bodyLarge = bodyLarge.ja(), bodyMedium = bodyMedium.ja(), bodySmall = bodySmall.ja(),
        labelLarge = labelLarge.ja(), labelMedium = labelMedium.ja(), labelSmall = labelSmall.ja(),
    )
}

@Composable
fun KotobaTheme(dark: Boolean = isSystemInDarkTheme(), content: @Composable () -> Unit) {
    val colors = when {
        Build.VERSION.SDK_INT >= 31 -> if (dark) dynamicDarkColorScheme(LocalContext.current) else dynamicLightColorScheme(LocalContext.current)
        dark -> DarkColors
        else -> LightColors
    }
    MaterialTheme(colorScheme = colors, typography = KotobaTypography, content = content)
}
