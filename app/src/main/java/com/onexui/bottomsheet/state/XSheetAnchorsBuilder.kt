package com.onexui.bottomsheet.state

import com.onexui.bottomsheet.config.XBottomSheetDsl

@XBottomSheetDsl
internal class XSheetAnchorsBuilder {
    private val anchors = linkedMapOf<String, Float>()

    infix fun String.at(heightFraction: Float) {
        anchors[this] = heightFraction
    }

    internal fun build(): Map<String, Float> = anchors.toMap()
}
