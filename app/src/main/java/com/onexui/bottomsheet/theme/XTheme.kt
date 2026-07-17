package com.onexui.bottomsheet.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color

// Стаб темы 1XUI для лаборатории. В реальном ядре цвета приходят из дизайн-токенов;
// здесь держим только два поля, которые вшиты в анатомию XBottomSheet: фон листа и цвет сепаратора
// (он же цвет Drag Handle в стиле Theme). Значения — лабовые (light/dark).
@Immutable
internal data class XColors(
    val backgroundContent: Color,
    val separator: Color,
)

private val LightXColors = XColors(
    backgroundContent = Color(0xFFFFFFFF),
    separator = Color(0xFFD8DDE9),
)

private val DarkXColors = XColors(
    backgroundContent = Color(0xFF121212),
    separator = Color(0xFF2A2F3A),
)

internal object XTheme {
    val colors: XColors
        @Composable get() = if (isSystemInDarkTheme()) DarkXColors else LightXColors
}
