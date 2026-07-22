package com.onexui.bottomsheet.layout

import androidx.compose.runtime.State
import androidx.compose.ui.layout.IntrinsicMeasurable
import androidx.compose.ui.layout.IntrinsicMeasureScope
import androidx.compose.ui.layout.Measurable
import androidx.compose.ui.layout.MeasurePolicy
import androidx.compose.ui.layout.MeasureResult
import androidx.compose.ui.layout.MeasureScope
import androidx.compose.ui.unit.Constraints
import org.xplatform.uikit.compose.modifier.keyboard.lift.KeyboardLiftState
import kotlin.math.roundToInt

/**
 * MeasurePolicy режима StayUnderKeyboard: раскладывает middle (скролл) и bottom (sticky). При IME middle кончается
 * у верхней кромки клавиатры, bottom уходит под неё; без IME bottom стоит над nav bar. IME/navBar читаются в
 * measure-фазе (State) — реакция без рекомпозиции.
 */
internal class StayUnderKeyboardMeasurePolicy(
    private val keyboardState: State<KeyboardLiftState>,
    private val navBarState: State<Int>,
) : MeasurePolicy {

    override fun MeasureScope.measure(
        measurables: List<Measurable>,
        constraints: Constraints,
    ): MeasureResult {
        val width = constraints.maxWidth
        // IME/navBar читаем в layout-фазе: инвалидация measure узла при их смене, без рекомпозиции.
        val navBarPx = navBarState.value
        val keyboardHeightPx = keyboardState.value.let { liftState ->
            if (liftState.isKeyboardVisible) liftState.keyboardHeight.roundToInt() else 0
        }
        val isKeyboardVisible = keyboardHeightPx > 0
        val bottomPlaceable = measurables[1].measure(
            constraints.copy(minHeight = 0, maxHeight = Constraints.Infinity),
        )
        val bottomHeight = bottomPlaceable.height
        // Гард intrinsic-Infinity: override maxIntrinsicHeight обычно перехватывает; здесь — натуральная раскладка на всякий случай.
        if (constraints.maxHeight == Constraints.Infinity) {
            val middleNatural = measurables[0].measure(
                constraints.copy(minHeight = 0, maxHeight = Constraints.Infinity),
            )
            val naturalHeight = middleNatural.height + bottomHeight
            return layout(width, naturalHeight) {
                middleNatural.place(x = 0, y = 0)
                bottomPlaceable.place(x = 0, y = middleNatural.height)
            }
        }
        val height = constraints.maxHeight
        // Резерв под middle: при IME — высота клавиатуры (middle кончается у её верха); без IME — bottom + nav bar.
        val reservePx = if (isKeyboardVisible) keyboardHeightPx else bottomHeight + navBarPx
        val middleMaxHeight = (height - reservePx).coerceAtLeast(0)
        val middlePlaceable = measurables[0].measure(
            constraints.copy(minHeight = 0, maxHeight = middleMaxHeight),
        )
        // Позиция bottom: без IME — над nav bar; при IME — прижат к низу экрана (уходит под клавиатуру).
        val bottomY = if (isKeyboardVisible) height - bottomHeight else height - bottomHeight - navBarPx
        return layout(width, height) {
            middlePlaceable.place(x = 0, y = 0)
            bottomPlaceable.place(x = 0, y = bottomY.coerceAtLeast(0))
        }
    }

    /** Натуральная высота = middle + bottom + nav bar. Без override measure с Infinity дал бы мусор в contentHeightPx и краш. */
    override fun IntrinsicMeasureScope.maxIntrinsicHeight(
        measurables: List<IntrinsicMeasurable>,
        width: Int,
    ): Int = measurables[0].maxIntrinsicHeight(width) +
        measurables[1].maxIntrinsicHeight(width) +
        navBarState.value
}
