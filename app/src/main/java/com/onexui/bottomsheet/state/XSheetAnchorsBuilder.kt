package com.onexui.bottomsheet.state

import com.onexui.bottomsheet.config.XBottomSheetDsl

// Якоря действуют только в fill-режиме (контент ≥ экрана); в wrap-режиме игнорируются.
@XBottomSheetDsl
internal class XSheetAnchorsBuilder {
    private val anchors = mutableListOf<XSheetAnchor>()

    // Канон DraggableAnchors: `"half" at 0.5f`. member-ext — infix виден только в скоупе билдера anchors { }.
    infix fun String.at(heightFraction: Float) {
        anchors.add(XSheetAnchor(key = this, heightFraction = heightFraction))
    }

    internal fun build(): List<XSheetAnchor> = anchors.toList()
}
