package com.example.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import com.example.state.CalcThemePreset

val LocalCalcPalette = staticCompositionLocalOf { paletteFor(CalcThemePreset.CLASSIC_METALLIC) }

@Composable
fun NaturalCalcTheme(
    preset: CalcThemePreset = CalcThemePreset.CLASSIC_METALLIC,
    content: @Composable () -> Unit,
) {
    val palette = paletteFor(preset)
    val colorScheme = if (palette.isDark) {
        darkColorScheme(
            primary = palette.equalsKeyTop,
            secondary = palette.shiftAccent,
            tertiary = palette.alphaAccent,
            background = palette.chassisBottom,
            surface = palette.sheetSurface,
            onBackground = palette.sheetText,
            onSurface = palette.sheetText,
        )
    } else {
        lightColorScheme(
            primary = palette.equalsKeyTop,
            secondary = palette.shiftAccent,
            tertiary = palette.alphaAccent,
            background = palette.chassisBottom,
            surface = palette.sheetSurface,
            onBackground = palette.sheetText,
            onSurface = palette.sheetText,
        )
    }

    CompositionLocalProvider(LocalCalcPalette provides palette) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = CalcTypography,
            content = content,
        )
    }
}
