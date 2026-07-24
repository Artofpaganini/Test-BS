package com.onexui.bottomsheet.layout

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntSize

@Composable
internal fun rememberSheetDimensions(
    density: Density,
    containerSize: IntSize,
    statusBarPx: Int,
    navBarPx: Int,
    loadingSheetHeight: Dp,
    scrimFadeDistance: Dp,
    predictiveBackMaxShift: Dp,
    wideScreenThreshold: Dp,
): SheetDimensions = remember(
    density,
    containerSize.width,
    containerSize.height,
    statusBarPx,
    navBarPx,
    loadingSheetHeight,
    scrimFadeDistance,
    predictiveBackMaxShift,
    wideScreenThreshold,
) {
    with(density) {
        SheetDimensions(
            insets = SheetInsets(
                screenHeightPx = containerSize.height,
                statusBarPx = statusBarPx,
                navBarPx = navBarPx,
                loadingSheetHeightPx = loadingSheetHeight.roundToPx() + navBarPx,
            ),
            scrimFadeDistancePx = scrimFadeDistance.toPx(),
            predictiveBackMaxShiftPx = predictiveBackMaxShift.toPx(),
            isWideScreen = containerSize.width.toDp() >= wideScreenThreshold,
        )
    }
}
