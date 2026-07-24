package com.onexui.bottomsheet.state

import com.onexui.bottomsheet.anchor.AnchorState
import com.onexui.bottomsheet.config.XBottomSheetDsl

@XBottomSheetDsl
internal class XSheetAnchorsBuilder {
    private val anchors = linkedSetOf<AnchorState>()

    operator fun AnchorState.unaryPlus() {
        anchors.add(this)
    }

    internal fun build(): Set<AnchorState> = anchors.toSet()
}
