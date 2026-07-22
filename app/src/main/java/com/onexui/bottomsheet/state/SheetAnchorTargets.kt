package com.onexui.bottomsheet.state

/** Стейт, в который лист открываеться при show, по замеру и skipCollapsed. */
internal fun SheetMetrics.openTarget(skipCollapsed: Boolean): SheetValue = when {
    isFillMode -> if (skipCollapsed) SheetValue.ExpandedFullScreen else SheetValue.Collapsed
    skipCollapsed -> SheetValue.Content
    contentHeightPx <= peekPx -> SheetValue.Content
    else -> SheetValue.Collapsed
}

/** Стейт раскрытия из Collapsed: контент в пределах экрана -> ExpandedContent, иначе ExpandedFullScreen. */
internal fun SheetMetrics.expandTarget(): SheetValue = when {
    isFillMode -> SheetValue.ExpandedFullScreen
    contentHeightPx <= maxHeightPx -> SheetValue.ExpandedContent
    else -> SheetValue.ExpandedFullScreen
}

/** Высота (px) якоря для заданного стейта по текущим метрикам. */
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
