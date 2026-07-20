package com.onexui.bottomsheet

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.layout.IntrinsicMeasurable
import androidx.compose.ui.layout.IntrinsicMeasureScope
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.Measurable
import androidx.compose.ui.layout.MeasurePolicy
import androidx.compose.ui.layout.MeasureResult
import androidx.compose.ui.layout.MeasureScope
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import kotlin.math.roundToInt

// Состояния Additional Top (§1). Переключаются ТОЛЬКО внешними факторами (кнопка/логика экрана), не жестами.
// Кейс: «Добавить в купон» / «Отслеживать».
internal enum class AdditionalTopState { Expanded, Collapsed }

// Стек «Additional Top + основная часть» со стики-перекрытием. visibleFraction (ПЛАВНАЯ анимация из SheetBody):
// 1 = Expanded (карточка раскрыта, нижние overlap-dp утоплены под основную часть), 0 = скрыта (полностью уехала
// вверх). Видимая высота карточки = fraction * (natural − overlap). cornerRadius — верхние скругления карточки.
// Основная часть (surface) рисуется ПОВЕРХ карточки (кладётся позже) → перекрытие корректно по z.
@Composable
internal fun AdditionalTopStack(
    visibleFraction: Float,
    cornerRadius: Dp,
    card: @Composable () -> Unit,
    surface: @Composable () -> Unit,
) {
    val density = LocalDensity.current
    val overlapPx = with(density) { XBottomSheetDefaults.AdditionalTopOverlap.roundToPx() }
    val measurePolicy = remember(visibleFraction, overlapPx) {
        AdditionalTopMeasurePolicy(visibleFraction, overlapPx)
    }
    Layout(
        content = {
            // Клип карточки: скругляем ТОЛЬКО верхние углы (cornerRadius). Низ квадратный и уходит под Surface
            // на overlap-dp (перекрытие в раскладке), поэтому квадратные нижние углы спрятаны за листом, а
            // скруглённые верхние углы Surface «наезжают» на тело карточки → визуально это единое продолжение
            // контента, без зазора-scrim. Карточка меряется натурально (wrapContentHeight unbounded, прижата к
            // верху) и обрезается по clipToBounds до заданной высоты.
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(topStart = cornerRadius, topEnd = cornerRadius))
                    .clipToBounds(),
            ) {
                Box(modifier = Modifier.wrapContentHeight(align = Alignment.Top, unbounded = true)) { card() }
            }
            Box { surface() }
        },
        measurePolicy = measurePolicy,
    )
}

private class AdditionalTopMeasurePolicy(
    private val visibleFraction: Float,
    private val overlapPx: Int,
) : MeasurePolicy {

    // Протрузия — видимая часть карточки НАД листом (анимируется fraction). Низ карточки (overlap) прячется
    // под Surface, поэтому в высоту протрузии overlap не входит.
    private fun protrusionPx(cardNaturalPx: Int): Int =
        (visibleFraction.coerceIn(0f, 1f) * (cardNaturalPx - overlapPx).coerceAtLeast(0)).roundToInt()

    override fun MeasureScope.measure(
        measurables: List<Measurable>,
        constraints: Constraints,
    ): MeasureResult {
        val cardMeasurable = measurables[0]
        val surfaceMeasurable = measurables[1]
        val width = constraints.maxWidth
        val totalHeight = constraints.maxHeight
        val cardNatural = cardMeasurable.maxIntrinsicHeight(width)
        val protrusion = protrusionPx(cardNatural)
        // Surface стоит на уровне протрузии; карточка тянется на overlap НИЖЕ верха Surface (низ утоплен под лист).
        val surfaceHeight = (totalHeight - protrusion).coerceAtLeast(0)
        val cardHeight = protrusion + overlapPx
        val cardPlaceable = cardMeasurable.measure(Constraints.fixed(width = width, height = cardHeight))
        val surfacePlaceable = surfaceMeasurable.measure(Constraints.fixed(width = width, height = surfaceHeight))
        return layout(width, totalHeight) {
            cardPlaceable.place(x = 0, y = 0)
            surfacePlaceable.place(x = 0, y = protrusion)
        }
    }

    // Натуральная высота стека = протрузия карточки над листом + натуральная высота листа (overlap поглощён).
    override fun IntrinsicMeasureScope.maxIntrinsicHeight(
        measurables: List<IntrinsicMeasurable>,
        width: Int,
    ): Int {
        val cardNatural = measurables[0].maxIntrinsicHeight(width)
        val surfaceNatural = measurables[1].maxIntrinsicHeight(width)
        return protrusionPx(cardNatural) + surfaceNatural
    }
}
