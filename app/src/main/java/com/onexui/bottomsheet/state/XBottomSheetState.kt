package com.onexui.bottomsheet.state

import androidx.compose.animation.core.Animatable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import com.onexui.bottomsheet.NativeSheetSpring
import com.onexui.bottomsheet.XBottomSheetDefaults
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

    // Якорная таблица (rest-якоря + settle-математика), пересчёт только в updateMetrics. null — пока нет метрик.
    private var anchorTable: SheetAnchorTable? = null

    // Рест-стейт до авто-промоушена в FullScreen из-за IME: при скрытии клавиатуры откатываемся к нему.
    private var imePromotedFrom: SheetValue? = null

    // Единственные источники правды для решения о промоушене (без копий-снапшотов): keyboardState — live-состояние
    // IME (xbet KeyboardLiftState); alwaysFullScreenOnIme — конфиг-флаг (StayUnderKeyboard + bottom). Оба вписываются
    // SideEffect'ом корня тем же путём, что dismissOnSwipeDown/onDismissRequest.
    internal var keyboardState: State<KeyboardLiftState>? = null
    internal var alwaysFullScreenOnIme: Boolean = false

    val isVisible: Boolean get() = currentValue != SheetValue.Hidden

    // Аддитивный факт бегущей анимации высоты (snapshot-state offset.isRunning; наблюдаемо из snapshotFlow).
    val isAnimating: Boolean get() = offset.isRunning

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
        // Лист приходит из Loading в рест-стейт. Если клавиатура уже открыта — применяем тот же авто-FullScreen,
        // что и onImeShown (иначе не-FullScreen лист поднимется withAdjustmentForKeyboard на полную высоту IME и
        // верх уедет за экран). onImeShown при currentValue=Loading промоушен не делает (Loading вне promotable).
        val target = measured.openTarget(skipCollapsed)
        if (shouldPromoteForIme(target, measured)) {
            imePromotedFrom = target
            animateTo(SheetValue.ExpandedFullScreen)
        } else {
            // Приходим в естественный рест-стейт (в т.ч. откат промоушена Loading→FullScreen, если контент влезает
            // с лифтом): сбрасываем imePromotedFrom, чтобы скрытие IME не откатывало обратно к Loading.
            imePromotedFrom = null
            animateTo(target)
        }
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

    // Промоушен в FullScreen при видимой IME: не-FullScreen лист поднимается withAdjustmentForKeyboard на ПОЛНУЮ
    // высоту клавиатуры (дамб-лифт), поэтому якорь + IME выше потолка → верх листа уехал бы за экран. В FullScreen
    // подъёма нет (Middle сжимается). alwaysFullScreenOnIme — режим StayUnderKeyboard (bottom уходит под клавиатуру).
    private fun shouldPromoteForIme(value: SheetValue, measured: SheetMetrics): Boolean {
        val ime = keyboardState?.value ?: return false
        if (!ime.isKeyboardVisible) return false
        // Loading: лист 192dp у нижней кромки; ADJUSTMENT-лифт снапнул бы его на полную высоту IME мгновенно
        // (пока клавиатура ещё анимируется) → «висение в воздухе». Сразу FullScreen — там лифта нет, а Loader
        // сжимается withKeyboardShrink покадрово (WindowInsetsAnimationCallback), синхронно с клавиатурой.
        if (value == SheetValue.Loading) return true
        val promotable = value == SheetValue.Content || value == SheetValue.Collapsed ||
            value == SheetValue.ExpandedContent || value is SheetValue.Custom
        if (!promotable) return false
        return alwaysFullScreenOnIme ||
            measured.anchorPx(value, skipCollapsed) + ime.keyboardHeight.roundToInt() > measured.maxHeightPx
    }

    internal suspend fun onImeShown() {
        val measured = metrics ?: return
        if (!isVisible) return
        if (shouldPromoteForIme(currentValue, measured)) {
            imePromotedFrom = currentValue
            animateTo(SheetValue.ExpandedFullScreen)
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
        anchorTable = updated.toAnchorTable(skipCollapsed)
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
        val table = anchorTable ?: return
        val floorPx = if (dismissOnSwipeDown) 0f else table.lowestRestAnchorPx.toFloat()
        val atTop = currentValue == SheetValue.ExpandedFullScreen
        // atTop — база maxHeight (rubber-band в статус-бар); иначе — верхний rest-якорь (overshoot тянет к maxHeight).
        val overshootBase = if (atTop) measured.maxHeightPx.toFloat() else table.highestRestAnchorPx.toFloat()
        val overshootCeiling =
            if (atTop) (measured.maxHeightPx + XBottomSheetDefaults.ResistanceMaxPx) else measured.maxHeightPx.toFloat()
        // Overshoot — когда лист у/над верхним rest-якорем (не по currentValue): включает и драг из Collapsed.
        val inOvershoot = accumulatedOvershootPx > 0f ||
            (offset.value >= overshootBase - OVERSHOOT_ENTER_EPS_PX && deltaHeightPx > 0f)
        if (inOvershoot) {
            accumulatedOvershootPx = (accumulatedOvershootPx + deltaHeightPx).coerceAtLeast(0f)
            val resisted = resistedOvershoot(accumulatedOvershootPx, XBottomSheetDefaults.ResistanceMaxPx)
            offset.snapTo((overshootBase + resisted).coerceAtMost(overshootCeiling))
        } else {
            accumulatedOvershootPx = 0f
            offset.snapTo((offset.value + deltaHeightPx).coerceIn(floorPx, overshootBase))
        }
    }

    // Settle: по скорости/ближайшему якорю из таблицы; Hidden → свайп-закрытие (если dismissOnSwipeDown).
    private suspend fun settle(velocity: Float) {
        isDragging = false
        accumulatedOvershootPx = 0f
        imePromotedFrom = null
        val table = anchorTable ?: return
        val target = table.settleTarget(offset.value, velocity, dismissOnSwipeDown)
        when {
            target == null -> animateTo(currentValue)
            target == SheetValue.Hidden -> onDismissRequest()
            else -> animateTo(target)
        }
    }

    // Лист стоит на rest-якоре? onPreFling не съедает инерцию списка, если лист уже дорос до якоря.
    internal fun isOffsetAtRestAnchor(): Boolean = anchorTable?.isAtRestAnchor(offset.value) ?: false

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
