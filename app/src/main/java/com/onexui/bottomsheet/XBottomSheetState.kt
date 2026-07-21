package com.onexui.bottomsheet

import androidx.compose.animation.core.Animatable
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
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
// применяется. Режим (wrap по контенту / fill фикс-якоря) определяется замером контента (см. SheetMetrics.isFillMode),
// стейт ВЫЧИСЛЯЕТСЯ по метрикам и фактам (skipCollapsed, isLoading, кастомные якоря).
@Stable
internal class XBottomSheetState internal constructor(
    config: XBottomSheetStateConfig,
) {
    val skipCollapsed: Boolean = config.skipCollapsed
    val peekFraction: Float = config.peekFraction
    val customAnchors: List<XSheetAnchor> = config.anchors

    var currentValue: SheetValue by mutableStateOf(SheetValue.Hidden)
        private set

    var isLoading by mutableStateOf(config.initialLoading)
        internal set

    var additionalTopState by mutableStateOf(AdditionalTopState.Expanded)

    internal var metrics by mutableStateOf<SheetMetrics?>(null)
        private set

    // Текущая высота листа в px. Единственный писатель — suspend-методы стейта (drag = snapTo, анимация =
    // animateTo). Читается в measure-лямбде измерителя (deferred), без рекомпозиций.
    internal val offset = Animatable(0f)

    internal var isDragging by mutableStateOf(false)

    private var accumulatedOvershootPx = 0f

    // Кэш нижнего рест-якоря (floor при dismissOnSwipeDown=false): пересчитывается ТОЛЬКО при фактической смене
    // метрик (updateMetrics), а не на каждом кадре драга. dragBy читает поле вместо аллокации списка кандидатов.
    private var lowestAllowedAnchorPx = 0

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
    internal fun restore(value: SheetValue, isLoadingSaved: Boolean, additionalTop: AdditionalTopState) {
        currentValue = value
        isLoading = isLoadingSaved
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

    suspend fun markContentReady() {
        // Лист закрыли (тап/hide()) во время Loading — НЕ переоткрываем сами: снимаем Loading и выходим,
        // иначе animateTo(openTarget) поднял бы закрытый лист без действия юзера. (одобрено)
        if (!isVisible) {
            isLoading = false
            return
        }
        val loaderMetrics = metrics
        isLoading = false
        val measured = withTimeoutOrNull(REMEASURE_TIMEOUT_MS) {
            snapshotFlow { metrics }.filterNotNull().first { remeasured -> remeasured !== loaderMetrics }
        } ?: awaitMetrics()
        // hide() мог прилететь в окно ожидания ре-замера — повторная проверка перед анимацией.
        if (!isVisible) return
        animateTo(measured.openTarget(skipCollapsed))
    }

    // Контент изменился (подгрузка/рост): высота следует за текущим якорем. В wrap+skipCollapsed+Content, если
    // контент заполнил экран — авто FullScreen. В Loading — no-op (высоту ведут show/markContentReady).
    // Признак overflow — measured.isFillMode (contentHeightPx >= maxHeightPx): при клампящем SubcomposeLayout
    // contentHeightPx НИКОГДА не превышает maxHeightPx (только равен), поэтому `> maxHeightPx` был мёртвым —
    // currentValue оставался Content вместо ExpandedFullScreen → неверный IME-гейтинг/resistance. (одобрено)
    internal suspend fun onContentRemeasured() {
        val measured = metrics ?: return
        if (!isVisible || currentValue == SheetValue.Loading) return
        if (skipCollapsed && currentValue == SheetValue.Content && measured.isFillMode) {
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
        if (updated != metrics) {
            metrics = updated
            lowestAllowedAnchorPx = computeLowestAllowedAnchorPx(updated)
        }
    }

    // Нижний рест-якорь (наименьшая px-высота среди рест-стейтов, без Hidden) — прямой min-перебор веток режима
    // без списка/Pair/distinctBy/sortedBy. Семантика 1:1 с settleCandidates(dismissOnSwipeDown=false).firstOrNull().
    private fun computeLowestAllowedAnchorPx(measured: SheetMetrics): Int = when {
        measured.isFillMode -> {
            var lowest = measured.maxHeightPx
            if (!skipCollapsed) lowest = minOf(lowest, measured.peekPx)
            measured.customAnchors.forEach { anchor -> lowest = minOf(lowest, measured.customAnchorPx(anchor.key)) }
            lowest
        }
        skipCollapsed -> measured.anchorPx(SheetValue.Content, skipCollapsed = true)
        measured.contentHeightPx <= measured.peekPx -> measured.anchorPx(SheetValue.Content, skipCollapsed = false)
        else -> measured.peekPx
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
        val floorPx = if (dismissOnSwipeDown) 0f else lowestAllowedAnchorPx.toFloat()
        val atTop = currentValue == SheetValue.ExpandedFullScreen
        val resistanceState = currentValue == SheetValue.Content ||
            currentValue == SheetValue.ExpandedContent || atTop
        val overshootCeiling = if (atTop) {
            (measured.maxHeightPx + RESISTANCE_MAX_PX)
        } else {
            measured.maxHeightPx.toFloat()
        }
        val inOvershoot = resistanceState &&
            (accumulatedOvershootPx > 0f || (offset.value >= anchor - OVERSHOOT_ENTER_EPS_PX && deltaHeightPx > 0f))
        if (inOvershoot) {
            accumulatedOvershootPx = (accumulatedOvershootPx + deltaHeightPx).coerceAtLeast(0f)
            val resisted = resistedOvershoot(accumulatedOvershootPx, RESISTANCE_MAX_PX)
            offset.snapTo((anchor + resisted).coerceAtMost(overshootCeiling))
        } else {
            accumulatedOvershootPx = 0f
            offset.snapTo((offset.value + deltaHeightPx).coerceIn(floorPx, measured.maxHeightPx.toFloat()))
        }
    }

    // Settle: по скорости и ближайшему якорю. Hidden — свайп-закрытие (если dismissOnSwipeDown).
    private suspend fun settle(velocity: Float) {
        isDragging = false
        accumulatedOvershootPx = 0f
        imePromotedFrom = null
        val measured = metrics ?: return
        val candidates = buildSettleCandidates(measured, dismissOnSwipeDown)
        if (candidates.isEmpty()) {
            animateTo(currentValue)
            return
        }
        val current = offset.value
        val chosen = when {
            velocity < -FLING_VELOCITY_THRESHOLD ->
                candidates.firstOrNull { candidate -> candidate.anchorPx > current + ANCHOR_EPS } ?: candidates.last()
            velocity > FLING_VELOCITY_THRESHOLD ->
                candidates.lastOrNull { candidate -> candidate.anchorPx < current - ANCHOR_EPS } ?: candidates.first()
            else ->
                candidates.minByOrNull { candidate -> abs(candidate.anchorPx - current) } ?: candidates.first()
        }
        if (chosen.value == SheetValue.Hidden) onDismissRequest() else animateTo(chosen.value)
    }

    // Отсортированные по высоте якоря доступных стейтов. fill-режим — Collapsed(peek)/ExpandedFullScreen(full) +
    // кастомные; wrap-режим — Content/Collapsed/ExpandedContent по контенту. Hidden — если разрешён свайп-close.
    private fun buildSettleCandidates(
        measured: SheetMetrics,
        dismissOnSwipeDown: Boolean,
    ): List<AnchorCandidate> {
        val list = mutableListOf<AnchorCandidate>()
        when {
            measured.isFillMode -> {
                if (!skipCollapsed) list.add(AnchorCandidate(SheetValue.Collapsed, measured.peekPx))
                list.add(AnchorCandidate(SheetValue.ExpandedFullScreen, measured.maxHeightPx))
                measured.customAnchors.forEach { anchor ->
                    list.add(AnchorCandidate(SheetValue.Custom(anchor.key), measured.customAnchorPx(anchor.key)))
                }
            }
            skipCollapsed ->
                list.add(AnchorCandidate(SheetValue.Content, measured.anchorPx(SheetValue.Content, skipCollapsed = true)))
            measured.contentHeightPx <= measured.peekPx ->
                list.add(AnchorCandidate(SheetValue.Content, measured.anchorPx(SheetValue.Content, skipCollapsed = false)))
            else -> {
                list.add(AnchorCandidate(SheetValue.Collapsed, measured.peekPx))
                val expanded = measured.expandTarget()
                list.add(AnchorCandidate(expanded, measured.anchorPx(expanded, skipCollapsed = false)))
            }
        }
        if (dismissOnSwipeDown) list.add(AnchorCandidate(SheetValue.Hidden, 0))
        return list.distinctBy { candidate -> candidate.anchorPx }.sortedBy { candidate -> candidate.anchorPx }
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
        // Порог входа в overshoot: считаем, что лист «у верхнего якоря», если offset в пределах этого допуска
        // ниже якоря (гасит дрожание float-сравнения на границе перед началом сопротивления).
        const val OVERSHOOT_ENTER_EPS_PX = 0.5f
    }
}

// Кандидат-якорь для settle: стейт + его высота в px (замена безымянному Pair<SheetValue, Int>).
private data class AnchorCandidate(val value: SheetValue, val anchorPx: Int)

// Конфиг стейта — чистые данные (правило 10, без лямбд). Value-equality: configure-лямбда исполняется каждую
// рекомпозицию, но равный по значению конфиг НЕ инвалидирует ключ rememberSaveable (тот же контракт, что был у
// позиционных ключей). Смена конфига пересоздаёт стейт.
@Immutable
internal data class XBottomSheetStateConfig(
    val skipCollapsed: Boolean,
    val initialLoading: Boolean,
    val peekFraction: Float,
    val anchors: List<XSheetAnchor>,
)

@XBottomSheetDsl
internal class XBottomSheetStateConfigBuilder {
    var skipCollapsed: Boolean = false
    var initialLoading: Boolean = false
    var peekFraction: Float = XBottomSheetDefaults.PeekFraction
    private val anchorsBuilder = XSheetAnchorsBuilder()

    fun anchors(configure: XSheetAnchorsBuilder.() -> Unit) {
        anchorsBuilder.configure()
    }

    internal fun build(): XBottomSheetStateConfig = XBottomSheetStateConfig(
        skipCollapsed = skipCollapsed,
        initialLoading = initialLoading,
        peekFraction = peekFraction,
        anchors = anchorsBuilder.build(),
    )
}

@XBottomSheetDsl
internal class XSheetAnchorsBuilder {
    private val anchors = mutableListOf<XSheetAnchor>()

    fun anchor(key: String, heightFraction: Float) {
        anchors.add(XSheetAnchor(key = key, heightFraction = heightFraction))
    }

    internal fun build(): List<XSheetAnchor> = anchors.toList()
}

// rememberSaveable: стейт листа (currentValue/разворот, isLoading, additionalTopState) переживает ротацию и
// process-death. Позиция скролла контента переживает через свой rememberLazyListState/rememberScrollState.
@Composable
internal fun rememberXBottomSheetState(
    configure: XBottomSheetStateConfigBuilder.() -> Unit = {},
): XBottomSheetState {
    val config = XBottomSheetStateConfigBuilder().apply(configure).build()
    return rememberSaveable(config, saver = xBottomSheetStateSaver(config)) {
        XBottomSheetState(config)
    }
}

// Saver: сохраняет тег стейта + isLoading + имя additionalTopState (всё Bundle-совместимо). Конфиг передаётся в
// конструктор из фабрики (не сохраняется — задан разработчиком).
private fun xBottomSheetStateSaver(config: XBottomSheetStateConfig): Saver<XBottomSheetState, List<Any>> = Saver(
    save = { state ->
        listOf(sheetValueTag(state.currentValue), state.isLoading, state.additionalTopState.name)
    },
    restore = { saved ->
        val tag = saved.getOrNull(0) as? String ?: "h"
        val loading = saved.getOrNull(1) as? Boolean ?: false
        val topName = saved.getOrNull(2) as? String
        XBottomSheetState(config).apply {
            restore(
                value = sheetValueFromTag(tag),
                isLoadingSaved = loading,
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
