package com.onexui.bottomsheet.config

import com.onexui.bottomsheet.handle.DragHandleStyle

@XBottomSheetDsl
internal class XBottomSheetConfigBuilder {
    var overlayBackground: Boolean = true
    var dragHandle: DragHandleStyle? = DragHandleStyle.Theme
    // internal для inline-групп; при переносе в public API xbet → @PublishedApi internal
    internal val additionalTopBuilder = AdditionalTopConfigBuilder()
    internal val dismissBuilder = DismissConfigBuilder()
    internal val keyboardBuilder = KeyboardConfigBuilder()
    internal val colorsBuilder = XBottomSheetColorsBuilder()

    // Повторные вызовы мёржатся (last-write-wins): переиспользуем один билдер группы.
    inline fun additionalTop(configure: AdditionalTopConfigBuilder.() -> Unit) {
        additionalTopBuilder.configure()
    }

    inline fun dismiss(configure: DismissConfigBuilder.() -> Unit) {
        dismissBuilder.configure()
    }

    inline fun keyboard(configure: KeyboardConfigBuilder.() -> Unit) {
        keyboardBuilder.configure()
    }

    inline fun colors(configure: XBottomSheetColorsBuilder.() -> Unit) {
        colorsBuilder.configure()
    }

    internal fun build(): XBottomSheetConfig = XBottomSheetConfig(
        overlayBackground = overlayBackground,
        dragHandle = dragHandle,
        additionalTop = additionalTopBuilder.build(),
        dismiss = dismissBuilder.build(),
        keyboard = keyboardBuilder.build(),
        colors = colorsBuilder.build(),
    )
}
