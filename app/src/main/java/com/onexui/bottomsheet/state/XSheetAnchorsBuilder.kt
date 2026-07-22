package com.onexui.bottomsheet.state

import com.onexui.bottomsheet.config.XBottomSheetDsl

// Якоря действуют только в fill-режиме (контент ≥ экрана); в wrap-режиме игнорируются.
@XBottomSheetDsl
internal class XSheetAnchorsBuilder {
    private val anchors = mutableListOf<XSheetAnchor>()

    infix fun String.at(heightFraction: Float) {
        anchors.add(XSheetAnchor(key = this, heightFraction = heightFraction))
    }

    internal fun build(): List<XSheetAnchor> = anchors.toList()
}
