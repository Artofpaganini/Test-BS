package com.onexui.bottomsheet

import kotlin.math.roundToInt

// Шесть значений стейт-машины высоты листа (§4 спеки). Значение ВЫЧИСЛЯЕТСЯ по метрикам и фактам
// (skipCollapsed, isLoading, высота контента), а не задаётся разработчиком напрямую.
internal enum class SheetValue {
    Hidden,
    Content,
    Collapsed,
    ExpandedContent,
    ExpandedFullScreen,
    Loading,
}

// Метрики экрана и контента. Пересчитываются при каждом изменении размеров (поворот, resize, рост контента).
internal data class SheetMetrics(
    val screenHeightPx: Int,
    val statusBarPx: Int,
    val contentHeightPx: Int,
    val loadingSheetHeightPx: Int,
) {
    // Потолок любого стейта: экран минус статус-бар (под статус-бар лист не заходит).
    val maxHeightPx: Int get() = (screenHeightPx - statusBarPx).coerceAtLeast(0)

    // Высота Collapsed — 60% экрана.
    val collapsedPx: Int get() = (screenHeightPx * XBottomSheetDefaults.CollapsedFraction).roundToInt()
}

// Целевой стейт при открытии.
internal fun SheetMetrics.openTarget(skipCollapsed: Boolean): SheetValue = when {
    skipCollapsed ->
        if (contentHeightPx <= maxHeightPx) SheetValue.Content else SheetValue.ExpandedFullScreen
    contentHeightPx <= collapsedPx -> SheetValue.Content
    else -> SheetValue.Collapsed
}

// Целевой стейт при развороте из Collapsed.
internal fun SheetMetrics.expandTarget(): SheetValue =
    if (contentHeightPx <= maxHeightPx) SheetValue.ExpandedContent else SheetValue.ExpandedFullScreen

// Высота (px) для конкретного стейта. Content зависит от skipCollapsed: без него лимит 60%, с ним — Status Bar.
internal fun SheetMetrics.anchorPx(value: SheetValue, skipCollapsed: Boolean): Int = when (value) {
    SheetValue.Hidden -> 0
    SheetValue.Loading -> loadingSheetHeightPx
    SheetValue.Content ->
        if (skipCollapsed) minOf(contentHeightPx, maxHeightPx) else minOf(contentHeightPx, collapsedPx)
    SheetValue.Collapsed -> collapsedPx
    SheetValue.ExpandedContent -> minOf(contentHeightPx, maxHeightPx)
    SheetValue.ExpandedFullScreen -> maxHeightPx
}
