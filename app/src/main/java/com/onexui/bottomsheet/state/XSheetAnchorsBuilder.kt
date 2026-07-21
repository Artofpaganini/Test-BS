package com.onexui.bottomsheet.state

import com.onexui.bottomsheet.config.XBottomSheetDsl

@XBottomSheetDsl
internal class XSheetAnchorsBuilder {
    private val anchors = mutableListOf<XSheetAnchor>()

    fun anchor(key: String, heightFraction: Float) {
        anchors.add(XSheetAnchor(key = key, heightFraction = heightFraction))
    }

    internal fun build(): List<XSheetAnchor> = anchors.toList()
}
