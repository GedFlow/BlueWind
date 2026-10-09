package com.example.bluewind.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

/**
 * BlueWind 색: 바탕은 검정에 가까운 남흑색, 포인트는 바람을 닮은 파랑 계열.
 * - primary: 하늘색 (눌린 키, 강조)
 * - primaryContainer: 진한 파랑 (다음 버튼, Enter)
 * - secondaryContainer: 남색 (일반 버튼)
 * - tertiary / tertiaryContainer: 청록빛 파랑 (Fn·CapsLock 켜짐, 좌상단 버튼)
 */
private val BlueWindColors = darkColorScheme(
    primary = Color(0xFF8CC8FF),
    onPrimary = Color(0xFF00315B),
    primaryContainer = Color(0xFF1A5FA8),
    onPrimaryContainer = Color(0xFFE3F0FF),
    secondary = Color(0xFF9CC7EE),
    onSecondary = Color(0xFF0A3150),
    secondaryContainer = Color(0xFF16324F),
    onSecondaryContainer = Color(0xFFD2E6FA),
    tertiary = Color(0xFF4FC3F7),
    onTertiary = Color(0xFF00344A),
    tertiaryContainer = Color(0xFF0F4C6E),
    onTertiaryContainer = Color(0xFFCDEEFF),
    background = Color(0xFF07090D),
    onBackground = Color(0xFFE3E8EF),
    surface = Color(0xFF07090D),
    onSurface = Color(0xFFE3E8EF),
    surfaceVariant = Color(0xFF18202B),
    onSurfaceVariant = Color(0xFFBFC9D6),
    outline = Color(0xFF5A6778),
    outlineVariant = Color(0xFF263142),
    surfaceContainerLowest = Color(0xFF040507),
    surfaceContainerLow = Color(0xFF0C1016),
    surfaceContainer = Color(0xFF10151D),
    surfaceContainerHigh = Color(0xFF151B25),
    surfaceContainerHighest = Color(0xFF1A212D),
)

@Composable
fun BlueWindTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = BlueWindColors, content = content)
}
