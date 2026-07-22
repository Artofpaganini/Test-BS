package com.onexui.bottomsheet.config

import androidx.compose.ui.graphics.Color

/** DSL-билдер цветов листа; невыставленый цвет остаётся Unspecified (резолв в корне). */
@XBottomSheetDsl
internal class XBottomSheetColorsBuilder {
    var scrim: Color = Color.Unspecified
    var sheetBackground: Color = Color.Unspecified
    var handleTheme: Color = Color.Unspecified
    var handleStatic: Color = Color.Unspecified
    internal fun build(): XBottomSheetColors = XBottomSheetColors(scrim, sheetBackground, handleTheme, handleStatic)
}
