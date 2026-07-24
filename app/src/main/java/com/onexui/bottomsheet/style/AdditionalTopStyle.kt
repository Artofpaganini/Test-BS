package com.onexui.bottomsheet.style

import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.onexui.bottomsheet.config.XBottomSheetDsl

internal val MAX_ADDITIONAL_TOP_PEEK: Dp = 20.dp

@Immutable
internal data class AdditionalTopStyle(
    val cornerRadius: Dp,
    val backgroundColor: Color,
    val peek: Dp,
)

@XBottomSheetDsl
internal class AdditionalTopStyleBuilder(current: AdditionalTopStyle) {
    var cornerRadius: Dp = current.cornerRadius
    var backgroundColor: Color = current.backgroundColor
    var peek: Dp = current.peek

    internal fun build(): AdditionalTopStyle =
        AdditionalTopStyle(
            cornerRadius = cornerRadius,
            backgroundColor = backgroundColor,
            peek = peek.coerceIn(0.dp, MAX_ADDITIONAL_TOP_PEEK),
        )
}
