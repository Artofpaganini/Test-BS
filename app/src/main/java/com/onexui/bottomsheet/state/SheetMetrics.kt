package com.onexui.bottomsheet.state

import kotlin.math.roundToInt

internal data class SheetMetrics(
    val screenHeightPx: Int,
    val statusBarPx: Int,
    val contentHeightPx: Int,
    val loadingSheetHeightPx: Int,
    val peekFraction: Float,
    val customAnchors: Map<String, Float>,
) {
    val maxHeightPx: Int get() = (screenHeightPx - statusBarPx).coerceAtLeast(0)

    val peekPx: Int get() = (screenHeightPx * peekFraction).roundToInt().coerceIn(0, maxHeightPx)

    val isFillMode: Boolean get() = contentHeightPx >= maxHeightPx

    fun customAnchorPx(key: String): Int {
        val fraction = customAnchors[key] ?: peekFraction
        return (screenHeightPx * fraction).roundToInt().coerceIn(0, maxHeightPx)
    }
}
