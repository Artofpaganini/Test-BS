package com.alva.core.uikit.components.bottom_sheet

import androidx.compose.ui.graphics.Color

private const val DEFAULT_SCRIM_ALPHA = 0.32f

/**
 * Управляет отображением scrim (затемнения) под нижним листом.
 */
sealed interface AlvaBottomSheetScrim {

    /** Стандартный scrim с заданным цветом (по умолчанию — Material baseline). */
    data class Enabled(
        val color: Color = Color.Black.copy(alpha = DEFAULT_SCRIM_ALPHA),
    ) : AlvaBottomSheetScrim

    /** Scrim отсутствует. */
    data object Disabled : AlvaBottomSheetScrim
}
