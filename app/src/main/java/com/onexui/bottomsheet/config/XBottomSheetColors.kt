package com.onexui.bottomsheet.config

import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color

// Цвета конфигурируемы; Unspecified → дефолт спеки/темы (резолв в композиции корня — см. XBottomSheetColorsResolve).
@Immutable
internal class XBottomSheetColors(
    val scrim: Color,
    val sheetBackground: Color,
    val handleTheme: Color,
    val handleStatic: Color,
) {
    internal val specScrim: Color = Color.Black.copy(alpha = 0.40f)
    internal val specHandleStatic: Color = Color.White.copy(alpha = 0.40f)
}
