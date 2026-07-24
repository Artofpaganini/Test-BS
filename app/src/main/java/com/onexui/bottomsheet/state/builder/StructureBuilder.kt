package com.onexui.bottomsheet.state.builder

import com.onexui.bottomsheet.anchor.AnchorState
import com.onexui.bottomsheet.config.XBottomSheetDsl
import com.onexui.bottomsheet.state.XSheetAnchorsBuilder

@XBottomSheetDsl
internal class StructureBuilder {
    var isSkipCollapsed: Boolean = false
    var isInitialLoading: Boolean = false
    var peekFraction: Float = DEFAULT_PEEK_FRACTION

    internal val anchorsBuilder = XSheetAnchorsBuilder()

    inline fun anchors(configure: XSheetAnchorsBuilder.() -> Unit) {
        anchorsBuilder.configure()
    }

    internal fun buildAnchors(): Set<AnchorState> = anchorsBuilder.build()

    private companion object {
        const val DEFAULT_PEEK_FRACTION = 2f / 3f
    }
}
