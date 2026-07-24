package com.onexui.bottomsheet.state

internal fun SheetMetrics.toAnchorTable(isSkipCollapsed: Boolean): SheetAnchorTable {
    val entries = mutableListOf<SheetAnchorTable.AnchorEntry>()
    when {
        isFillMode -> {
            if (!isSkipCollapsed) entries.add(SheetAnchorTable.AnchorEntry(SheetValue.Collapsed, collapsedPx))
            entries.add(SheetAnchorTable.AnchorEntry(SheetValue.ExpandedFullScreen, maxHeightPx))
            customAnchors.forEach { anchor ->
                entries.add(SheetAnchorTable.AnchorEntry(SheetValue.Custom(anchor), customAnchorPx(anchor)))
            }
        }
        isSkipCollapsed ->
            entries.add(SheetAnchorTable.AnchorEntry(SheetValue.Content, anchorPx(SheetValue.Content, isSkipCollapsed = true)))
        contentHeightPx <= collapsedPx ->
            entries.add(SheetAnchorTable.AnchorEntry(SheetValue.Content, anchorPx(SheetValue.Content, isSkipCollapsed = false)))
        else -> {
            entries.add(SheetAnchorTable.AnchorEntry(SheetValue.Collapsed, collapsedPx))
            val expanded = expandTarget()
            entries.add(SheetAnchorTable.AnchorEntry(expanded, anchorPx(expanded, isSkipCollapsed = false)))
        }
    }
    val restEntries = entries.distinctBy { entry -> entry.anchorPx }.sortedBy { entry -> entry.anchorPx }
    return SheetAnchorTable(restEntries)
}
