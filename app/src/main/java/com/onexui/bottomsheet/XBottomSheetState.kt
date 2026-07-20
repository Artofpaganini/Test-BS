package com.onexui.bottomsheet

import androidx.compose.animation.core.Animatable
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.math.abs

// Стейт-машина высоты листа. Compose-state (mutableStateOf), НЕ StateFlow — правило _state.update{}
// здесь не применяется. Состояние ВЫЧИСЛЯЕТСЯ по метрикам и фактам, публичное API управляет фактами.
@Stable
internal class XBottomSheetState internal constructor(
    val skipCollapsed: Boolean,
    initialLoading: Boolean,
) {
    var currentValue by mutableStateOf(SheetValue.Hidden)
        private set

    // Loading = Content + SkipCollapsed, Middle = Loader. Управляется извне (isLoading / contentReady()).
    var isLoading by mutableStateOf(initialLoading)
        internal set

    // Additional Top живёт на стейте (§7): переключается внешними факторами (кнопка/логика экрана), не жестами.
    var additionalTopState by mutableStateOf(AdditionalTopState.Expanded)

    internal var metrics by mutableStateOf<SheetMetrics?>(null)
        private set

    // Текущая высота листа в px. Единственный писатель — suspend-методы стейта (drag = snapTo,
    // анимация = animateTo). Читается в placement-лямбде измерителя (deferred), без рекомпозиций.
    internal val offset = Animatable(0f)

    // Флаг активного драга: nested-scroll/жесты выставляют его, settle снимает.
    internal var isDragging by mutableStateOf(false)

    // Аккумулированный СЫРОЙ overshoot над якорем во время драга (px). Сопротивление считается от суммы:
    // offset = anchor + resistedOvershoot(rawOvershootPx). Сброс на settle — иначе resist(resist(prev)+delta).
    private var rawOvershootPx = 0f

    // Стейт, ЗАПОМНЕННЫЙ перед авто-промоушеном в FullScreen из-за клавиатуры (§6, уточнение юзера): при
    // скрытии IME откатываемся к нему. Ручное взаимодействие с высотой (expand/settle) сбрасывает флаг —
    // ручной разворот не откатывается. null = авто-промоушена не было.
    private var imePromotedFrom: SheetValue? = null

    val isVisible: Boolean get() = currentValue != SheetValue.Hidden

    // Пока метрики не измерены (первый кадр), suspend-операции ждут первого замера — иначе show()
    // из LaunchedEffect(Unit) отработал бы вхолостую до готовности контента.
    private suspend fun awaitMetrics(): SheetMetrics =
        metrics ?: snapshotFlow { metrics }.filterNotNull().first()

    suspend fun show() {
        val measured = awaitMetrics()
        animateTo(if (isLoading) SheetValue.Loading else measured.openTarget(skipCollapsed))
    }

    suspend fun expand() {
        val measured = metrics ?: return
        imePromotedFrom = null // ручной разворот не откатывается при скрытии IME
        if (currentValue == SheetValue.Collapsed) animateTo(measured.expandTarget())
    }

    suspend fun hide() {
        animateTo(SheetValue.Hidden)
    }

    // Данные загрузились: снять Loading и анимировать высоту к целевому стейту. Во время Loading middle не
    // композируется (там Loader), поэтому contentHeightPx = высота Loader'а. Нужно ДОЖДАТЬСЯ ре-замера
    // реального контента (метрики станут новым инстансом), и лишь потом считать openTarget — иначе цель
    // высчитается по устаревшей высоте Loader'а. Если высота совпала (нового инстанса не будет) — таймаут
    // и текущие метрики уже корректны.
    suspend fun contentReady() {
        val loaderMetrics = metrics
        isLoading = false
        val measured = withTimeoutOrNull(REMEASURE_TIMEOUT_MS) {
            snapshotFlow { metrics }.filterNotNull().first { remeasured -> remeasured !== loaderMetrics }
        } ?: awaitMetrics()
        animateTo(measured.openTarget(skipCollapsed))
    }

    // Контент вырос/уменьшился (подгрузка страницы списка): высота следует за контентом. skipCollapsed +
    // Content + контент выше экрана → авто FullScreen.
    internal suspend fun onContentRemeasured() {
        val measured = metrics ?: return
        if (!isVisible) return
        // Во время Loading высота фиксирована (Loader 192dp), а показ/готовность контента ведут show()/
        // contentReady(). Если бы onContentRemeasured тут дёргал offset.animateTo (при замере Loader'а или
        // при появлении реального контента до contentReady's animateTo), он ОТМЕНИЛ бы show()/contentReady
        // CancellationException'ом (мьютекс Animatable) → лист завис бы в Loading. Поэтому в Loading — no-op.
        if (currentValue == SheetValue.Loading) return
        if (skipCollapsed && currentValue == SheetValue.Content &&
            measured.contentHeightPx > measured.maxHeightPx
        ) {
            animateTo(SheetValue.ExpandedFullScreen)
        } else {
            offset.animateTo(measured.anchorPx(currentValue, skipCollapsed).toFloat(), NativeSheetSpring)
        }
    }

    // Показ клавиатуры (§6): в Content/Collapsed/ExpandedContent, если места на подъём не хватает —
    // авто-переход в ExpandedFullScreen; иначе стейт не меняется (лист поднимается модификатором
    // withAdjustmentForKeyboard). В FullScreen/Loading/Hidden стейт неизменен.
    internal suspend fun onImeShown(imeHeightPx: Int, alwaysFullScreen: Boolean) {
        val measured = metrics ?: return
        if (!isVisible) return
        when (currentValue) {
            SheetValue.Content, SheetValue.Collapsed, SheetValue.ExpandedContent -> {
                val liftedTop = measured.anchorPx(currentValue, skipCollapsed) + imeHeightPx
                // alwaysFullScreen (режим StayUnderKeyboard): разворачиваем на весь экран при любом показе IME —
                // bottom должен доставать до нижней кромки экрана, под клавиатуру.
                if (alwaysFullScreen || liftedTop > measured.maxHeightPx) {
                    imePromotedFrom = currentValue // запомним стейт до промоушена для отката при скрытии IME
                    animateTo(SheetValue.ExpandedFullScreen)
                }
            }
            else -> Unit
        }
    }

    // Скрытие клавиатуры (уточнение юзера): если в FullScreen попали АВТОМАТИЧЕСКИ из-за IME —
    // откатываемся к запомненному стейту и сбрасываем флаг. Ручной разворот (imePromotedFrom == null)
    // не откатывается. Если стейт уже сменился другим способом (не FullScreen) — просто сбрасываем флаг.
    internal suspend fun onImeHidden() {
        val target = imePromotedFrom ?: return
        imePromotedFrom = null
        if (currentValue == SheetValue.ExpandedFullScreen) animateTo(target)
    }

    // Смена размеров экрана (поворот/resize): высота снапится к якорю текущего стейта, без анимации. ВАЖНО:
    // не снапим, пока идёт анимация (offset.isRunning) — иначе транзиент containerSize на старте (screenHeightPx
    // ещё «устаканивается») снапнул бы offset и ОТМЕНИЛ бы show()/contentReady/settle (мьютекс Animatable),
    // сорвав их последовательность. Снап только когда лист покоится (реальный поворот в устоявшемся стейте).
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
        )
        if (updated != metrics) metrics = updated
    }

    // Живой драг за хендл/контент: двигаем высоту, с сопротивлением на overshoot в Content/ExpandedContent.
    // Overshoot аккумулируется СЫРЫМ (rawOvershootPx), сопротивление применяется к сумме — не итеративно.
    internal suspend fun dragBy(deltaHeightPx: Float, dismissOnSwipeDown: Boolean) {
        val measured = metrics ?: return
        val ceiling = measured.maxHeightPx.toFloat()
        val anchor = measured.anchorPx(currentValue, skipCollapsed).toFloat()
        // Нижняя граница драга. При запрещённом свайп-закрытии лист нельзя утянуть ниже самого нижнего
        // разрешённого якоря — иначе палец уводит его к 0 (лист визуально «закрывается»), хотя единственная
        // цель ниже — Hidden, а она отключена. При разрешённом закрытии граница = 0 (можно дотянуть до dismiss).
        val floorPx = if (dismissOnSwipeDown) 0f else lowestRestAnchorPx(measured)
        val resistanceState = currentValue == SheetValue.Content || currentValue == SheetValue.ExpandedContent
        val inOvershoot = resistanceState &&
            (rawOvershootPx > 0f || (offset.value >= anchor - 0.5f && deltaHeightPx > 0f))
        if (inOvershoot) {
            rawOvershootPx = (rawOvershootPx + deltaHeightPx).coerceAtLeast(0f)
            val resisted = resistedOvershoot(rawOvershootPx, RESISTANCE_MAX_PX)
            offset.snapTo((anchor + resisted).coerceAtMost(ceiling))
        } else {
            rawOvershootPx = 0f
            offset.snapTo((offset.value + deltaHeightPx).coerceIn(floorPx, ceiling))
        }
    }

    // Самый нижний разрешённый якорь покоя (без Hidden). Ниже него драг не пускаем при dismissOnSwipeDown=false.
    private fun lowestRestAnchorPx(measured: SheetMetrics): Float =
        settleCandidates(measured, dismissOnSwipeDown = false).firstOrNull()?.second?.toFloat() ?: 0f

    // Settle после отпускания (§5): по скорости и ближайшему якорю. Собираем отсортированные якоря доступных
    // стейтов; при скорости — ближайший якорь ПО НАПРАВЛЕНИЮ, при малой скорости — ближайший по расстоянию
    // (граница между соседями = midpoint). Hidden в наборе = свайп-закрытие (если dismissOnSwipeDown).
    internal suspend fun settle(
        velocity: Float,
        dismissOnSwipeDown: Boolean,
        onDismissRequest: () -> Unit,
    ) {
        isDragging = false
        rawOvershootPx = 0f
        imePromotedFrom = null // ручное взаимодействие с высотой отменяет авто-откат при скрытии IME
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

    // Отсортированные по высоте якоря доступных стейтов для settle. Rest-стейты зависят от skipCollapsed и
    // высоты контента; Hidden добавляется только если разрешено закрытие свайпом.
    private fun settleCandidates(
        measured: SheetMetrics,
        dismissOnSwipeDown: Boolean,
    ): List<Pair<SheetValue, Int>> {
        val list = mutableListOf<Pair<SheetValue, Int>>()
        when {
            skipCollapsed -> {
                list.add(SheetValue.Content to measured.anchorPx(SheetValue.Content, skipCollapsed = true))
                if (measured.contentHeightPx > measured.maxHeightPx) {
                    list.add(SheetValue.ExpandedFullScreen to measured.maxHeightPx)
                }
            }
            measured.contentHeightPx <= measured.collapsedPx ->
                list.add(SheetValue.Content to measured.anchorPx(SheetValue.Content, skipCollapsed = false))
            else -> {
                list.add(SheetValue.Collapsed to measured.collapsedPx)
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

@Composable
internal fun rememberXBottomSheetState(
    skipCollapsed: Boolean = false,
    initialLoading: Boolean = false,
): XBottomSheetState = remember(skipCollapsed, initialLoading) {
    XBottomSheetState(skipCollapsed = skipCollapsed, initialLoading = initialLoading)
}
