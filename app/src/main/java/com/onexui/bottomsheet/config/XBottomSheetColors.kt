package com.onexui.bottomsheet.config

import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color

// Цвета конфигурируемы; Unspecified → дефолт спеки/темы (резолв в композиции корня — см. XBottomSheetColorsResolve).
@Immutable
internal data class XBottomSheetColors(
    val scrim: Color,
    val sheetBackground: Color,
    val handleTheme: Color,
    val handleStatic: Color,
)
