package com.onexui.bottomsheet.handle

import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color

@Immutable
internal sealed interface DragHandleStyle {
    data class Theme(val color: Color = Color.Unspecified) : DragHandleStyle
    data object Static : DragHandleStyle
}
