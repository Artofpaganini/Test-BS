package com.onexui.bottomsheet

import androidx.compose.runtime.Immutable
import kotlin.math.roundToInt

// Значение стейта высоты листа. Режим определяется ЗАМЕРОМ контента (SubcomposeLayout при ограниченной высоте):
//  · wrap-режим (короткий/измеримый контент, contentHeight < экрана): Content / Collapsed(peek) / ExpandedContent
//    по высоте контента — как в дизайн-спеке;
//  · fill-режим (LazyColumn ИЛИ контент заполняет экран, contentHeight == maxHeight): Collapsed(peek) /
//    ExpandedFullScreen(full) + пользовательские якоря Custom(key).
sealed interface SheetValue {
    data object Hidden : SheetValue
    data object Content : SheetValue
    data object Collapsed : SheetValue
    data object ExpandedContent : SheetValue
    data object ExpandedFullScreen : SheetValue
    data object Loading : SheetValue
    data class Custom(val key: String) : SheetValue
}

// Пользовательский якорь: ключ + доля высоты экрана (0..1). Разраб задаёт свои промежуточные якоря (fill-режим).
@Immutable
data class XSheetAnchor(val key: String, val heightFraction: Float)

// Метрики экрана и контента. contentHeightPx — ЗАМЕР SubcomposeLayout при Constraints(maxHeight):
// короткий контент → его натуральная высота; LazyColumn/overflow → maxHeight (заполняет). fills = заполнил
// доступную высоту → fill-режим (фикс-якоря). peekFraction — высота Collapsed; customAnchors — доп. якоря.
internal data class SheetMetrics(
    val screenHeightPx: Int,
    val statusBarPx: Int,
    val contentHeightPx: Int,
    val loadingSheetHeightPx: Int,
    val peekFraction: Float,
    val customAnchors: List<XSheetAnchor>,
) {
    // Потолок любого стейта: экран минус статус-бар (под статус-бар лист не заходит в покое).
    val maxHeightPx: Int get() = (screenHeightPx - statusBarPx).coerceAtLeast(0)

    // Высота Collapsed (peek) — доля экрана, но не выше потолка.
    val peekPx: Int get() = (screenHeightPx * peekFraction).roundToInt().coerceIn(0, maxHeightPx)

    // Контент заполнил доступную высоту (замер при maxHeight вернул maxHeight) — LazyColumn или контент > экрана.
    val fills: Boolean get() = contentHeightPx >= maxHeightPx

    fun customAnchorPx(key: String): Int {
        val fraction = customAnchors.firstOrNull { anchor -> anchor.key == key }?.heightFraction ?: peekFraction
        return (screenHeightPx * fraction).roundToInt().coerceIn(0, maxHeightPx)
    }
}

// Целевой стейт при открытии.
internal fun SheetMetrics.openTarget(skipCollapsed: Boolean): SheetValue = when {
    fills -> if (skipCollapsed) SheetValue.ExpandedFullScreen else SheetValue.Collapsed
    skipCollapsed -> SheetValue.Content
    contentHeightPx <= peekPx -> SheetValue.Content
    else -> SheetValue.Collapsed
}

// Целевой стейт при развороте из Collapsed.
internal fun SheetMetrics.expandTarget(): SheetValue = when {
    fills -> SheetValue.ExpandedFullScreen
    contentHeightPx <= maxHeightPx -> SheetValue.ExpandedContent
    else -> SheetValue.ExpandedFullScreen
}

// Высота (px) для конкретного стейта. Content зависит от skipCollapsed: без него лимит peek, с ним — Status Bar.
internal fun SheetMetrics.anchorPx(value: SheetValue, skipCollapsed: Boolean): Int = when (value) {
    SheetValue.Hidden -> 0
    SheetValue.Loading -> loadingSheetHeightPx
    SheetValue.Content ->
        if (skipCollapsed) minOf(contentHeightPx, maxHeightPx) else minOf(contentHeightPx, peekPx)
    SheetValue.Collapsed -> peekPx
    SheetValue.ExpandedContent -> minOf(contentHeightPx, maxHeightPx)
    SheetValue.ExpandedFullScreen -> maxHeightPx
    is SheetValue.Custom -> customAnchorPx(value.key)
}
