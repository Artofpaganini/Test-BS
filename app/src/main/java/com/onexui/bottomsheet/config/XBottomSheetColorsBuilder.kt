package com.onexui.bottomsheet.config

import androidx.compose.ui.graphics.Color

@XBottomSheetDsl
internal class XBottomSheetColorsBuilder {
    var scrim: Color = Color.Unspecified
    var sheetBackground: Color = Color.Unspecified
    var handleTheme: Color = Color.Unspecified
    var handleStatic: Color = Color.Unspecified

    internal fun build(): XBottomSheetColors = XBottomSheetColors(scrim, sheetBackground, handleTheme, handleStatic)
}
