package com.onexui.bottomsheet.state

import com.onexui.bottomsheet.config.XBottomSheetDsl

/**
 * DSL-билдер начальной вариации стейта: исполняется ОДИН раз (rememberXBottomSheetState), дальше поведени
 * меняется прямо на живых полях стейта.
 */
@XBottomSheetDsl
internal class XBottomSheetStateBuilder {
    var skipCollapsed: Boolean = false
    var initialLoading: Boolean = false
    var peekFraction: Float = 2f / 3f

    // internal для доступа из inline-групп; при переносе в public API xbet -> @PublishedApi internal.
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
