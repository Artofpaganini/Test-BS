package com.onexui.bottomsheet

import androidx.compose.animation.core.Animatable
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.math.abs

// Стейт-машина высоты листа. Compose-state (mutableStateOf), НЕ StateFlow — правило _state.update{} здесь не
// применяется. Режим (wrap по контенту / fill фикс-якоря) определяется замером контента (см. SheetMetrics.fills),
// стейт ВЫЧИСЛЯЕТСЯ по метрикам и фактам (skipCollapsed, isLoading, кастомные якоря).
@Stable
internal class XBottomSheetState internal constructor(
    val skipCollapsed: Boolean,
    initialLoading: Boolean,
    val peekFraction: Float,
    val customAnchors: List<XSheetAnchor>,
) {
    var currentValue: SheetValue by mutableStateOf(SheetValue.Hidden)
        private set

    var isLoading by mutableStateOf(initialLoading)
        internal set

    var additionalTopState by mutableStateOf(AdditionalTopState.Expanded)

    internal var metrics by mutableStateOf<SheetMetrics?>(null)
        private set

    // Текущая высота листа в px. Единственный писатель — suspend-методы стейта (drag = snapTo, анимация =
    // animateTo). Читается в measure-лямбде измерителя (deferred), без рекомпозиций.
    internal val offset = Animatable(0f)

    internal var isDragging by mutableStateOf(false)

    private var rawOvershootPx = 0f

    // Стейт до авто-промоушена в FullScreen из-за клавиатуры (§6): при скрытии IME откатываемся к нему.
    private var imePromotedFrom: SheetValue? = null

    val isVisible: Boolean get() = currentValue != SheetValue.Hidden

    private suspend fun awaitMetrics(): SheetMetrics =
        metrics ?: snapshotFlow { metrics }.filterNotNull().first()

    suspend fun show() {
        // Уже открыт (в т.ч. восстановлен после ротации/process-death через rememberSaveable) — не переоткрываем,
        // иначе анимация к openTarget сбросила бы восстановленный стейт. Высоту к якорю снапнет snapToCurrentAnchor.
        if (isVisible) return
        val measured = awaitMetrics()
        animateTo(if (isLoading) SheetValue.Loading else measured.openTarget(skipCollapsed))
    }

    // Восстановление стейта после ротации/process-death (из Saver). offset снапнется к якорю, когда придут метрики
    // (snapToCurrentAnchor в LaunchedEffect по размерам экрана).
    internal fun restore(value: SheetValue, loading: Boolean, additionalTop: AdditionalTopState) {
        currentValue = value
        isLoading = loading
        additionalTopState = additionalTop
    }

    suspend fun expand() {
        val measured = metrics ?: return
        imePromotedFrom = null
        if (currentValue == SheetValue.Collapsed) animateTo(measured.expandTarget())
    }

    suspend fun hide() {
        animateTo(SheetValue.Hidden)
    }

    suspend fun contentReady() {
        val loaderMetrics = metrics
        isLoading = false
        val measured = withTimeoutOrNull(REMEASURE_TIMEOUT_MS) {
            snapshotFlow { metrics }.filterNotNull().first { remeasured -> remeasured !== loaderMetrics }
        } ?: awaitMetrics()
        animateTo(measured.openTarget(skipCollapsed))
    }

    // Контент изменился (подгрузка/рост): высота следует за текущим якорем. В wrap+skipCollapsed+Content, если
    // контент выше экрана — авто FullScreen. В Loading — no-op (высоту ведут show/contentReady).
    internal suspend fun onContentRemeasured() {
        val measured = metrics ?: return
        if (!isVisible || currentValue == SheetValue.Loading) return
        if (skipCollapsed && currentValue == SheetValue.Content && measured.contentHeightPx > measured.maxHeightPx) {
            animateTo(SheetValue.ExpandedFullScreen)
        } else {
            offset.animateTo(measured.anchorPx(currentValue, skipCollapsed).toFloat(), NativeSheetSpring)
        }
    }

    internal suspend fun onImeShown(imeHeightPx: Int, alwaysFullScreen: Boolean) {
        val measured = metrics ?: return
        if (!isVisible) return
        when (currentValue) {
            SheetValue.Content, SheetValue.Collapsed, SheetValue.ExpandedContent, is SheetValue.Custom -> {
                val liftedTop = measured.anchorPx(currentValue, skipCollapsed) + imeHeightPx
                if (alwaysFullScreen || liftedTop > measured.maxHeightPx) {
                    imePromotedFrom = currentValue
                    animateTo(SheetValue.ExpandedFullScreen)
                }
            }
            else -> Unit
        }
    }

    internal suspend fun onImeHidden() {
        val target = imePromotedFrom ?: return
        imePromotedFrom = null
        if (currentValue == SheetValue.ExpandedFullScreen) animateTo(target)
    }

    internal suspend fun snapToCurrentAnchor() {
        val measured = metrics ?: return
        if (!isVisible || offset.isRunning) return
        offset.snapTo(measured.anchorPx(currentValue, skipCollapsed).toFloat())
    }

    internal fun updateMetrics(
        screenHeightPx: Int,
        statusBarPx: Int,
        contentHeightPx: Int,
        loadingSheetHeightPx: Int,
    ) {
        val updated = SheetMetrics(
            screenHeightPx = screenHeightPx,
            statusBarPx = statusBarPx,
            contentHeightPx = contentHeightPx,
            loadingSheetHeightPx = loadingSheetHeightPx,
            peekFraction = peekFraction,
            customAnchors = customAnchors,
        )
        if (updated != metrics) metrics = updated
    }

    // Команды жестов сериализуются через ОДИН FIFO-канал (см. processGestures) — иначе независимые
    // scope.launch { dragBy } и scope.launch { settle } гоняются: settle стартует анимацию возврата, а
    // прилетевший позже dragBy.snapTo её отменяет. Команды — чистые данные без лямбд; конфиг закрытия — в свойствах.
    private sealed interface GestureCommand {
        data class Drag(val deltaHeightPx: Float) : GestureCommand
        data class Settle(val velocity: Float) : GestureCommand
    }

    private val gestureCommands = Channel<GestureCommand>(Channel.UNLIMITED)

    internal var dismissOnSwipeDown: Boolean = true
    internal var onDismissRequest: () -> Unit = {}

    internal fun enqueueDrag(deltaHeightPx: Float) {
        isDragging = true
        gestureCommands.trySend(GestureCommand.Drag(deltaHeightPx))
    }

    internal fun enqueueSettle(velocity: Float) {
        gestureCommands.trySend(GestureCommand.Settle(velocity))
    }

    internal suspend fun processGestures(): Unit = coroutineScope {
        var settleJob: Job? = null
        for (command in gestureCommands) {
            when (command) {
                is GestureCommand.Drag -> {
                    settleJob?.cancelAndJoin()
                    settleJob = null
                    dragBy(command.deltaHeightPx)
                }
                is GestureCommand.Settle -> {
                    settleJob?.cancelAndJoin()
                    settleJob = launch { settle(command.velocity) }
                }
            }
        }
    }

    // Живой драг: двигаем высоту, с сопротивлением на overshoot над верхним якорем (iOS-растягивание).
    // wrap: overshoot над Content/ExpandedContent тянет к потолку. fill: overshoot над ExpandedFullScreen
    // (верхний якорь) уводит выше потолка с сопротивлением (rubber-band в зону статус-бара), spring назад.
    private suspend fun dragBy(deltaHeightPx: Float) {
        val measured = metrics ?: return
        val anchor = measured.anchorPx(currentValue, skipCollapsed).toFloat()
        val floorPx = if (dismissOnSwipeDown) 0f else lowestRestAnchorPx(measured)
        val atTop = currentValue == SheetValue.ExpandedFullScreen
        val resistanceState = currentValue == SheetValue.Content ||
            currentValue == SheetValue.ExpandedContent || atTop
        val overshootCeiling = if (atTop) {
            (measured.maxHeightPx + RESISTANCE_MAX_PX)
        } else {
            measured.maxHeightPx.toFloat()
        }
        val inOvershoot = resistanceState &&
            (rawOvershootPx > 0f || (offset.value >= anchor - 0.5f && deltaHeightPx > 0f))
        if (inOvershoot) {
            rawOvershootPx = (rawOvershootPx + deltaHeightPx).coerceAtLeast(0f)
            val resisted = resistedOvershoot(rawOvershootPx, RESISTANCE_MAX_PX)
            offset.snapTo((anchor + resisted).coerceAtMost(overshootCeiling))
        } else {
            rawOvershootPx = 0f
            offset.snapTo((offset.value + deltaHeightPx).coerceIn(floorPx, measured.maxHeightPx.toFloat()))
        }
    }

    private fun lowestRestAnchorPx(measured: SheetMetrics): Float =
        settleCandidates(measured, dismissOnSwipeDown = false).firstOrNull()?.second?.toFloat() ?: 0f

    // Settle: по скорости и ближайшему якорю. Hidden — свайп-закрытие (если dismissOnSwipeDown).
    private suspend fun settle(velocity: Float) {
        isDragging = false
        rawOvershootPx = 0f
        imePromotedFrom = null
        val measured = metrics ?: return
        val candidates = settleCandidates(measured, dismissOnSwipeDown)
        if (candidates.isEmpty()) {
            animateTo(currentValue)
            return
        }
        val current = offset.value
        val chosen = when {
            velocity < -FLING_VELOCITY_THRESHOLD ->
                candidates.firstOrNull { candidate -> candidate.second > current + ANCHOR_EPS } ?: candidates.last()
            velocity > FLING_VELOCITY_THRESHOLD ->
                candidates.lastOrNull { candidate -> candidate.second < current - ANCHOR_EPS } ?: candidates.first()
            else ->
                candidates.minByOrNull { candidate -> abs(candidate.second - current) } ?: candidates.first()
        }
        if (chosen.first == SheetValue.Hidden) onDismissRequest() else animateTo(chosen.first)
    }

    // Отсортированные по высоте якоря доступных стейтов. fill-режим — Collapsed(peek)/ExpandedFullScreen(full) +
    // кастомные; wrap-режим — Content/Collapsed/ExpandedContent по контенту. Hidden — если разрешён свайп-close.
    private fun settleCandidates(
        measured: SheetMetrics,
        dismissOnSwipeDown: Boolean,
    ): List<Pair<SheetValue, Int>> {
        val list = mutableListOf<Pair<SheetValue, Int>>()
        when {
            measured.fills -> {
                if (!skipCollapsed) list.add(SheetValue.Collapsed to measured.peekPx)
                list.add(SheetValue.ExpandedFullScreen to measured.maxHeightPx)
                measured.customAnchors.forEach { anchor ->
                    list.add(SheetValue.Custom(anchor.key) to measured.customAnchorPx(anchor.key))
                }
            }
            skipCollapsed ->
                list.add(SheetValue.Content to measured.anchorPx(SheetValue.Content, skipCollapsed = true))
            measured.contentHeightPx <= measured.peekPx ->
                list.add(SheetValue.Content to measured.anchorPx(SheetValue.Content, skipCollapsed = false))
            else -> {
                list.add(SheetValue.Collapsed to measured.peekPx)
                val expanded = measured.expandTarget()
                list.add(expanded to measured.anchorPx(expanded, skipCollapsed = false))
            }
        }
        if (dismissOnSwipeDown) list.add(SheetValue.Hidden to 0)
        return list.distinctBy { candidate -> candidate.second }.sortedBy { candidate -> candidate.second }
    }

    private suspend fun animateTo(target: SheetValue) {
        currentValue = target
        val anchor = metrics?.anchorPx(target, skipCollapsed)?.toFloat() ?: 0f
        offset.animateTo(anchor, NativeSheetSpring)
    }

    private companion object {
        const val FLING_VELOCITY_THRESHOLD = 400f
        const val RESISTANCE_MAX_PX = 240f
        const val ANCHOR_EPS = 1f
        const val REMEASURE_TIMEOUT_MS = 500L
    }
}

// rememberSaveable: стейт листа (currentValue/разворот, isLoading, additionalTopState) переживает ротацию и
// process-death. Позиция скролла контента переживает через свой rememberLazyListState/rememberScrollState.
@Composable
internal fun rememberXBottomSheetState(
    skipCollapsed: Boolean = false,
    initialLoading: Boolean = false,
    peekFraction: Float = DEFAULT_PEEK_FRACTION,
    customAnchors: List<XSheetAnchor> = emptyList(),
): XBottomSheetState = rememberSaveable(
    skipCollapsed, initialLoading, peekFraction, customAnchors,
    saver = xBottomSheetStateSaver(skipCollapsed, initialLoading, peekFraction, customAnchors),
) {
    XBottomSheetState(
        skipCollapsed = skipCollapsed,
        initialLoading = initialLoading,
        peekFraction = peekFraction,
        customAnchors = customAnchors,
    )
}

// Saver: сохраняет тег стейта + isLoading + имя additionalTopState (всё Bundle-совместимо). Конфиг (skipCollapsed,
// peekFraction, customAnchors) передаётся в конструктор из фабрики (не сохраняется — задан разработчиком).
private fun xBottomSheetStateSaver(
    skipCollapsed: Boolean,
    initialLoading: Boolean,
    peekFraction: Float,
    customAnchors: List<XSheetAnchor>,
): Saver<XBottomSheetState, List<Any>> = Saver(
    save = { state ->
        listOf(sheetValueTag(state.currentValue), state.isLoading, state.additionalTopState.name)
    },
    restore = { saved ->
        val tag = saved.getOrNull(0) as? String ?: "h"
        val loading = saved.getOrNull(1) as? Boolean ?: false
        val topName = saved.getOrNull(2) as? String
        XBottomSheetState(skipCollapsed, initialLoading, peekFraction, customAnchors).apply {
            restore(
                value = sheetValueFromTag(tag),
                loading = loading,
                additionalTop = additionalTopFromName(topName),
            )
        }
    },
)

private fun additionalTopFromName(name: String?): AdditionalTopState =
    AdditionalTopState.entries.firstOrNull { entry -> entry.name == name } ?: AdditionalTopState.Expanded

private fun sheetValueTag(value: SheetValue): String = when (value) {
    SheetValue.Hidden -> "h"
    SheetValue.Content -> "c"
    SheetValue.Collapsed -> "col"
    SheetValue.ExpandedContent -> "ec"
    SheetValue.ExpandedFullScreen -> "efs"
    SheetValue.Loading -> "l"
    is SheetValue.Custom -> "cu:${value.key}"
}

private fun sheetValueFromTag(tag: String): SheetValue = when {
    tag == "h" -> SheetValue.Hidden
    tag == "c" -> SheetValue.Content
    tag == "col" -> SheetValue.Collapsed
    tag == "ec" -> SheetValue.ExpandedContent
    tag == "efs" -> SheetValue.ExpandedFullScreen
    tag == "l" -> SheetValue.Loading
    tag.startsWith("cu:") -> SheetValue.Custom(tag.removePrefix("cu:"))
    else -> SheetValue.Hidden
}

// Высота Collapsed по умолчанию — 2/3 экрана (требование юзера, п.1). Разраб переопределяет через peekFraction.
internal const val DEFAULT_PEEK_FRACTION: Float = 2f / 3f
