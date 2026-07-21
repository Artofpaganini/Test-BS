package com.onexui.bottomsheet.state

import kotlin.math.roundToInt

// contentHeightPx — замер контента при ограниченной высоте: короткий → натуральная высота,
// ленивый/overflow → maxHeight (isFillMode, фикс-якоря).
internal data class SheetMetrics(
    val screenHeightPx: Int,
    val statusBarPx: Int,
    val contentHeightPx: Int,
    val loadingSheetHeightPx: Int,
    val peekFraction: Float,
    val customAnchors: List<XSheetAnchor>,
) {
    // Потолок стейта: экран минус статус-бар (в покое лист под него не заходит).
    val maxHeightPx: Int get() = (screenHeightPx - statusBarPx).coerceAtLeast(0)

    val peekPx: Int get() = (screenHeightPx * peekFraction).roundToInt().coerceIn(0, maxHeightPx)

    val isFillMode: Boolean get() = contentHeightPx >= maxHeightPx

    fun customAnchorPx(key: String): Int {
        val fraction = customAnchors.firstOrNull { anchor -> anchor.key == key }?.heightFraction ?: peekFraction
        return (screenHeightPx * fraction).roundToInt().coerceIn(0, maxHeightPx)
    }
}
