package com.onexui.bottomsheet.state

import com.onexui.bottomsheet.config.XBottomSheetDsl

@XBottomSheetDsl
internal class XBottomSheetStateBuilder {
    var skipCollapsed: Boolean = false
    var initialLoading: Boolean = false
    var peekFraction: Float = 2f / 3f

    internal val anchorsBuilder = XSheetAnchorsBuilder()

    inline fun anchors(configure: XSheetAnchorsBuilder.() -> Unit) {
        anchorsBuilder.configure()
    }

    internal fun buildState(): XBottomSheetState = XBottomSheetState(
        skipCollapsed = skipCollapsed,
        initialLoading = initialLoading,
        peekFraction = peekFraction,
        anchors = anchorsBuilder.build(),
    )
}
