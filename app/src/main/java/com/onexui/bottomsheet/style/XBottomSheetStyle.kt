package com.onexui.bottomsheet.style

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import com.onexui.bottomsheet.config.XBottomSheetColors
import com.onexui.bottomsheet.config.XBottomSheetColorsBuilder
import com.onexui.bottomsheet.config.XBottomSheetDsl
import com.onexui.bottomsheet.handle.DragHandleStyle

@Immutable
internal data class XBottomSheetStyle(
    val isOverlayBackground: Boolean,
    val dragHandleStyle: DragHandleStyle?,
    val additionalTop: AdditionalTopStyle,
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
    val shape: Shape
        get() = RoundedCornerShape(
            topStart = cornerRadius,
            topEnd = cornerRadius,
            bottomStart = 0.dp,
            bottomEnd = 0.dp,
        )
}

internal fun defaultXBottomSheetStyle(): XBottomSheetStyle = XBottomSheetStyle(
    isOverlayBackground = true,
    dragHandleStyle = DragHandleStyle.Theme,
    additionalTop = AdditionalTopStyle(cornerRadius = 0.dp, backgroundColor = Color.Unspecified),
    colors = XBottomSheetColors(scrim = Color.Unspecified, sheetBackground = Color.Unspecified),
)

@XBottomSheetDsl
internal class XBottomSheetStyleBuilder(current: XBottomSheetStyle) {
    var isOverlayBackground: Boolean = current.isOverlayBackground
    var dragHandleStyle: DragHandleStyle? = current.dragHandleStyle

    internal val additionalTopBuilder = AdditionalTopStyleBuilder(current.additionalTop)
    internal val colorsBuilder = XBottomSheetColorsBuilder(current.colors)

    inline fun additionalTop(configure: AdditionalTopStyleBuilder.() -> Unit) {
        additionalTopBuilder.configure()
    }

    inline fun colors(configure: XBottomSheetColorsBuilder.() -> Unit) {
        colorsBuilder.configure()
    }

    internal fun build(): XBottomSheetStyle = XBottomSheetStyle(
        isOverlayBackground = isOverlayBackground,
        dragHandleStyle = dragHandleStyle,
        additionalTop = additionalTopBuilder.build(),
        colors = colorsBuilder.build(),
    )
}
