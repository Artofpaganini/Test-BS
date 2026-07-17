package com.alva.core.uikit.components.bottom_sheet.config

import androidx.compose.runtime.Stable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import com.alva.core.uikit.components.bottom_sheet.AlvaBottomSheetScrim

@Stable
class AlvaBottomSheetConfig internal constructor(
    val shape: Shape,
    val containerColor: Color,
    val scrim: AlvaBottomSheetScrim,
    val draggable: Boolean,
    val dismissOnScrimTap: Boolean,
    val positionalThresholdFraction: Float,
    val adaptiveWidthFraction: Float,
)
