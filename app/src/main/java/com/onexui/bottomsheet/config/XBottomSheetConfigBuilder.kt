package com.onexui.bottomsheet.config

import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.onexui.bottomsheet.handle.DragHandleStyle

@XBottomSheetDsl
internal class XBottomSheetConfigBuilder {
    var overlayBackground: Boolean = true
    var dragHandle: DragHandleStyle? = DragHandleStyle.Theme
    var additionalTopCornerRadius: Dp = 0.dp
    private val dismissBuilder = DismissConfigBuilder()
    private val keyboardBuilder = KeyboardConfigBuilder()

    // Повторные вызовы мёржатся (last-write-wins): переиспользуем один билдер группы.
    fun dismiss(configure: DismissConfigBuilder.() -> Unit) {
        dismissBuilder.configure()
    }

    fun keyboard(configure: KeyboardConfigBuilder.() -> Unit) {
        keyboardBuilder.configure()
    }

    internal fun build(): XBottomSheetConfig = XBottomSheetConfig(
        overlayBackground = overlayBackground,
        dragHandle = dragHandle,
        additionalTopCornerRadius = additionalTopCornerRadius,
        dismiss = dismissBuilder.build(),
        keyboard = keyboardBuilder.build(),
    )
}
