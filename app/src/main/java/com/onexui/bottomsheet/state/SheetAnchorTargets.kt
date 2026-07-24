package com.onexui.bottomsheet.state

internal fun SheetMetrics.openTarget(isSkipCollapsed: Boolean): SheetValue = when {
    isFillMode -> if (isSkipCollapsed) SheetValue.ExpandedFullScreen else SheetValue.Collapsed
    isSkipCollapsed -> SheetValue.Content
    contentHeightPx <= peekPx -> SheetValue.Content
    else -> SheetValue.Collapsed
}

internal fun SheetMetrics.expandTarget(): SheetValue = when {
    isFillMode -> SheetValue.ExpandedFullScreen
    contentHeightPx <= maxHeightPx -> SheetValue.ExpandedContent
    else -> SheetValue.ExpandedFullScreen
}

internal fun SheetMetrics.anchorPx(value: SheetValue, isSkipCollapsed: Boolean): Int = when (value) {
    SheetValue.Hidden -> 0
    SheetValue.Loading -> loadingSheetHeightPx
    SheetValue.Content ->
        if (isSkipCollapsed) minOf(contentHeightPx, maxHeightPx) else minOf(contentHeightPx, peekPx)
    SheetValue.Collapsed -> peekPx
    SheetValue.ExpandedContent -> minOf(contentHeightPx, maxHeightPx)
    SheetValue.ExpandedFullScreen -> maxHeightPx
    is SheetValue.Custom -> customAnchorPx(value.anchor)
}
