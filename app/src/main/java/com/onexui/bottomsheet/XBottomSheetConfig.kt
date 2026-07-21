package com.onexui.bottomsheet

import androidx.compose.runtime.Immutable
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

@DslMarker
internal annotation class XBottomSheetDsl

// Конфиг листа — ЧИСТЫЕ данные (без лямбд, правило 10): слоты additionalTop/top/bottom/middle остаются отдельными
// параметрами XBottomSheet. Value-equality (@Immutable data class) → инлайновый xBottomSheetConfig{} на call-site
// не ломает skip рекомпозиции листа (равный по значению конфиг = равный параметр), оборачивать в remember не нужно.
// Дефолты живут ТОЛЬКО в билдерах.
@Immutable
internal data class XBottomSheetConfig internal constructor(
    val overlayBackground: Boolean,
    val dragHandle: DragHandleStyle?,
    val additionalTopCornerRadius: Dp,
    val dismiss: DismissConfig,
    val keyboard: KeyboardConfig,
) {
    internal companion object {
        val Default = xBottomSheetConfig()
    }
}

@Immutable
internal data class DismissConfig(
    val onOutsideTap: Boolean,
    val onSwipeDown: Boolean,
)

@Immutable
internal data class KeyboardConfig(
    val bottomBehavior: BottomKeyboardBehavior,
)

@XBottomSheetDsl
internal class XBottomSheetConfigBuilder {
    var overlayBackground: Boolean = true
    var dragHandle: DragHandleStyle? = DragHandleStyle.Theme
    var additionalTopCornerRadius: Dp = 0.dp
    private val dismissBuilder = DismissConfigBuilder()
    private val keyboardBuilder = KeyboardConfigBuilder()

    // Повторные вызовы мёржатся last-write-wins (переиспользуем один билдер группы).
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

@XBottomSheetDsl
internal class DismissConfigBuilder {
    var onOutsideTap: Boolean = true
    var onSwipeDown: Boolean = true
    internal fun build(): DismissConfig = DismissConfig(onOutsideTap = onOutsideTap, onSwipeDown = onSwipeDown)
}

@XBottomSheetDsl
internal class KeyboardConfigBuilder {
    var bottomBehavior: BottomKeyboardBehavior = BottomKeyboardBehavior.Lift
    internal fun build(): KeyboardConfig = KeyboardConfig(bottomBehavior = bottomBehavior)
}

// DSL-фабрика конфига листа. configure исполняется сразу; вложенные dismiss{}/keyboard{} — группы флагов.
internal fun xBottomSheetConfig(configure: XBottomSheetConfigBuilder.() -> Unit = {}): XBottomSheetConfig =
    XBottomSheetConfigBuilder().apply(configure).build()
