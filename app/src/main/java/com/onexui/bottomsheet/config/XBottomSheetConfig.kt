package com.onexui.bottomsheet.config

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import com.onexui.bottomsheet.handle.DragHandleStyle

@Immutable
internal class XBottomSheetConfig internal constructor(
    val overlayBackground: Boolean,
    val dragHandle: DragHandleStyle?,
    val additionalTop: AdditionalTopConfig,
    val dismiss: DismissConfig,
    val keyboard: KeyboardConfig,
    val colors: XBottomSheetColors,
) {
    val cornerRadius: Dp = 20.dp
    val maxWidth: Dp = 512.dp
    val wideScreenThreshold: Dp = 600.dp
    val dragHandleSize: DpSize = DpSize(36.dp, 4.dp)
    val dragHandleTopPadding: Dp = 8.dp
    val additionalTopOverlap: Dp = 32.dp
    val loadingSheetHeight: Dp = 192.dp
    val scrimFadeDistance: Dp = 120.dp
    val predictiveBackMaxShift: Dp = 48.dp
    val flingVelocityThresholdPxPerSec: Float = 400f
    val resistanceMaxPx: Float = 240f
    val shape: Shape = RoundedCornerShape(
        topStart = cornerRadius,
        topEnd = cornerRadius,
        bottomStart = 0.dp,
        bottomEnd = 0.dp,
    )
}
