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

internal class StayUnderKeyboardMeasurePolicy(
    private val keyboardState: State<KeyboardLiftState>,
    private val navBarState: State<Int>,
) : MeasurePolicy {
    override fun MeasureScope.measure(
        measurables: List<Measurable>,
        constraints: Constraints,
    ): MeasureResult {
        val width = constraints.maxWidth
        val navBarPx = navBarState.value
        val keyboardHeightPx = keyboardState.value.keyboardHeight.roundToInt().coerceAtLeast(0)
        val navReservePx = (navBarPx - keyboardHeightPx).coerceAtLeast(0)
        val bottomPlaceable = measurables[1].measure(
            constraints.copy(minHeight = 0, maxHeight = Constraints.Infinity),
        )
        val bottomHeight = bottomPlaceable.height
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
        val reservePx = maxOf(keyboardHeightPx, bottomHeight + navReservePx)
        val middleMaxHeight = (height - reservePx).coerceAtLeast(0)
        val middlePlaceable = measurables[0].measure(
            constraints.copy(minHeight = 0, maxHeight = middleMaxHeight),
        )
        val bottomY = height - bottomHeight - navReservePx
        return layout(width, height) {
            middlePlaceable.place(x = 0, y = 0)
            bottomPlaceable.place(x = 0, y = bottomY.coerceAtLeast(0))
        }
    }

    override fun IntrinsicMeasureScope.maxIntrinsicHeight(
        measurables: List<IntrinsicMeasurable>,
        width: Int,
    ): Int = measurables[0].maxIntrinsicHeight(width) +
        measurables[1].maxIntrinsicHeight(width) +
        navBarState.value
}
