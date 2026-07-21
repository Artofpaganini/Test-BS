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

// Полноэкранный scrim: тачи под листом гасятся ВСЕГДА (hit-таргет), затемнение опционально. Тап вне листа →
// закрытие при dismissOnOutsideTap. Тапы по самому листу не долетают — тело гасит их своим no-op
// detectTapGestures (up поглощается выше по z). Alpha разгоняется по offset листа.
@Composable
internal fun BoxScope.SheetScrim(
    state: XBottomSheetState,
    overlayBackground: Boolean,
    scrimColor: Color,
    dismissOnOutsideTap: Boolean,
    scrimFadeDistancePx: Float,
    onDismissRequest: () -> Unit,
) {
    Box(
        modifier = Modifier
            .matchParentSize()
            .pointerInput(dismissOnOutsideTap, state) {
                detectTapGestures {
                    if (dismissOnOutsideTap && !state.isDragging) onDismissRequest()
                }
            }
            .drawBehind {
                if (overlayBackground) {
                    val fade = (state.offset.value / scrimFadeDistancePx).coerceIn(0f, 1f)
                    drawRect(color = scrimColor, alpha = fade)
                }
            },
    )
}
