package com.onexui.bottomsheet

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationVector1D
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
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import kotlin.math.roundToInt

// Состояния Additional Top (§1). Переключаются ТОЛЬКО внешними факторами (кнопка/логика экрана), не жестами.
// Кейс: «Добавить в купон» / «Отслеживать».
internal enum class AdditionalTopState { Expanded, Collapsed }

// Стек «Additional Top + основная часть» со стики-перекрытием. visibleFraction — Animatable (ПЛАВНАЯ анимация,
// драйвится из XBottomSheet): 1 = Expanded (карточка раскрыта, нижние overlap-dp утоплены под основную часть),
// 0 = скрыта (полностью уехала вверх). Видимая высота карточки = fraction * (natural − overlap). cornerRadius —
// верхние скругления карточки. Основная часть (surface) рисуется ПОВЕРХ карточки (кладётся позже) → перекрытие
// корректно по z. MeasurePolicy — ОДИН инстанс (remember без ключей): fraction.value и overlapPx (из density
// MeasureScope) читаются ВНУТРИ measure → инвалидация только layout-фазы, композиция за кадры не трогается.
@Composable
internal fun AdditionalTopStack(
    visibleFraction: Animatable<Float, AnimationVector1D>,
    cornerRadius: Dp,
    additionalTopContent: @Composable () -> Unit,
    sheetContent: @Composable () -> Unit,
) {
    val measurePolicy = remember { AdditionalTopMeasurePolicy(visibleFraction) }
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
                Box(modifier = Modifier.wrapContentHeight(align = Alignment.Top, unbounded = true)) { additionalTopContent() }
            }
            Box { sheetContent() }
        },
        measurePolicy = measurePolicy,
    )
}

// Один инстанс на жизнь листа. visibleFraction — Animatable (стабильная ссылка); .value читается в measure/
// intrinsic (snapshot-read в layout-фазе). overlapPx берётся из density MeasureScope (не хранится).
private class AdditionalTopMeasurePolicy(
    private val visibleFraction: Animatable<Float, AnimationVector1D>,
) : MeasurePolicy {

    // Видимая часть карточки НАД листом (анимируется fraction). Низ карточки (overlap) прячется под Surface,
    // поэтому в эту высоту overlap не входит.
    private fun cardVisibleHeightPx(cardNaturalPx: Int, overlapPx: Int): Int =
        (visibleFraction.value.coerceIn(0f, 1f) * (cardNaturalPx - overlapPx).coerceAtLeast(0)).roundToInt()

    override fun MeasureScope.measure(
        measurables: List<Measurable>,
        constraints: Constraints,
    ): MeasureResult {
        val overlapPx = XBottomSheetDefaults.AdditionalTopOverlap.roundToPx()
        val cardMeasurable = measurables[0]
        val surfaceMeasurable = measurables[1]
        val width = constraints.maxWidth
        val cardNatural = cardMeasurable.maxIntrinsicHeight(width)
        val cardVisibleHeight = cardVisibleHeightPx(cardNatural, overlapPx)
        val cardHeight = cardVisibleHeight + overlapPx
        val cardPlaceable = cardMeasurable.measure(Constraints.fixed(width = width, height = cardHeight))
        // Surface стоит на уровне видимой части; карточка тянется на overlap НИЖЕ верха Surface (низ утоплен).
        val surfaceMaxHeight = (constraints.maxHeight - cardVisibleHeight).coerceAtLeast(0)
        // detect-пасс (высота СВОБОДНАЯ, minHeight<maxHeight) — меряем surface loose: короткий контент wrap'ится
        // (natural), скролл/ленивый заполняет; итоговая высота стека = видимая часть карточки + натуральная высота
        // surface → корректный wrap/fill-детект. Иначе (fixed) стек всегда возвращал бы maxHeight → isFillMode=true
        // при любом additionalTop, и короткий контент открывался бы на peek вместо Content-по-контенту. (одобрено)
        val isDetectPass = constraints.minHeight < constraints.maxHeight
        val surfacePlaceable = if (isDetectPass) {
            surfaceMeasurable.measure(
                Constraints(minWidth = width, maxWidth = width, minHeight = 0, maxHeight = surfaceMaxHeight),
            )
        } else {
            surfaceMeasurable.measure(Constraints.fixed(width = width, height = surfaceMaxHeight))
        }
        val totalHeight = if (isDetectPass) cardVisibleHeight + surfacePlaceable.height else constraints.maxHeight
        return layout(width, totalHeight) {
            cardPlaceable.place(x = 0, y = 0)
            surfacePlaceable.place(x = 0, y = cardVisibleHeight)
        }
    }

    // Натуральная высота стека = видимая часть карточки над листом + натуральная высота листа (overlap поглощён).
    override fun IntrinsicMeasureScope.maxIntrinsicHeight(
        measurables: List<IntrinsicMeasurable>,
        width: Int,
    ): Int {
        val overlapPx = XBottomSheetDefaults.AdditionalTopOverlap.roundToPx()
        val cardNatural = measurables[0].maxIntrinsicHeight(width)
        val surfaceNatural = measurables[1].maxIntrinsicHeight(width)
        return cardVisibleHeightPx(cardNatural, overlapPx) + surfaceNatural
    }
}
