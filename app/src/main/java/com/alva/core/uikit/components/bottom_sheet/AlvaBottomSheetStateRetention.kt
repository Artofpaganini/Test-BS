package com.alva.core.uikit.components.bottom_sheet

import androidx.compose.animation.core.AnimationSpec
import androidx.compose.runtime.Composable
import androidx.compose.runtime.saveable.rememberSaveable

// Фабрика holder'а листа для overlay-сцены bottom sheet с поддержкой forward-back retention.
// retainedValue — сохранённый анкер из in-memory store НАД NavDisplay (см. core:navigation
// AlvaBottomSheetSceneRetentionStore). НЕ null только на возврате forward-back: тогда holder
// стартует restored (snap на анкер, без appear-анимации). null — свежее открытие (проигрывается appear).
// Process death обслуживает restore-ветка Saver (значение из persisted SaveableStateRegistry ядра),
// поэтому retainedValue отвечает исключительно за forward-back внутри живой композиции.
@Composable
fun rememberRetainedAlvaBottomSheetStateHolder(
    initial: AlvaBottomSheetValue,
    anchors: List<AlvaBottomSheetValue>,
    animationSpec: AnimationSpec<Float>,
    retainedValue: AlvaBottomSheetValue?,
): AlvaBottomSheetStateHolder = rememberSaveable(
    saver = AlvaBottomSheetStateHolder.Saver(
        anchors = anchors,
        animationSpec = animationSpec,
    ),
) {
    AlvaBottomSheetStateHolder(
        initial = retainedValue ?: initial,
        restoredValue = retainedValue,
        anchors = anchors,
        animationSpec = animationSpec,
    )
}
