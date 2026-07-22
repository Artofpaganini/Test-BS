package com.onexui.bottomsheet.state

import com.onexui.bottomsheet.config.XBottomSheetDsl

@XBottomSheetDsl
internal class XBottomSheetStateConfigBuilder {
    var skipCollapsed: Boolean = false
    var initialLoading: Boolean = false
    var peekFraction: Float = 2f / 3f
    private val anchorsBuilder = XSheetAnchorsBuilder()

    fun anchors(configure: XSheetAnchorsBuilder.() -> Unit) {
        anchorsBuilder.configure()
    }

    internal fun build(): XBottomSheetStateConfig = XBottomSheetStateConfig(
        skipCollapsed = skipCollapsed,
        initialLoading = initialLoading,
        peekFraction = peekFraction,
        anchors = anchorsBuilder.build(),
    )
}
