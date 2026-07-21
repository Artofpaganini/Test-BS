package com.onexui.bottomsheet.config

import com.onexui.bottomsheet.handle.DragHandleStyle

@XBottomSheetDsl
internal class XBottomSheetConfigBuilder {
    var overlayBackground: Boolean = true
    var dragHandle: DragHandleStyle? = DragHandleStyle.Theme
    private val additionalTopBuilder = AdditionalTopConfigBuilder()
    private val dismissBuilder = DismissConfigBuilder()
    private val keyboardBuilder = KeyboardConfigBuilder()
    private val colorsBuilder = XBottomSheetColorsBuilder()

    // Повторные вызовы мёржатся (last-write-wins): переиспользуем один билдер группы.
    fun additionalTop(configure: AdditionalTopConfigBuilder.() -> Unit) {
        additionalTopBuilder.configure()
    }

    fun dismiss(configure: DismissConfigBuilder.() -> Unit) {
        dismissBuilder.configure()
    }

    fun keyboard(configure: KeyboardConfigBuilder.() -> Unit) {
        keyboardBuilder.configure()
    }

    fun colors(configure: XBottomSheetColorsBuilder.() -> Unit) {
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
