package com.onexui.bottomsheet.layout

import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import com.onexui.bottomsheet.state.XBottomSheetState

@Composable
internal fun BoxScope.SheetScrim(
    state: XBottomSheetState,
    isOverlayBackground: Boolean,
    scrimColor: Color,
    isDismissOnOutsideTap: Boolean,
    scrimFadeDistancePx: Float,
    onDismissRequest: () -> Unit,
) {
    Box(
        modifier = Modifier
            .matchParentSize()
            .pointerInput(isDismissOnOutsideTap, state) {
                detectTapGestures {
                    if (isDismissOnOutsideTap && !state.isDragging) onDismissRequest()
                }
            }
            .drawBehind {
                if (isOverlayBackground) {
                    val fade = (state.offset.value / scrimFadeDistancePx).coerceIn(0f, 1f)
                    drawRect(color = scrimColor, alpha = fade)
                }
            },
    )
}
