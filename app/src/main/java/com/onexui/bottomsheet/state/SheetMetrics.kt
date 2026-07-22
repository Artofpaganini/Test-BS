package com.onexui.bottomsheet.state

import kotlin.math.roundToInt

/**
 * Замеренные размеры листа, из которых считаются якоря. contentHeightPx — замер контена при ограниченной
 * высоте: короткий -> натуральная высота, ленивый/overflow -> maxHeight (isFillMode, фикс-якоря).
 *
 * @property maxHeightPx потолок стейта: экран минус статус-бар (в покое лист под него не заходит).
 * @property isFillMode заполнил ли контент экран (fill-режим с фикс-якорями vs wrap по контенту).
 */
internal data class SheetMetrics(
    val screenHeightPx: Int,
    val statusBarPx: Int,
    val contentHeightPx: Int,
    val loadingSheetHeightPx: Int,
    val peekFraction: Float,
    val customAnchors: List<XSheetAnchor>,
) {
    val maxHeightPx: Int get() = (screenHeightPx - statusBarPx).coerceAtLeast(0)

    val peekPx: Int get() = (screenHeightPx * peekFraction).roundToInt().coerceIn(0, maxHeightPx)

    val isFillMode: Boolean get() = contentHeightPx >= maxHeightPx

    /** Высота (px) кастомного якоря по ключу; неизвестный ключ -> peekFraction. */
    fun customAnchorPx(key: String): Int {
        val fraction = customAnchors.firstOrNull { anchor -> anchor.key == key }?.heightFraction ?: peekFraction
        return (screenHeightPx * fraction).roundToInt().coerceIn(0, maxHeightPx)
    }
}
