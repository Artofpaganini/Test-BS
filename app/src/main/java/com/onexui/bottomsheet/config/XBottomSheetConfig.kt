package com.onexui.bottomsheet.config

import androidx.compose.runtime.Immutable
import androidx.compose.ui.unit.Dp
import com.onexui.bottomsheet.handle.DragHandleStyle

@Immutable
internal data class XBottomSheetConfig internal constructor(
    val overlayBackground: Boolean,
    val dragHandle: DragHandleStyle?,
    val additionalTopCornerRadius: Dp,
    val dismiss: DismissConfig,
    val keyboard: KeyboardConfig,
) {
    internal companion object {
        val Default = xBottomSheetConfig()
    }
}
