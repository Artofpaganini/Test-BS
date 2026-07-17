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
import kotlin.math.roundToInt

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

    internal var metrics by mutableStateOf<SheetMetrics?>(null)
        private set

    // Текущая высота листа в px. Единственный писатель — suspend-методы стейта (drag = snapTo,
    // анимация = animateTo). Читается в placement-лямбде измерителя (deferred), без рекомпозиций.
    internal val offset = Animatable(0f)

    // Флаг активного драга: nested-scroll/жесты выставляют его, settle снимает.
    internal var isDragging by mutableStateOf(false)

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

    // Данные загрузились: снять Loading и анимировать высоту к целевому стейту.
    suspend fun contentReady() {
        isLoading = false
        val measured = awaitMetrics()
        animateTo(measured.openTarget(skipCollapsed))
    }

    // Контент вырос/уменьшился (подгрузка страницы списка): высота следует за контентом. skipCollapsed +
    // Content + контент выше экрана → авто FullScreen.
    internal suspend fun onContentRemeasured() {
        val measured = metrics ?: return
        if (!isVisible) return
        android.util.Log.d("XBS", "onContentRemeasured content=${measured.contentHeightPx} maxH=${measured.maxHeightPx} cur=$currentValue skip=$skipCollapsed")
        if (skipCollapsed && currentValue == SheetValue.Content &&
            measured.contentHeightPx > measured.maxHeightPx
        ) {
            animateTo(SheetValue.ExpandedFullScreen)
        } else {
            offset.animateTo(measured.anchorPx(currentValue, skipCollapsed).toFloat(), NativeSheetSpring)
        }
    }

    // Рост/уменьшение контента Middle, пойманный по scrollState.maxValue (измеритель тело не переизмеряет).
    // Натуральная высота = offset + overflow (== M+S при offset ≥ sticky). Обновляем contentHeightPx в метриках
    // → snapshotFlow в XBottomSheet поднимает onContentRemeasured. Скролл списка maxValue не меняет (только
    // при смене размера контента/viewport), поэтому спама во время скролла нет.
    internal fun onMiddleScrollRangeChanged(scrollOverflowPx: Int) {
        val measured = metrics ?: return
        if (!isVisible) return
        val estimated = offset.value.roundToInt() + scrollOverflowPx
        android.util.Log.d("XBS", "onMiddleScrollRangeChanged overflow=$scrollOverflowPx offset=${offset.value.roundToInt()} estimated=$estimated old=${measured.contentHeightPx} maxH=${measured.maxHeightPx} cur=$currentValue")
        if (estimated != measured.contentHeightPx) {
            metrics = measured.copy(contentHeightPx = estimated)
        }
    }

    // Показ клавиатуры (§6): в Content/Collapsed/ExpandedContent, если места на подъём не хватает —
    // авто-переход в ExpandedFullScreen; иначе стейт не меняется (лист поднимается модификатором
    // withAdjustmentForKeyboard). В FullScreen/Loading/Hidden стейт неизменен.
    internal suspend fun onImeShown(imeHeightPx: Int) {
        val measured = metrics ?: return
        if (!isVisible) return
        when (currentValue) {
            SheetValue.Content, SheetValue.Collapsed, SheetValue.ExpandedContent -> {
                val liftedTop = measured.anchorPx(currentValue, skipCollapsed) + imeHeightPx
                if (liftedTop > measured.maxHeightPx) {
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

    // Смена размеров экрана (поворот/resize): высота снапится к якорю текущего стейта, без анимации.
    internal suspend fun snapToCurrentAnchor() {
        val measured = metrics ?: return
        if (!isVisible) return
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
    internal suspend fun dragBy(deltaHeightPx: Float) {
        val measured = metrics ?: return
        val raw = offset.value + deltaHeightPx
        offset.snapTo(resolveDragTarget(measured, raw))
    }

    private fun resolveDragTarget(measured: SheetMetrics, rawHeightPx: Float): Float {
        val ceiling = measured.maxHeightPx.toFloat()
        val anchor = measured.anchorPx(currentValue, skipCollapsed).toFloat()
        val resistanceState = currentValue == SheetValue.Content || currentValue == SheetValue.ExpandedContent
        return when {
            resistanceState && rawHeightPx > anchor ->
                (anchor + resistedOvershoot(rawHeightPx - anchor, RESISTANCE_MAX_PX)).coerceAtMost(ceiling)
            else -> rawHeightPx.coerceIn(0f, ceiling)
        }
    }

    // Settle после отпускания: по скорости и ближайшему якорю (§5). Возвращает управление,
    // когда анимация к якорю завершена или запрошено закрытие.
    internal suspend fun settle(
        velocity: Float,
        dismissOnSwipeDown: Boolean,
        onDismissRequest: () -> Unit,
    ) {
        isDragging = false
        imePromotedFrom = null // ручное взаимодействие с высотой отменяет авто-откат при скрытии IME
        val measured = metrics ?: return
        when {
            velocity < -FLING_VELOCITY_THRESHOLD -> settleUp(measured)
            velocity > FLING_VELOCITY_THRESHOLD -> settleDown(dismissOnSwipeDown, onDismissRequest)
            else -> settleByPosition(measured, dismissOnSwipeDown, onDismissRequest)
        }
    }

    private suspend fun settleUp(measured: SheetMetrics) {
        if (currentValue == SheetValue.Collapsed) animateTo(measured.expandTarget()) else animateTo(currentValue)
    }

    private suspend fun settleDown(dismissOnSwipeDown: Boolean, onDismissRequest: () -> Unit) {
        val step = stepDownTarget()
        when {
            step != null -> animateTo(step)
            dismissOnSwipeDown -> onDismissRequest()
            else -> animateTo(currentValue)
        }
    }

    private suspend fun settleByPosition(
        measured: SheetMetrics,
        dismissOnSwipeDown: Boolean,
        onDismissRequest: () -> Unit,
    ) {
        val anchor = measured.anchorPx(currentValue, skipCollapsed).toFloat()
        when {
            offset.value < anchor * SETTLE_DISMISS_FRACTION -> settleDown(dismissOnSwipeDown, onDismissRequest)
            currentValue == SheetValue.Collapsed && offset.value > measured.collapsedPx * SETTLE_EXPAND_FRACTION ->
                animateTo(measured.expandTarget())
            else -> animateTo(currentValue)
        }
    }

    // Один шаг вниз по стейт-машине: Expanded* → Collapsed (или Content при skipCollapsed);
    // Content/Collapsed → null (означает закрытие).
    private fun stepDownTarget(): SheetValue? = when (currentValue) {
        SheetValue.ExpandedContent, SheetValue.ExpandedFullScreen ->
            if (skipCollapsed) SheetValue.Content else SheetValue.Collapsed
        else -> null
    }

    private suspend fun animateTo(target: SheetValue) {
        currentValue = target
        val anchor = metrics?.anchorPx(target, skipCollapsed)?.toFloat() ?: 0f
        offset.animateTo(anchor, NativeSheetSpring)
    }

    private companion object {
        const val FLING_VELOCITY_THRESHOLD = 400f
        const val SETTLE_DISMISS_FRACTION = 0.5f
        const val SETTLE_EXPAND_FRACTION = 1.15f
        const val RESISTANCE_MAX_PX = 240f
    }
}

@Composable
internal fun rememberXBottomSheetState(
    skipCollapsed: Boolean = false,
    initialLoading: Boolean = false,
): XBottomSheetState = remember(skipCollapsed, initialLoading) {
    XBottomSheetState(skipCollapsed = skipCollapsed, initialLoading = initialLoading)
}
