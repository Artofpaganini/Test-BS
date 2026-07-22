package com.onexui.bottomsheet.state

import androidx.compose.animation.core.Animatable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.State
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
import org.xplatform.uikit.compose.modifier.keyboard.lift.KeyboardLiftState
import kotlin.math.roundToInt
import kotlin.time.Duration.Companion.milliseconds

/**
 * Стейт-машина высоты листа и единый набор живых поведенческих ручек — одна сущность для разработчка
 * (канон M3 SheetState: поведение живёт в стейте, а не в отдельном config-объекте). Значения — Compose-state
 * (mutableStateOf), не StateFlow; вычисляются по метрикам замера и фактам. Режим wrap/fill определяется
 * замером (SheetMetrics.isFillMode).
 *
 * Живые ручки (skipCollapsed/peekFraction/anchors) меняются прямо в композиции: покоящийся лист доезжает к
 * новому якорю сам (onLiveConfigChanged), присвоение равного значения — no-op.
 *
 * @property skipCollapsed есть ли промежуточный Collapsed-якорь; false — открываем сразу в раскрытый стейт.
 * @property peekFraction доля высоты экрана для Collapsed-якоря (0..1).
 * @property anchors кастомные rest-якоря fill-режима (DSL `"half" at 0.5f`).
 * @property currentValue текущий логический стейт высоты; пишется только внутри стейта.
 * @property isLoading идёт ли загрузка контента (в Middle — Loader вместо контента).
 * @property initialLoading стартовое значение isLoading, заданное билдером.
 */
@Stable
internal class XBottomSheetState internal constructor(
    skipCollapsed: Boolean,
    val initialLoading: Boolean,
    peekFraction: Float,
    anchors: List<XSheetAnchor>,
) {
    var skipCollapsed: Boolean by mutableStateOf(skipCollapsed)
    var peekFraction: Float by mutableStateOf(peekFraction)
    var anchors: List<XSheetAnchor> by mutableStateOf(anchors)
        private set

    /** Меняет набор якорей той же DSL-грамматикой, что и билдер (`"half" at 0.5f`). */
    fun anchors(configure: XSheetAnchorsBuilder.() -> Unit) {
        anchors = XSheetAnchorsBuilder().apply(configure).build()
    }

    var currentValue: SheetValue by mutableStateOf(SheetValue.Hidden)
        private set

    var isLoading by mutableStateOf(initialLoading)
        internal set

    var additionalTopState by mutableStateOf(AdditionalTopState.Expanded)

    internal var metrics by mutableStateOf<SheetMetrics?>(null)
        private set

    /**
     * Текущая высота листа, px. Пишут только suspend-методы стейта (drag -> snapTo, анимация -> animateTo),
     * читается в measure-фазе измерителя (deferred) — без рекомпозиций.
     */
    internal val offset = Animatable(0f)

    internal var isDragging by mutableStateOf(false)

    private var accumulatedOvershootPx = 0f

    /** Якорная таблица (rest-якоря + settle-математика); пересчитывается только в updateMetrics. null — пока нет метрик. */
    private var anchorTable: SheetAnchorTable? = null

    /** Рест-стейт до авто-промоушена в FullScreen из-за IME: при скрытии клавиатуры откатываемся к нему. */
    private var imePromotedFrom: SheetValue? = null

    /**
     * Live-состояние IME (xbet KeyboardLiftState) — читается напрямую в shouldPromoteForIme, без копий-снапшотов.
     * Вписывается SideEffect'ом корня, как dismissOnSwipeDown.
     */
    internal var keyboardState: State<KeyboardLiftState>? = null

    /** Конфиг-флаг StayUnderKeyboard + bottom-слот: bottom уходит под клавиатуру -> лист форсим в FullScreen. */
    internal var alwaysFullScreenOnIme: Boolean = false

    val isVisible: Boolean get() = currentValue != SheetValue.Hidden

    /** Идёт ли анимация высоты (snapshot-state offset.isRunning, наблюдаемо из snapshotFlow). */
    val isAnimating: Boolean get() = offset.isRunning

    private suspend fun awaitMetrics(): SheetMetrics =
        metrics ?: snapshotFlow { metrics }.filterNotNull().first()

    /** Открывает лист (если ещё не открыт): в Loading — к Loader-якорю, иначе к openTarget по замеру. */
    suspend fun show() {
        // Уже открыт (в т.ч. восстановлен из Saver) — не переоткрываем: анимация к openTarget сбросила бы стейт.
        // Высоту к якорю снапнет snapToCurrentAnchor.
        if (isVisible) return
        val measured = awaitMetrics()
        animateTo(if (isLoading) SheetValue.Loading else measured.openTarget(skipCollapsed))
    }

    /** Восстановление из Saver. offset снапнется к якорю с приходом метрик (snapToCurrentAnchor). */
    internal fun restore(value: SheetValue, isLoadingSaved: Boolean, additionalTop: AdditionalTopState) {
        currentValue = value
        isLoading = isLoadingSaved
        additionalTopState = additionalTop
    }

    /** Раскрывает лист из Collapsed в expandTarget. */
    suspend fun expand() {
        val measured = metrics ?: return
        imePromotedFrom = null
        if (currentValue == SheetValue.Collapsed) animateTo(measured.expandTarget())
    }

    /** Закрывает лист (анимация к Hidden). */
    suspend fun hide() {
        animateTo(SheetValue.Hidden)
    }

    /**
     * Снимает Loading и доводит лист до рест-стейта по РЕАЛЬНОМУ контенту (ждёт ре-замер после снятия isLoading).
     * Если IME уже открыта — тот же авто-FullScreen, что и onImeShown (иначе верх листа уехал бы за экран).
     * Закрытый лист не переоткрывает.
     */
    suspend fun markContentReady() {
        // Закрыли во время Loading: снимаем флаг и выходим, иначе openTarget поднял бы закрытый лист.
        if (!isVisible) {
            isLoading = false
            return
        }
        val loaderMetrics = metrics
        isLoading = false
        // На таймаут (measure заморожен на фоне) stale-метрики не берём — ждём реальный ре-замер без лимита.
        val measured = withTimeoutOrNull(REMEASURE_TIMEOUT_MS.milliseconds) {
            awaitContentMetrics(loaderMetrics)
        } ?: awaitContentMetrics(loaderMetrics)
        // hide() мог прилететь за время ожидания ре-замера.
        if (!isVisible) return
        val target = measured.openTarget(skipCollapsed)
        if (shouldPromoteForIme(target, measured)) {
            imePromotedFrom = target
            animateTo(SheetValue.ExpandedFullScreen)
        } else {
            // Сброс промоушена: скрытие IME не должно откатывать к Loading.
            imePromotedFrom = null
            animateTo(target)
        }
    }

    /** Ждёт первый инстанс метрик, отличный от Loader-метрик по ссылке — то есть ре-замер по реальному контенту. */
    private suspend fun awaitContentMetrics(loaderMetrics: SheetMetrics?): SheetMetrics =
        snapshotFlow { metrics }.filterNotNull().first { remeasured -> remeasured !== loaderMetrics }

    /**
     * Контент вырос/подгрузился: высота следует за текущим якорем. skipCollapsed+Content, заполнивший экран
     * (isFillMode) -> авто-FullScreen. В Loading — no-op (высоту ведут show/markContentReady).
     */
    internal suspend fun onContentRemeasured() {
        val measured = metrics ?: return
        if (!isVisible || currentValue == SheetValue.Loading) return
        if (skipCollapsed && currentValue == SheetValue.Content && measured.isFillMode) {
            animateTo(SheetValue.ExpandedFullScreen)
        } else {
            offset.animateTo(measured.anchorPx(currentValue, skipCollapsed).toFloat(), NativeSheetSpring)
        }
    }

    /**
     * Живые поля (skipCollapsed/peekFraction/anchors) сменились в композиции: пере-резолвим метрики и якорную
     * таблицу под новые значения; если лист в покое (виден, не грузится, палец не тянет) — доводим высоту к новому
     * якорю сразу, не дожидаясь жеста. Закрытый лист/Loading оставляем как есть.
     */
    internal suspend fun onLiveConfigChanged() {
        val current = metrics ?: return
        // copy обязателен: updateMetrics при неизменных размерах рано выходит и держал бы старый peekFraction/anchors.
        val rebuilt = current.copy(peekFraction = peekFraction, customAnchors = anchors)
        metrics = rebuilt
        val table = rebuilt.toAnchorTable(skipCollapsed)
        anchorTable = table
        if (!isVisible || currentValue == SheetValue.Loading || isDragging) return
        val target = resolveRestTargetAfterConfigChange(table)
        if (target != currentValue) currentValue = target
        offset.animateTo(rebuilt.anchorPx(target, skipCollapsed).toFloat(), NativeSheetSpring)
    }

    /**
     * Цель для доводки после смены живых полей. Текущее value валидно -> оно же. Custom(key) с исчезнувшим из
     * anchors ключом -> ближайший существующий rest-якорь (settleTarget v=0, без dismiss), НЕ закрытие листа.
     */
    private fun resolveRestTargetAfterConfigChange(table: SheetAnchorTable): SheetValue {
        val value = currentValue
        if (value is SheetValue.Custom && anchors.none { anchor -> anchor.key == value.key }) {
            return table.settleTarget(offset.value, 0f, isDismissAllowed = false, flingVelocityThresholdPxPerSec)
                ?: value
        }
        return value
    }

    /**
     * Нужно ли форсировать FullScreen при видимой IME. Не-FullScreen лист поднимается withAdjustmentForKeyboard
     * на ПОЛНУЮ высоту клавиатуры (безусловный подъём), поэтому якорь + IME выше потолка -> верх уехал бы за экран.
     * В FullScreen подъёма нет (Middle сжимается). alwaysFullScreenOnIme — режим StayUnderKeyboard.
     */
    private fun shouldPromoteForIme(value: SheetValue, measured: SheetMetrics): Boolean {
        val ime = keyboardState?.value ?: return false
        if (!ime.isKeyboardVisible) return false
        // Loading: подъём швырнул бы Loader на полную высоту IME мгновенно; FullScreen сжимает его покадрово с клавиатурой.
        if (value == SheetValue.Loading) return true
        val promotable = value == SheetValue.Content || value == SheetValue.Collapsed ||
            value == SheetValue.ExpandedContent || value is SheetValue.Custom
        if (!promotable) return false
        return alwaysFullScreenOnIme ||
            measured.anchorPx(value, skipCollapsed) + ime.keyboardHeight.roundToInt() > measured.maxHeightPx
    }

    /** IME показалась: при нехватке места промоутим в FullScreen, запоминая рест-стейт для отката. */
    internal suspend fun onImeShown() {
        val measured = metrics ?: return
        if (!isVisible) return
        if (shouldPromoteForIme(currentValue, measured)) {
            imePromotedFrom = currentValue
            animateTo(SheetValue.ExpandedFullScreen)
        }
    }

    /** IME скрылась: откат из авто-FullScreen к рест-стейту, запомненному при промоушене. */
    internal suspend fun onImeHidden() {
        val target = imePromotedFrom ?: return
        imePromotedFrom = null
        if (currentValue == SheetValue.ExpandedFullScreen) animateTo(target)
    }

    /** Снап высоты к якорю текущего стейта без анимации (поворот/resize). Поверх бегущей анимации не снапает. */
    internal suspend fun snapToCurrentAnchor() {
        val measured = metrics ?: return
        if (!isVisible || offset.isRunning) return
        offset.snapTo(measured.anchorPx(currentValue, skipCollapsed).toFloat())
    }

    /**
     * Пересобирает метрики и якорную таблицу по свежему замеру. Зовётся из measure на КАЖДОМ пассе, поэтому
     * сравнивает примитивы до аллокации — равный SheetMetrics не пишем (иначе snapshot грязнится каждый layout-пасс).
     */
    internal fun updateMetrics(
        screenHeightPx: Int,
        statusBarPx: Int,
        contentHeightPx: Int,
        loadingSheetHeightPx: Int,
    ) {
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
            customAnchors = anchors,
        )
        metrics = updated
        anchorTable = updated.toAnchorTable(skipCollapsed)
    }

    /** FIFO-канал жестов (drag/settle): единственный вход, потребитель — processGestures. */
    private val gestureCommands = Channel<GestureCommand>(Channel.UNLIMITED)

    /** Разрешён ли свайп-вниз-закрытие (settle добавляет Hidden-якорь на 0px). */
    internal var dismissOnSwipeDown: Boolean = true

    /** Не-suspend launcher закрытия: скрим/settle/back/requestDismiss зовут синхронно. Вписывается SideEffect'ом корня. */
    internal var onDismissRequest: () -> Unit = {}

    /**
     * Физика жестов из config, вписывается SideEffect'ом корня. Старт 0f не дублирует источник: жесты невозможны
     * до первой композиции корня, где вайринг уже произошёл.
     */
    internal var flingVelocityThresholdPxPerSec: Float = 0f
    internal var resistanceMaxPx: Float = 0f

    /** Кладёт drag-дельту в FIFO-канал жестов и помечает лист тянущимся. */
    internal fun enqueueDrag(deltaHeightPx: Float) {
        isDragging = true
        gestureCommands.trySend(GestureCommand.Drag(deltaHeightPx))
    }

    /** Кладёт settle (по скорости отпускания) в FIFO-канал жестов. */
    internal fun enqueueSettle(velocity: Float) {
        gestureCommands.trySend(GestureCommand.Settle(velocity))
    }

    /**
     * Единственный потребитель FIFO-канала: применяет drag/settle по порядку. Сериализация против гонки
     * drag↔settle (иначе поздний dragBy.snapTo отменил бы settle-анимацию возврата).
     */
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

    /**
     * Живой драг высоты. Обычный ход ограничен верхним rest-якорем (и floor снизу); overshoot над ним — с
     * сопротивлением (wrap -> к потолку; fill/FullScreen -> rubber-band в зону статус-бара, spring назад).
     */
    private suspend fun dragBy(deltaHeightPx: Float) {
        val measured = metrics ?: return
        val table = anchorTable ?: return
        val floorPx = if (dismissOnSwipeDown) 0f else table.lowestRestAnchorPx.toFloat()
        val atTop = currentValue == SheetValue.ExpandedFullScreen
        // atTop — база maxHeight (rubber-band в статус-бар); иначе верхний rest-якорь.
        val overshootBase = if (atTop) measured.maxHeightPx.toFloat() else table.highestRestAnchorPx.toFloat()
        val overshootCeiling =
            if (atTop) (measured.maxHeightPx + resistanceMaxPx) else measured.maxHeightPx.toFloat()
        // Overshoot по позиции, не по currentValue: срабатывает и при драге из Collapsed.
        val inOvershoot = accumulatedOvershootPx > 0f ||
            (offset.value >= overshootBase - OVERSHOOT_ENTER_EPS_PX && deltaHeightPx > 0f)
        if (inOvershoot) {
            accumulatedOvershootPx = (accumulatedOvershootPx + deltaHeightPx).coerceAtLeast(0f)
            val resisted = resistedOvershoot(accumulatedOvershootPx, resistanceMaxPx)
            offset.snapTo((overshootBase + resisted).coerceAtMost(overshootCeiling))
        } else {
            accumulatedOvershootPx = 0f
            offset.snapTo((offset.value + deltaHeightPx).coerceIn(floorPx, overshootBase))
        }
    }

    /** Довод к якорю после отпускания: по скорости/ближайшему из таблицы; Hidden -> свайп-закрытие (если разрешено). */
    private suspend fun settle(velocity: Float) {
        isDragging = false
        accumulatedOvershootPx = 0f
        imePromotedFrom = null
        val table = anchorTable ?: return
        val target = table.settleTarget(offset.value, velocity, dismissOnSwipeDown, flingVelocityThresholdPxPerSec)
        when {
            target == null -> animateTo(currentValue)
            target == SheetValue.Hidden -> onDismissRequest()
            else -> animateTo(target)
        }
    }

    /** Стоит ли лист на rest-якоре: onPreFling не съедает инерцию списка, если лист уже дорос до якоря. */
    internal fun isOffsetAtRestAnchor(): Boolean = anchorTable?.isAtRestAnchor(offset.value) ?: false

    /** Меняет логический стейт и анимирует высоту к его якорю пружиной NativeSheetSpring. */
    private suspend fun animateTo(target: SheetValue) {
        currentValue = target
        val anchor = metrics?.anchorPx(target, skipCollapsed)?.toFloat() ?: 0f
        offset.animateTo(anchor, NativeSheetSpring)
    }

    private companion object {
        const val REMEASURE_TIMEOUT_MS = 500L
        // Допуск «у верхнего якоря» — гасит float-дрожание на границе перед началом сопротивления.
        const val OVERSHOOT_ENTER_EPS_PX = 0.5f
    }
}
