package com.onexui.bottomsheet.state

import android.util.Log
import androidx.compose.animation.core.Animatable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import com.onexui.bottomsheet.NativeSheetSpring
import com.onexui.bottomsheet.additionaltop.AdditionalTopState
import com.onexui.bottomsheet.gesture.resistedOvershoot
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.math.abs
import kotlin.time.Duration.Companion.milliseconds

// Стейт-машина высоты листа. Compose-state (mutableStateOf), не StateFlow. Стейт вычисляется по метрикам и
// фактам (skipCollapsed / isLoading / кастомные якоря); режим wrap/fill — по замеру (SheetMetrics.isFillMode).
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

    // Текущая высота листа, px. Единственный писатель — suspend-методы стейта (drag = snapTo, анимация =
    // animateTo); читается в measure-лямбде измерителя (deferred), без рекомпозиций.
    internal val offset = Animatable(0f)

    internal var isDragging by mutableStateOf(false)

    private var accumulatedOvershootPx = 0f

    // Кэш рест-якорей (пересчёт только в updateMetrics): lowest — floor при dismissOnSwipeDown=false;
    // highest — потолок обычного драга (выше — только overshoot с сопротивлением).
    private var lowestAllowedAnchorPx = 0
    private var highestAllowedAnchorPx = 0

    // Стейт до авто-промоушена в FullScreen из-за клавиатуры: при скрытии IME откатываемся к нему.
    private var imePromotedFrom: SheetValue? = null

    init {
        if (customAnchors.isNotEmpty()) {
            Log.w(
                "XBottomSheet",
                "customAnchors работают только в fill-режиме (контент ≥ экрана); в wrap-режиме игнорируются",
            )
        }
    }

    val isVisible: Boolean get() = currentValue != SheetValue.Hidden

    private suspend fun awaitMetrics(): SheetMetrics =
        metrics ?: snapshotFlow { metrics }.filterNotNull().first()

    suspend fun show() {
        // Уже открыт (в т.ч. восстановлен из Saver) — не переоткрываем: иначе анимация к openTarget сбросила бы
        // стейт. Высоту к якорю снапнет snapToCurrentAnchor.
        if (isVisible) return
        val measured = awaitMetrics()
        animateTo(if (isLoading) SheetValue.Loading else measured.openTarget(skipCollapsed))
    }

    // Восстановление из Saver. offset снапнется к якорю с приходом метрик (snapToCurrentAnchor).
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
        // Лист закрыли во время Loading — не переоткрываем: снимаем Loading и выходим (иначе openTarget поднял
        // бы закрытый лист сам по себе).
        if (!isVisible) {
            isLoading = false
            return
        }
        val loaderMetrics = metrics
        isLoading = false
        // openTarget по РЕАЛЬНОМУ контенту, не по Loader-метрикам: ждём первый инстанс метрик после снятия
        // isLoading (ссылка != loaderMetrics). На таймаут (measure заморожен на фоне) stale-метрики не
        // принимаем — ждём реальные без ограничения (реанимируются на foreground).
        val measured = withTimeoutOrNull(REMEASURE_TIMEOUT_MS.milliseconds) {
            awaitContentMetrics(loaderMetrics)
        } ?: awaitContentMetrics(loaderMetrics)
        // hide() мог прилететь за время ожидания ре-замера — повторная проверка перед анимацией.
        if (!isVisible) return
        animateTo(measured.openTarget(skipCollapsed))
    }

    private suspend fun awaitContentMetrics(loaderMetrics: SheetMetrics?): SheetMetrics =
        snapshotFlow { metrics }.filterNotNull().first { remeasured -> remeasured !== loaderMetrics }

    // Контент изменился (рост/подгрузка): высота следует за якорем. skipCollapsed+Content и контент заполнил
    // экран (isFillMode) → авто-FullScreen. В Loading — no-op (высоту ведут show/markContentReady).
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
        // offset.isRunning: не снапаем поверх бегущей анимации (settle/show) — snapTo прервал бы её (мьютекс Animatable).
        if (!isVisible || offset.isRunning) return
        offset.snapTo(measured.anchorPx(currentValue, skipCollapsed).toFloat())
    }

    internal fun updateMetrics(
        screenHeightPx: Int,
        statusBarPx: Int,
        contentHeightPx: Int,
        loadingSheetHeightPx: Int,
    ) {
        // updateMetrics зовётся из measure на КАЖДОМ пассе (кадре драга/settle) — сравниваем примитивы до
        // аллокации, чтобы не писать равный SheetMetrics (иначе snapshot грязнится каждый layout-пасс).
        val current = metrics
        if (current != null &&
            current.screenHeightPx == screenHeightPx &&
            current.statusBarPx == statusBarPx &&
            current.contentHeightPx == contentHeightPx &&
            current.loadingSheetHeightPx == loadingSheetHeightPx
        ) {
            return
        }
        val updated = SheetMetrics(
            screenHeightPx = screenHeightPx,
            statusBarPx = statusBarPx,
            contentHeightPx = contentHeightPx,
            loadingSheetHeightPx = loadingSheetHeightPx,
            peekFraction = peekFraction,
            customAnchors = customAnchors,
        )
        metrics = updated
        lowestAllowedAnchorPx = computeLowestAllowedAnchorPx(updated)
        highestAllowedAnchorPx = computeHighestAllowedAnchorPx(updated)
    }

    // Нижний рест-якорь (минимальная высота среди рест-стейтов, без Hidden) — floor при dismissOnSwipeDown=false.
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

    // Верхний рест-якорь — потолок обычного драга: выше только overshoot с сопротивлением.
    private fun computeHighestAllowedAnchorPx(measured: SheetMetrics): Int = when {
        measured.isFillMode -> measured.maxHeightPx
        skipCollapsed -> measured.anchorPx(SheetValue.Content, skipCollapsed = true)
        measured.contentHeightPx <= measured.peekPx -> measured.anchorPx(SheetValue.Content, skipCollapsed = false)
        else -> measured.anchorPx(measured.expandTarget(), skipCollapsed = false)
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

    // Единственный потребитель FIFO-канала команд: применяет drag/settle по порядку. Сериализация против гонки
    // drag↔settle (иначе прилетевший позже dragBy.snapTo отменил бы settle-анимацию возврата).
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

    // Живой драг: двигаем высоту. Обычный драг ограничен верхним rest-якорем (и floor снизу); overshoot над
    // ним — с сопротивлением (wrap → к потолку; fill/FullScreen → rubber-band в зону статус-бара, spring назад).
    private suspend fun dragBy(deltaHeightPx: Float) {
        val measured = metrics ?: return
        val floorPx = if (dismissOnSwipeDown) 0f else lowestAllowedAnchorPx.toFloat()
        val atTop = currentValue == SheetValue.ExpandedFullScreen
        // atTop — база maxHeight (rubber-band в статус-бар); иначе — верхний rest-якорь (overshoot тянет к maxHeight).
        val overshootBase = if (atTop) measured.maxHeightPx.toFloat() else highestAllowedAnchorPx.toFloat()
        val overshootCeiling = if (atTop) (measured.maxHeightPx + RESISTANCE_MAX_PX) else measured.maxHeightPx.toFloat()
        // Overshoot — когда лист у/над верхним rest-якорем (не по currentValue): включает и драг из Collapsed.
        val inOvershoot = accumulatedOvershootPx > 0f ||
            (offset.value >= overshootBase - OVERSHOOT_ENTER_EPS_PX && deltaHeightPx > 0f)
        if (inOvershoot) {
            accumulatedOvershootPx = (accumulatedOvershootPx + deltaHeightPx).coerceAtLeast(0f)
            val resisted = resistedOvershoot(accumulatedOvershootPx, RESISTANCE_MAX_PX)
            offset.snapTo((overshootBase + resisted).coerceAtMost(overshootCeiling))
        } else {
            accumulatedOvershootPx = 0f
            offset.snapTo((offset.value + deltaHeightPx).coerceIn(floorPx, overshootBase))
        }
    }

    // Settle: по скорости и ближайшему якорю; Hidden → свайп-закрытие (если dismissOnSwipeDown).
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

    // Якоря доступных стейтов, отсортированные по высоте. Hidden — если разрешён свайп-close.
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

    // Лист стоит на rest-якоре? onPreFling не съедает инерцию списка, если лист уже дорос до якоря.
    internal fun isOffsetAtRestAnchor(): Boolean {
        val measured = metrics ?: return false
        val current = offset.value
        return buildSettleCandidates(measured, dismissOnSwipeDown).any { candidate ->
            candidate.value != SheetValue.Hidden && abs(candidate.anchorPx - current) < ANCHOR_EPS
        }
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
        // Допуск «у верхнего якоря» — гасит float-дрожание на границе перед началом сопротивления.
        const val OVERSHOOT_ENTER_EPS_PX = 0.5f
    }
}

private data class AnchorCandidate(val value: SheetValue, val anchorPx: Int)
