package com.onexui.bottomsheet

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.IntrinsicMeasurable
import androidx.compose.ui.layout.IntrinsicMeasureScope
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.Measurable
import androidx.compose.ui.layout.MeasurePolicy
import androidx.compose.ui.layout.MeasureResult
import androidx.compose.ui.layout.MeasureScope
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Constraints

// Состояния Additional Top (§1). Переключаются ТОЛЬКО внешними факторами (кнопка/логика экрана),
// не жестами листа. Кейс: «Добавить в купон» / «Отслеживать».
internal enum class AdditionalTopState { Expanded, Collapsed }

// Стек «Additional Top + основная часть» со стики-перекрытием (§1, §E):
// Expanded — карточка раскрыта, её нижние 32dp утоплены под основную часть (overlap); контент виден.
// Collapsed — видна полоска 20dp (peek), контент карточки в Alpha 0.
// Основная часть (surface) рисуется ПОВЕРХ карточки (кладётся позже) → перекрытие корректно по z.
// Высота карточки в contentHeight входит видимой частью (peek / natural-overlap); смену высоты листа
// плавно доигрывает onContentRemeasured, а контент карточки — плавным fade alpha.
@Composable
internal fun AdditionalTopStack(
    additionalTopState: AdditionalTopState,
    card: @Composable () -> Unit,
    surface: @Composable () -> Unit,
) {
    val density = LocalDensity.current
    val overlapPx = with(density) { XBottomSheetDefaults.AdditionalTopOverlap.roundToPx() }
    val peekPx = with(density) { XBottomSheetDefaults.AdditionalTopPeek.roundToPx() }
    val alpha by animateFloatAsState(
        targetValue = if (additionalTopState == AdditionalTopState.Expanded) 1f else 0f,
        label = "additionalTopAlpha",
    )
    val measurePolicy = remember(additionalTopState, overlapPx, peekPx) {
        AdditionalTopMeasurePolicy(additionalTopState, overlapPx, peekPx)
    }
    Layout(
        content = {
            // Читаем alpha в graphicsLayer-лямбде (deferred) — fade без рекомпозиции.
            Box(modifier = Modifier.graphicsLayer { this.alpha = alpha }) { card() }
            Box { surface() }
        },
        measurePolicy = measurePolicy,
    )
}

private class AdditionalTopMeasurePolicy(
    private val additionalTopState: AdditionalTopState,
    private val overlapPx: Int,
    private val peekPx: Int,
) : MeasurePolicy {

    private fun visibleCardPx(cardNaturalPx: Int): Int = when (additionalTopState) {
        AdditionalTopState.Expanded -> (cardNaturalPx - overlapPx).coerceAtLeast(0)
        AdditionalTopState.Collapsed -> peekPx
    }

    override fun MeasureScope.measure(
        measurables: List<Measurable>,
        constraints: Constraints,
    ): MeasureResult {
        val cardMeasurable = measurables[0]
        val surfaceMeasurable = measurables[1]
        val width = constraints.maxWidth
        val totalHeight = constraints.maxHeight
        val cardNatural = cardMeasurable.maxIntrinsicHeight(width)
        val visibleCard = visibleCardPx(cardNatural)
        val surfaceHeight = (totalHeight - visibleCard).coerceAtLeast(0)
        val cardPlaceable = cardMeasurable.measure(Constraints.fixed(width = width, height = cardNatural))
        val surfacePlaceable = surfaceMeasurable.measure(Constraints.fixed(width = width, height = surfaceHeight))
        return layout(width, totalHeight) {
            cardPlaceable.place(x = 0, y = 0)
            surfacePlaceable.place(x = 0, y = visibleCard)
        }
    }

    // Натуральная высота стека = видимая часть карточки + натуральная высота основной части.
    // Нужна корректная реализация: дефолтная intrinsic-логика прогнала бы measure с Infinity-высотой.
    override fun IntrinsicMeasureScope.maxIntrinsicHeight(
        measurables: List<IntrinsicMeasurable>,
        width: Int,
    ): Int {
        val cardNatural = measurables[0].maxIntrinsicHeight(width)
        val surfaceNatural = measurables[1].maxIntrinsicHeight(width)
        return visibleCardPx(cardNatural) + surfaceNatural
    }
}
