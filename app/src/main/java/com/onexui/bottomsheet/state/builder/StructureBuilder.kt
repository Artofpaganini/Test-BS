package com.onexui.bottomsheet.state.builder

import com.onexui.bottomsheet.anchor.AnchorState
import com.onexui.bottomsheet.config.XBottomSheetDsl

@XBottomSheetDsl
internal class StructureBuilder {
    var isSkipCollapsed: Boolean = false
    var isInitialLoading: Boolean = false
    var peekFraction: Float = DEFAULT_PEEK_FRACTION

    private var anchors: Set<AnchorState> = emptySet()

    fun anchors(vararg states: AnchorState) {
        anchors = states.toSet()
    }

    internal fun buildAnchors(): Set<AnchorState> = anchors

    private companion object {
        const val DEFAULT_PEEK_FRACTION = 2f / 3f
    }
}
