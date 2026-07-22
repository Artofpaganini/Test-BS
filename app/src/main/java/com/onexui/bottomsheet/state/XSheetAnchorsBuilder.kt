package com.onexui.bottomsheet.state

import com.onexui.bottomsheet.config.XBottomSheetDsl

/**
 * DSL-набор кастомных rest-якорей листа: `"half" at 0.5f` (доля высоты экрана 0..1).
 * Якоря действуют только в fill-режиме (контент ≥ экрана); в wrap-режиме игнорирутся.
 */
@XBottomSheetDsl
internal class XSheetAnchorsBuilder {
    private val anchors = mutableListOf<XSheetAnchor>()

    /** Добавляет якорь `key at heightFraction` (доля высоты экрана 0..1). */
    infix fun String.at(heightFraction: Float) {
        anchors.add(XSheetAnchor(key = this, heightFraction = heightFraction))
    }

    internal fun build(): List<XSheetAnchor> = anchors.toList()
}
