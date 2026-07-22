package com.onexui.bottomsheet.state

import com.onexui.bottomsheet.config.XBottomSheetDsl

// Начальная вариация живого стейта: билдер исполняется ОДИН раз (rememberXBottomSheetState), дальше поведение
// меняется прямо на полях стейта. Правило «один файл = одна сущность» сохранено.
@XBottomSheetDsl
internal class XBottomSheetStateBuilder {
    var skipCollapsed: Boolean = false
    var initialLoading: Boolean = false
    var peekFraction: Float = 2f / 3f
    // internal для inline-групп; при переносе в public API xbet → @PublishedApi internal
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
