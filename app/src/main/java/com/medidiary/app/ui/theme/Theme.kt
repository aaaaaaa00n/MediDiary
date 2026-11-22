package com.medidiary.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

val MedicineBlue = Color(0xFF1565C0)
val MedicineBlueContainer = Color(0xFFDCE8FB)
val SymptomAmber = Color(0xFFB26A00)
val SymptomAmberContainer = Color(0xFFFFEFD2)

private val Light = lightColorScheme(
    primary = MedicineBlue,
    onPrimary = Color.White,
    primaryContainer = MedicineBlueContainer,
    onPrimaryContainer = Color(0xFF0A2A57),
    secondary = Color(0xFF00897B),
    tertiary = SymptomAmber,
    tertiaryContainer = SymptomAmberContainer,
    background = Color(0xFFF7F9FC),
    surface = Color.White,
    error = Color(0xFFB00020),
)

private val Dark = darkColorScheme(
    primary = Color(0xFF9CC2FF),
    primaryContainer = Color(0xFF0D3B78),
    secondary = Color(0xFF80CBC4),
    tertiary = Color(0xFFFFC266),
    tertiaryContainer = Color(0xFF5A3A00),
)

@Composable
fun MediDiaryTheme(dark: Boolean = isSystemInDarkTheme(), content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = if (dark) Dark else Light, content = content)
}
