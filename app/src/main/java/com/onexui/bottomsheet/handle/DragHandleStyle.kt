package com.onexui.bottomsheet.handle

import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color

@Immutable
internal sealed interface DragHandleStyle {
    data object Theme : DragHandleStyle
    data class Static(val color: Color) : DragHandleStyle
}
