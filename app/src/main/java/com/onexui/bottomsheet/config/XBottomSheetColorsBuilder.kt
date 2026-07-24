package com.onexui.bottomsheet.config

import androidx.compose.ui.graphics.Color

@XBottomSheetDsl
internal class XBottomSheetColorsBuilder(current: XBottomSheetColors) {
    var scrim: Color = current.scrim
    var sheetBackground: Color = current.sheetBackground

    internal fun build(): XBottomSheetColors = XBottomSheetColors(scrim = scrim, sheetBackground = sheetBackground)
}
