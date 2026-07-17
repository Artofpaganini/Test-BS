package com.alva.core.uikit.components.bottom_sheet.fling

import androidx.compose.foundation.gestures.AnchoredDraggableDefaults
import androidx.compose.foundation.gestures.FlingBehavior
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import com.alva.core.uikit.components.bottom_sheet.AlvaBottomSheetStateHolder
import com.alva.core.uikit.components.bottom_sheet.config.AlvaBottomSheetConfig

@Composable
fun rememberAlvaBottomSheetFlingBehavior(
    state: AlvaBottomSheetStateHolder,
    config: AlvaBottomSheetConfig,
): FlingBehavior {
    val positionalThreshold = remember(config.positionalThresholdFraction) {
        { distance: Float -> distance * config.positionalThresholdFraction }
    }
    return AnchoredDraggableDefaults.flingBehavior(
        state = state.draggableState,
        positionalThreshold = positionalThreshold,
    )
}
