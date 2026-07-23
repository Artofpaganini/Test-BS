package com.onexui.bottomsheet.state

internal fun SheetMetrics.toAnchorTable(skipCollapsed: Boolean): SheetAnchorTable {
    val entries = mutableListOf<SheetAnchorTable.AnchorEntry>()
    when {
        isFillMode -> {
            if (!skipCollapsed) entries.add(SheetAnchorTable.AnchorEntry(SheetValue.Collapsed, peekPx))
            entries.add(SheetAnchorTable.AnchorEntry(SheetValue.ExpandedFullScreen, maxHeightPx))
            customAnchors.forEach { anchor ->
                entries.add(SheetAnchorTable.AnchorEntry(SheetValue.Custom(anchor.key), customAnchorPx(anchor.key)))
            }
        }
        skipCollapsed ->
            entries.add(SheetAnchorTable.AnchorEntry(SheetValue.Content, anchorPx(SheetValue.Content, skipCollapsed = true)))
        contentHeightPx <= peekPx ->
            entries.add(SheetAnchorTable.AnchorEntry(SheetValue.Content, anchorPx(SheetValue.Content, skipCollapsed = false)))
        else -> {
            entries.add(SheetAnchorTable.AnchorEntry(SheetValue.Collapsed, peekPx))
            val expanded = expandTarget()
            entries.add(SheetAnchorTable.AnchorEntry(expanded, anchorPx(expanded, skipCollapsed = false)))
        }
    }
    val restEntries = entries.distinctBy { entry -> entry.anchorPx }.sortedBy { entry -> entry.anchorPx }
    return SheetAnchorTable(restEntries)
}
