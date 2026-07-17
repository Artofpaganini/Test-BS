package com.alva.core.uikit.components.bottom_sheet

import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.gestures.AnchoredDraggableState
import androidx.compose.foundation.gestures.DraggableAnchors
import androidx.compose.foundation.gestures.animateTo
import androidx.compose.foundation.gestures.snapTo
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import kotlin.math.abs

private val DEFAULT_ANCHORS: List<AlvaBottomSheetValue> = listOf(
    AlvaBottomSheetValue.Hidden,
    AlvaBottomSheetValue.PartialExpanded(DEFAULT_PARTIAL_EXPANDED_FRACTION),
    AlvaBottomSheetValue.Expanded,
)
private val DEFAULT_ANIMATION_SPEC: AnimationSpec<Float> = spring(stiffness = Spring.StiffnessMediumLow)
private const val DEFAULT_PARTIAL_EXPANDED_FRACTION = 0.5f

private const val SAVER_ORDINAL_HIDDEN = 0
private const val SAVER_ORDINAL_EXPANDED = 1
private const val SAVER_ORDINAL_PARTIAL_EXPANDED = 2

@Stable
class AlvaBottomSheetStateHolder internal constructor(
    initial: AlvaBottomSheetValue,
    restoredValue: AlvaBottomSheetValue?,
    val anchors: List<AlvaBottomSheetValue>,
    val animationSpec: AnimationSpec<Float>,
) {
    // Признак restore: holder создан из restore-ветки Saver (смена конфигурации /
    // возврат процесса), а не первый показ. Различаем именно по restore-пути
    // (restoredValue != null), а НЕ по значению initial: у СВЕЖЕГО открытия initial
    // тоже не-Hidden (sceneConfig.initialValue = PartialExpanded/Expanded), и анимацию
    // появления глушить нельзя. На restore лист стартует на сохранённом якоре без анимации.
    private val isRestored: Boolean = restoredValue != null

    // Лист восстановлен на ВИДИМОМ (не-Hidden) resting-анкере: он уже показывался
    // до пересоздания, поэтому hasSettledVisible стартует как true (восстановленное закрытие
    // снизу должно корректно довести dismiss).
    private val isRestoredVisible: Boolean =
        restoredValue != null && restoredValue !is AlvaBottomSheetValue.Hidden

    // Якорь, на который лист выезжает при ПЕРВОМ показе. AnchoredDraggable стартует
    // со скрытого якоря — это даёт анимацию показа через смещение offset (единый
    // источник истины вместо отдельного AnimatedVisibility).
    private val showTarget: AlvaBottomSheetValue =
        initial.takeUnless { value -> value is AlvaBottomSheetValue.Hidden }
            ?: anchors.firstOrNull { value -> value !is AlvaBottomSheetValue.Hidden }
            ?: AlvaBottomSheetValue.Expanded

    private var confirmValueChangeState: (AlvaBottomSheetValue, DismissReason?) -> Boolean
            by mutableStateOf(DEFAULT_CONFIRM_VALUE_CHANGE)

    var confirmValueChange: (target: AlvaBottomSheetValue, reason: DismissReason?) -> Boolean
        get() = confirmValueChangeState
        set(value) {
            confirmValueChangeState = value
            rebuildDragAnchors()
        }

    internal val draggableState: AnchoredDraggableState<AlvaBottomSheetValue> =
        AnchoredDraggableState(
            // Свежее открытие стартует на Hidden — showOnAppear() анимирует Hidden -> showTarget.
            // Restore стартует на сохранённом якоре: updateAnchors сделает snap без переигрывания.
            initialValue = restoredValue ?: AlvaBottomSheetValue.Hidden,
        )

    internal var pendingDismiss: DismissReason? by mutableStateOf(null)
        private set

    // Лист уже осел на ВИДИМОМ якоре хотя бы раз (appear-анимация завершилась). На restore
    // считаем, что появление уже было. Пока false — идёт первое появление: перетаскивание
    // выключено, чтобы pointer-down по телу листа не перехватил drag (startDragImmediately при
    // работающей анимации) и не осадил лист в Hidden от нечаянного тапа сразу после открытия.
    private var hasSettledVisible by mutableStateOf(isRestoredVisible)

    private var maxHeightPx by mutableFloatStateOf(0f)

    val currentValue: AlvaBottomSheetValue
        get() = draggableState.currentValue

    val targetValue: AlvaBottomSheetValue
        get() = draggableState.targetValue

    internal val settledValue: AlvaBottomSheetValue
        get() = draggableState.settledValue

    // true, пока идёт первая appear-анимация (лист ещё не осел на видимом якоре).
    internal val isAppearing: Boolean
        get() = !hasSettledVisible

    // Высота листа измерена — якоря готовы, можно запускать анимацию появления.
    internal val hasMeasuredHeight: Boolean
        get() = maxHeightPx > 0f

    // Завершённое закрытие: лист осел на Hidden ЛИБО после того как хотя бы раз показался
    // (hasSettledVisible), ЛИБО по явному запросу закрытия (pendingDismiss — scrim/back/hide/code).
    // Так спонтанный settle-в-Hidden ВО ВРЕМЯ первого появления (без намерения) НЕ считается
    // закрытием — иначе лист изредка «не открывался» (появление срывалось в Hidden, диалог схлопывался).
    internal val isSettledAtHidden: Boolean by derivedStateOf {
        (hasSettledVisible || pendingDismiss != null) &&
            !draggableState.isAnimationRunning &&
            draggableState.settledValue == AlvaBottomSheetValue.Hidden
    }

    internal val expandProgress: Float by derivedStateOf {
        val height = maxHeightPx
        if (height <= 0f) {
            return@derivedStateOf if (currentValue == AlvaBottomSheetValue.Expanded) 1f else 0f
        }
        val offset = draggableState.offset
        if (offset.isNaN()) return@derivedStateOf 0f
        ((height - offset) / height).coerceIn(0f, 1f)
    }

    internal fun requestDismiss(reason: DismissReason) {
        if (pendingDismiss != null) return
        if (!confirmValueChange(AlvaBottomSheetValue.Hidden, reason)) return
        pendingDismiss = reason
    }

    internal fun markSettledVisible() {
        hasSettledVisible = true
    }

    internal fun updateMaxHeight(heightPx: Float) {
        if (maxHeightPx != heightPx) {
            maxHeightPx = heightPx
            rebuildDragAnchors()
        }
    }

    internal suspend fun showOnAppear() {
        if (isRestored) return
        animateToInternal(showTarget, reason = null)
    }

    internal suspend fun animateToHidden() {
        ensureAnchorAvailable(AlvaBottomSheetValue.Hidden)
        draggableState.animateTo(AlvaBottomSheetValue.Hidden, animationSpec)
    }

    private fun rebuildDragAnchors() {
        val height = maxHeightPx
        if (height <= 0f) return
        val dragAnchors = anchors.filter { value ->
            val reason = if (value is AlvaBottomSheetValue.Hidden) DismissReason.UserSwipedDown else null
            confirmValueChange(value, reason)
        }
        val newAnchors = buildAnchors(dragAnchors, height)
        // При смене высоты листа (onLayoutRectChanged может сработать дважды: первый раз с NaN-offset,
        // второй — с уже заданным offset от первого прохода) AnchoredDraggable по умолчанию ищет
        // ближайший якорь по OFFSET-расстоянию (closestAnchor). Это приводит к перескоку: например,
        // offset=720 при H=800 → при H=1000 closest → PE(0.25) [dist=30], хотя текущий anchor PE(0.1).
        // Фикс: явно передаём текущее ЗНАЧЕНИЕ якоря как newTarget → snap по VALUE, не по distance.
        val current = draggableState.currentValue
        val targetForRebuild = if (dragAnchors.contains(current)) current else draggableState.targetValue
        draggableState.updateAnchors(newAnchors, newTarget = targetForRebuild)
    }

    private fun buildAnchors(
        anchorValues: List<AlvaBottomSheetValue>,
        heightPx: Float,
    ): DraggableAnchors<AlvaBottomSheetValue> = DraggableAnchors {
        anchorValues.forEach { value ->
            val offset = when (value) {
                is AlvaBottomSheetValue.Hidden -> heightPx
                is AlvaBottomSheetValue.Expanded -> 0f
                is AlvaBottomSheetValue.PartialExpanded -> heightPx * (1f - value.fraction)
            }
            value at offset
        }
    }

    private fun ensureAnchorAvailable(value: AlvaBottomSheetValue) {
        val height = maxHeightPx
        if (height <= 0f) return
        if (draggableState.anchors.hasPositionFor(value)) return
        val anchorsForOp = if (anchors.contains(value)) anchors else anchors + value
        draggableState.updateAnchors(buildAnchors(anchorsForOp, height))
    }

    private suspend fun animateToInternal(value: AlvaBottomSheetValue, reason: DismissReason?) {
        if (!confirmValueChange(value, reason)) return
        ensureAnchorAvailable(value)
        try {
            draggableState.animateTo(value, animationSpec)
        } finally {
            if (value !is AlvaBottomSheetValue.Hidden) {
                rebuildDragAnchors()
            }
        }
    }

    private suspend fun snapToInternal(value: AlvaBottomSheetValue, reason: DismissReason?) {
        if (!confirmValueChange(value, reason)) return
        ensureAnchorAvailable(value)
        try {
            draggableState.snapTo(value)
        } finally {
            if (value !is AlvaBottomSheetValue.Hidden) {
                rebuildDragAnchors()
            }
        }
    }

    suspend fun show() {
        animateToInternal(showTarget, reason = null)
    }

    suspend fun hide() {
        requestDismiss(DismissReason.RequestedByCode)
    }

    suspend fun expand() {
        val target = anchors.find { anchor -> anchor is AlvaBottomSheetValue.Expanded } ?: return
        animateToInternal(target, reason = null)
    }

    suspend fun partialExpand(fraction: Float? = null) {
        val target = if (fraction != null) {
            anchors
                .filterIsInstance<AlvaBottomSheetValue.PartialExpanded>()
                .find { anchor -> anchor.fraction == fraction }
                ?: anchors
                    .filterIsInstance<AlvaBottomSheetValue.PartialExpanded>()
                    .minByOrNull { partial -> abs(partial.fraction - fraction) }
                ?: return
        } else {
            anchors.find { anchor -> anchor is AlvaBottomSheetValue.PartialExpanded } ?: return
        }
        animateToInternal(target, reason = null)
    }

    suspend fun animateTo(value: AlvaBottomSheetValue) {
        if (value is AlvaBottomSheetValue.Hidden) {
            requestDismiss(DismissReason.RequestedByCode)
        } else {
            animateToInternal(value, reason = null)
        }
    }

    suspend fun snapTo(value: AlvaBottomSheetValue) {
        if (value is AlvaBottomSheetValue.Hidden) {
            requestDismiss(DismissReason.RequestedByCode)
        } else {
            snapToInternal(value, reason = null)
        }
    }

    companion object {
        fun Saver(
            anchors: List<AlvaBottomSheetValue>,
            animationSpec: AnimationSpec<Float>,
        ): Saver<AlvaBottomSheetStateHolder, Any> = listSaver(
            save = { holder ->
                when (val current = holder.currentValue) {
                    is AlvaBottomSheetValue.Hidden -> listOf(SAVER_ORDINAL_HIDDEN, 0f)
                    is AlvaBottomSheetValue.Expanded -> listOf(SAVER_ORDINAL_EXPANDED, 0f)
                    is AlvaBottomSheetValue.PartialExpanded -> listOf(SAVER_ORDINAL_PARTIAL_EXPANDED, current.fraction)
                }
            },
            restore = { list ->
                val ordinal = list[0] as Int
                val fraction = list[1] as Float
                val savedValue = when (ordinal) {
                    SAVER_ORDINAL_EXPANDED -> AlvaBottomSheetValue.Expanded
                    SAVER_ORDINAL_PARTIAL_EXPANDED -> AlvaBottomSheetValue.PartialExpanded(fraction)
                    else -> AlvaBottomSheetValue.Hidden
                }
                // restoredValue != null -> это restore: snap на сохранённый якорь, без appear-анимации.
                AlvaBottomSheetStateHolder(
                    initial = savedValue,
                    restoredValue = savedValue,
                    anchors = anchors,
                    animationSpec = animationSpec,
                )
            },
        )
    }
}

private val DEFAULT_CONFIRM_VALUE_CHANGE: (AlvaBottomSheetValue, DismissReason?) -> Boolean = { _, _ -> true }

@Composable
fun rememberAlvaBottomSheetStateHolder(
    initial: AlvaBottomSheetValue = AlvaBottomSheetValue.Hidden,
    anchors: List<AlvaBottomSheetValue> = DEFAULT_ANCHORS,
    animationSpec: AnimationSpec<Float> = DEFAULT_ANIMATION_SPEC,
): AlvaBottomSheetStateHolder = rememberSaveable(
    saver = AlvaBottomSheetStateHolder.Saver(
        anchors = anchors,
        animationSpec = animationSpec,
    ),
) {
    // restoredValue = null -> свежее открытие: draggableState стартует на Hidden,
    // showOnAppear() проигрывает плавную анимацию появления.
    AlvaBottomSheetStateHolder(
        initial = initial,
        restoredValue = null,
        anchors = anchors,
        animationSpec = animationSpec,
    )
}
