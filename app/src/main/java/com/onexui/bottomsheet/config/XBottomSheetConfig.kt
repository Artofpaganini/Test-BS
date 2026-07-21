package com.onexui.bottomsheet.config

import androidx.compose.runtime.Immutable
import com.onexui.bottomsheet.handle.DragHandleStyle

@Immutable
internal data class XBottomSheetConfig internal constructor(
    val overlayBackground: Boolean,
    val dragHandle: DragHandleStyle?,
    val additionalTop: AdditionalTopConfig,
    val dismiss: DismissConfig,
    val keyboard: KeyboardConfig,
    val colors: XBottomSheetColors,
)
