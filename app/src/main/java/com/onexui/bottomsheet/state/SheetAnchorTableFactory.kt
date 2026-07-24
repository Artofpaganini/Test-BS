package com.onexui.bottomsheet.state

internal fun SheetMetrics.toAnchorTable(isSkipCollapsed: Boolean): SheetAnchorTable {
    val entries = mutableListOf<SheetAnchorTable.AnchorEntry>()
    when {
        isFillMode -> {
            if (!isSkipCollapsed) entries.add(SheetAnchorTable.AnchorEntry(SheetValue.Collapsed, peekPx))
            entries.add(SheetAnchorTable.AnchorEntry(SheetValue.ExpandedFullScreen, maxHeightPx))
            customAnchors.keys.forEach { key ->
                entries.add(SheetAnchorTable.AnchorEntry(SheetValue.Custom(key), customAnchorPx(key)))
            }
        }
        isSkipCollapsed ->
            entries.add(SheetAnchorTable.AnchorEntry(SheetValue.Content, anchorPx(SheetValue.Content, isSkipCollapsed = true)))
        contentHeightPx <= peekPx ->
            entries.add(SheetAnchorTable.AnchorEntry(SheetValue.Content, anchorPx(SheetValue.Content, isSkipCollapsed = false)))
        else -> {
            entries.add(SheetAnchorTable.AnchorEntry(SheetValue.Collapsed, peekPx))
            val expanded = expandTarget()
            entries.add(SheetAnchorTable.AnchorEntry(expanded, anchorPx(expanded, isSkipCollapsed = false)))
        }
    }
    val restEntries = entries.distinctBy { entry -> entry.anchorPx }.sortedBy { entry -> entry.anchorPx }
    return SheetAnchorTable(restEntries)
}
