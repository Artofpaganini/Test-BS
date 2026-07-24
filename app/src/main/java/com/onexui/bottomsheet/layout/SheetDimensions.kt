package com.onexui.bottomsheet.layout

import androidx.compose.runtime.Immutable

@Immutable
internal class SheetDimensions(
    val insets: SheetInsets,
    val scrimFadeDistancePx: Float,
    val predictiveBackMaxShiftPx: Float,
    val isWideScreen: Boolean,
)
