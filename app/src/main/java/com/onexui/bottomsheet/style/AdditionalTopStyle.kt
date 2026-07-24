package com.onexui.bottomsheet.style

import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import com.onexui.bottomsheet.config.XBottomSheetDsl

@Immutable
internal data class AdditionalTopStyle(
    val cornerRadius: Dp,
    val backgroundColor: Color,
)

@XBottomSheetDsl
internal class AdditionalTopStyleBuilder(current: AdditionalTopStyle) {
    var cornerRadius: Dp = current.cornerRadius
    var backgroundColor: Color = current.backgroundColor

    internal fun build(): AdditionalTopStyle =
        AdditionalTopStyle(cornerRadius = cornerRadius, backgroundColor = backgroundColor)
}
