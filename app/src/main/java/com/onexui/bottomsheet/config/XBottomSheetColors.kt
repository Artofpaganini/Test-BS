package com.onexui.bottomsheet.config

import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color

/**
 * Цвета листа (scrim/фон/хендл). Каждый конфигурируем; Unspecified -> дефолт спеки или темы, резолв в композицие
 * корня (см. XBottomSheetColorsResolve). specScrim/specHandleStatic — тема-независимые дефолты спеки.
 */
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
