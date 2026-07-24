package com.onexui.bottomsheet.state

import com.onexui.bottomsheet.anchor.AnchorState
import kotlin.math.roundToInt

internal data class SheetMetrics(
    val screenHeightPx: Int,
    val statusBarPx: Int,
    val contentHeightPx: Int,
    val loadingSheetHeightPx: Int,
    val peekFraction: Float,
    val customAnchors: Set<AnchorState>,
) {
    val maxHeightPx: Int get() = (screenHeightPx - statusBarPx).coerceAtLeast(0)

    val peekPx: Int get() = (screenHeightPx * peekFraction).roundToInt().coerceIn(0, maxHeightPx)

    val isFillMode: Boolean get() = contentHeightPx >= maxHeightPx

    fun customAnchorPx(anchor: AnchorState): Int {
        val fraction = anchor.fraction
        val px = when {
            fraction != null -> (screenHeightPx * fraction).roundToInt()
            anchor is AnchorState.FullScreen -> maxHeightPx
            anchor is AnchorState.WrapContent -> minOf(contentHeightPx, maxHeightPx)
            else -> peekPx
        }
        return px.coerceIn(0, maxHeightPx)
    }
}
