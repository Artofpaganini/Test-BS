package com.onexui.bottomsheet.config

import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color

@Immutable
internal data class XBottomSheetColors(
    val scrim: Color,
    val sheetBackground: Color,
) {
    internal val specScrim: Color = Color.Black.copy(alpha = SCRIM_ALPHA)

    private companion object {
        const val SCRIM_ALPHA = 0.40f
    }
}
