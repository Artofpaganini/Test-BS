package com.onexui.bottomsheet

import android.view.WindowManager
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import com.onexui.bottomsheet.presets.PresetLoader
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.launch
import org.xplatform.uikit.compose.modifier.keyboard.adjustment.withAdjustmentForKeyboard
import org.xplatform.uikit.compose.modifier.keyboard.lift.KeyboardLiftState
import org.xplatform.uikit.compose.modifier.keyboard.lift.rememberKeyboardLiftState
import org.xplatform.uikit.compose.modifier.keyboard.shrink.withKeyboardShrink
import kotlin.math.roundToInt

// Fade-дистанция затемнения scrim: на первых px открытия/закрытия alpha разгоняется 0 → полный.
private val ScrimFadeDistance = 120.dp

@Composable
internal fun XBottomSheet(
    state: XBottomSheetState,
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
    overlayBackground: Boolean = true,
    dragHandle: DragHandleStyle? = DragHandleStyle.Theme,
    dismissOnOutsideTap: Boolean = true,
    dismissOnSwipeDown: Boolean = true,
    additionalTop: (@Composable () -> Unit)? = null,
    top: (@Composable () -> Unit)? = null,
    bottom: (@Composable () -> Unit)? = null,
    middle: @Composable ColumnScope.() -> Unit,
) {
    val density = LocalDensity.current
    val scope = rememberCoroutineScope()
    val keyboardController = LocalSoftwareKeyboardController.current
    val focusManager = LocalFocusManager.current
    // Скролл Middle: единственный скролл листа. Поднят сюда, чтобы жить на уровне листа (переживает
    // рекомпозиции тела), передаётся в verticalScroll внутри SheetBody.
    val middleScrollState = rememberScrollState()
    val currentOnDismiss by rememberUpdatedState(onDismissRequest)
    // Размеры экрана — из LocalWindowInfo.containerSize (px), без BoxWithConstraints. При повороте
    // containerSize обновляется сам → composition пересчитывается.
    val containerSize = LocalWindowInfo.current.containerSize
    val screenHeightPx = containerSize.height
    val screenWidthDp = with(density) { containerSize.width.toDp() }
    val statusBarPx = WindowInsets.statusBars.getTop(density)
    // Нижний inset навигации: фон листа (Surface) остаётся edge-to-edge (заливает зону под nav bar), а контент
    // паддится этим инсетом ВНУТРИ Surface (bottom-слот встаёт над баром). См. SheetBody.
    val navBarPx = WindowInsets.navigationBars.getBottom(density)
    // Loading-якорь: видимая зона Loader'а должна остаться 192dp, а контент внутри отступает на nav bar →
    // якорь = 192dp + navBarPx (иначе Loader был бы 192−navBar видимых px).
    val loadingSheetHeightPx = with(density) { XBottomSheetDefaults.LoadingSheetHeight.roundToPx() } + navBarPx
    val scrimFadeDistancePx = with(density) { ScrimFadeDistance.toPx() }
    val isWide = screenWidthDp >= XBottomSheetDefaults.WideScreenThreshold
    val widthModifier = if (isWide) Modifier.width(XBottomSheetDefaults.MaxWidth) else Modifier.fillMaxWidth()
    // Клавиатура: единый источник состояния IME (xbet KeyboardLiftState), общий для авто-FullScreen и
    // модификаторов подъёма/сжатия. Alva-копию KeyboardLiftState не трогаем — здесь xbet-пакет.
    val keyboardState = rememberKeyboardLiftState()
    val imeVisible = keyboardState.value.isKeyboardVisible
    // Взаимодействие с высотой отключено: хендл скрыт, Loading, ИЛИ открыта клавиатура (уточнение юзера §6 —
    // при видимой IME жесты высоты выключены; закрытие только тапом снаружи/кнопкой).
    val interactionsEnabled = dragHandle != null && !state.isLoading && !imeVisible
    val isFullScreen = state.currentValue == SheetValue.ExpandedFullScreen

    // Рост/уменьшение контента (подгрузка списка): высота листа следует за контентом. Измеритель (Layout)
    // при смене контента переизмеряется (relayout поднимается от списка), intrinsic обновляется → contentHeightPx
    // меняется → snapshotFlow. onContentRemeasured сам гейтит isVisible. КАЖДЫЙ пересчёт — в отдельном launch:
    // его offset.animateTo прерывается конкурирующей анимацией (show/settle) с CancellationException, и без
    // launch это отменило бы сам коллектор (он бы умер после первого прерывания). Дочерний launch изолирует
    // отмену — коллектор живёт и ловит последующие изменения контента.
    LaunchedEffect(state) {
        snapshotFlow { state.metrics?.contentHeightPx }
            .filterNotNull()
            .collect { launch { state.onContentRemeasured() } }
    }
    // Поворот/resize: высота снапится к якорю текущего стейта (без анимации).
    LaunchedEffect(state, screenHeightPx, statusBarPx) { state.snapToCurrentAnchor() }
    // Показ IME → авто-переход в ExpandedFullScreen при нехватке места (§6).
    SheetKeyboardAutoFullScreenEffect(state = state, keyboardState = keyboardState)
    // Закрытие БШ при открытой клавиатуре (§6, усиление юзера): как только лист уходит в Hidden, ПРИНУДИТЕЛЬНО
    // роняем клавиатуру — иначе keyboard-lift оффсет (withAdjustmentForKeyboard) продолжал бы поднимать
    // контейнер, и лист «улетел» бы вверх, отцепившись от нижней кромки. Дроп синхронно с началом анимации
    // закрытия → лифт уходит к 0 вместе с высотой, лист остаётся прижат к низу.
    LaunchedEffect(state) {
        snapshotFlow { state.currentValue == SheetValue.Hidden }
            .collect { hidden ->
                if (hidden) {
                    keyboardController?.hide()
                    focusManager.clearFocus(force = true)
                }
            }
    }

    val nestedScrollConnection = remember(state, scope, interactionsEnabled, dismissOnSwipeDown) {
        SheetNestedScrollConnection(
            state = state,
            scope = scope,
            interactionsEnabled = interactionsEnabled,
            dismissOnSwipeDown = dismissOnSwipeDown,
            onDismissRequest = { currentOnDismiss() },
        )
    }
    Box(modifier = Modifier.fillMaxSize().then(modifier)) {
        SheetScrim(
            state = state,
            overlayBackground = overlayBackground,
            dismissOnOutsideTap = dismissOnOutsideTap,
            scrimFadeDistancePx = scrimFadeDistancePx,
            onDismissRequest = { currentOnDismiss() },
        )

        SheetContainer(
            state = state,
            screenHeightPx = screenHeightPx,
            statusBarPx = statusBarPx,
            loadingSheetHeightPx = loadingSheetHeightPx,
            overlayBackground = overlayBackground,
            dragHandle = dragHandle,
            interactionsEnabled = interactionsEnabled,
            nestedScrollConnection = nestedScrollConnection,
            scope = scope,
            dismissOnSwipeDown = dismissOnSwipeDown,
            onDismissRequest = { currentOnDismiss() },
            keyboardState = keyboardState,
            isFullScreen = isFullScreen,
            middleScrollState = middleScrollState,
            additionalTop = additionalTop,
            top = top,
            bottom = bottom,
            middle = middle,
            modifier = Modifier.align(Alignment.BottomCenter).then(widthModifier),
        )
    }
}

// Полноэкранный scrim: блокировка тачей ВСЕГДА (scrim — hit-таргет с detectTapGestures, тапы под БШ гасятся
// и не доходят до контента позади), затемнение опционально. Тап по scrim'у (вне листа) закрывает БШ, если
// dismissOnOutsideTap. Тапы по самому листу не долетают: тело листа гасит их своим no-op detectTapGestures
// (up поглощается выше по z → waitForUpOrCancellation в scrim'е отменяется). Alpha разгоняется по offset листа.
@Composable
private fun BoxScope.SheetScrim(
    state: XBottomSheetState,
    overlayBackground: Boolean,
    dismissOnOutsideTap: Boolean,
    scrimFadeDistancePx: Float,
    onDismissRequest: () -> Unit,
) {
    Box(
        modifier = Modifier
            .matchParentSize()
            .pointerInput(dismissOnOutsideTap, state) {
                detectTapGestures {
                    if (dismissOnOutsideTap && !state.isDragging) onDismissRequest()
                }
            }
            .drawBehind {
                if (overlayBackground) {
                    val fade = (state.offset.value / scrimFadeDistancePx).coerceIn(0f, 1f)
                    drawRect(color = XBottomSheetDefaults.ScrimColor, alpha = fade)
                }
            },
    )
}

// Контейнер листа: измеритель контента через кастомный Layout с ОДНИМ ребёнком-телом (тело композируется
// РОВНО один раз, без SubcomposeLayout). soft-shadow при overlayBackground=false. Подъём над клавиатурой —
// withAdjustmentForKeyboard (вместо imePadding), включён только вне FullScreen: в FullScreen лист уже у потолка,
// подъём выгнал бы верх под статус-бар — там вместо подъёма Middle сжимается (см. SheetBody).
@Composable
private fun SheetContainer(
    state: XBottomSheetState,
    screenHeightPx: Int,
    statusBarPx: Int,
    loadingSheetHeightPx: Int,
    overlayBackground: Boolean,
    dragHandle: DragHandleStyle?,
    interactionsEnabled: Boolean,
    nestedScrollConnection: SheetNestedScrollConnection,
    scope: CoroutineScope,
    dismissOnSwipeDown: Boolean,
    onDismissRequest: () -> Unit,
    keyboardState: State<KeyboardLiftState>,
    isFullScreen: Boolean,
    middleScrollState: ScrollState,
    additionalTop: (@Composable () -> Unit)?,
    top: (@Composable () -> Unit)?,
    bottom: (@Composable () -> Unit)?,
    middle: @Composable ColumnScope.() -> Unit,
    modifier: Modifier = Modifier,
) {
    val ceilingPx = (screenHeightPx - statusBarPx).coerceAtLeast(0)
    val shadowModifier = if (!overlayBackground) Modifier.softSheetShadow(XBottomSheetDefaults.Shape) else Modifier
    val keyboardAdjustmentModifier = if (!isFullScreen) {
        Modifier.withAdjustmentForKeyboard(keyboardState = keyboardState)
    } else {
        Modifier
    }
    Layout(
        content = {
            SheetBody(
                state = state,
                dragHandle = dragHandle,
                keyboardState = keyboardState,
                isFullScreen = isFullScreen,
                middleScrollState = middleScrollState,
                additionalTop = additionalTop,
                top = top,
                bottom = bottom,
                middle = middle,
            )
        },
        modifier = modifier
            .then(shadowModifier)
            .then(keyboardAdjustmentModifier)
            .nestedScroll(nestedScrollConnection)
            .sheetDrag(
                state = state,
                enabled = interactionsEnabled,
                scope = scope,
                dismissOnSwipeDown = dismissOnSwipeDown,
                onDismissRequest = onDismissRequest,
            ),
    ) { measurables, constraints ->
        val body = measurables.first()
        val width = constraints.maxWidth
        // Плейсмент: тело кладём высотой offset (текущая высота листа), но не выше потолка. Reflow top/middle/
        // bottom корректный (sticky-низ у нижнего края offset), клип не нужен.
        val placeHeight = state.offset.value.roundToInt().coerceIn(0, ceilingPx)
        // Натуральная высота контента — intrinsic (unclamped): точное M+S синхронно (в т.ч. на Hidden). При
        // росте контента relayout поднимается от списка → Layout переизмеряется → intrinsic обновляется →
        // contentHeightPx меняется → onContentRemeasured тянет высоту.
        val naturalHeight = body.maxIntrinsicHeight(width)
        val placeable = body.measure(Constraints.fixed(width = width, height = placeHeight))
        layout(width = width, height = placeHeight) {
            // Репорт в state с guard (updateMetrics не пишет одинаковое) — не грязним snapshot каждый пасс.
            state.updateMetrics(
                screenHeightPx = screenHeightPx,
                statusBarPx = statusBarPx,
                contentHeightPx = naturalHeight,
                loadingSheetHeightPx = loadingSheetHeightPx,
            )
            placeable.place(x = 0, y = 0)
        }
    }
}

// Тело листа: Surface(20,20,0,0) с top(sticky) / middle(scroll) / bottom(sticky). Основная часть получает
// фиксированную высоту (от измерителя / AdditionalTopStack), поэтому weight у middle работает без внешнего
// Column. DragHandle рисуется поверх (TopCenter) и вёрстку не двигает. AdditionalTop — стики-слой с
// перекрытием. В FullScreen Middle сжимается под клавиатуру (withKeyboardShrink) — подъёма там нет.
@Composable
private fun SheetBody(
    state: XBottomSheetState,
    dragHandle: DragHandleStyle?,
    keyboardState: State<KeyboardLiftState>,
    isFullScreen: Boolean,
    middleScrollState: ScrollState,
    additionalTop: (@Composable () -> Unit)?,
    top: (@Composable () -> Unit)?,
    bottom: (@Composable () -> Unit)?,
    middle: @Composable ColumnScope.() -> Unit,
) {
    val middleShrinkModifier = if (isFullScreen) {
        Modifier.withKeyboardShrink(
            softInputMode = WindowManager.LayoutParams.SOFT_INPUT_ADJUST_NOTHING,
            keyboardState = keyboardState,
        )
    } else {
        Modifier
    }
    val sheetSurface: @Composable () -> Unit = {
        Surface(
            modifier = Modifier.fillMaxSize(),
            shape = XBottomSheetDefaults.Shape,
            color = XBottomSheetDefaults.SheetBackground,
        ) {
            // Surface — edge-to-edge (фон заливает зону под nav bar). Инсет навигации накладываем на КОНТЕНТ
            // внутри Surface: bottom-слот встаёт над баром, фон продолжается под ним. Паддинг внутри
            // измеряемого тела → intrinsic-высота включает inset автоматически (contentHeightPx корректна).
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .windowInsetsPadding(WindowInsets.navigationBars),
            ) {
                top?.invoke()
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .then(middleShrinkModifier)
                        .verticalScroll(middleScrollState),
                ) {
                    if (state.isLoading) {
                        PresetLoader()
                    } else {
                        middle()
                    }
                }
                bottom?.invoke()
            }
        }
    }
    Box(
        modifier = Modifier
            .fillMaxSize()
            // No-op гаситель тапов по телу листа: consume up → тап не проваливается на scrim (сосед снизу по z)
            // и не закрывает лист. detectTapGestures пропускает drag'и (отменяется по slop) — драг листа/скролл
            // списка работают штатно.
            .pointerInput(Unit) { detectTapGestures {} },
    ) {
        if (additionalTop != null) {
            AdditionalTopStack(
                additionalTopState = state.additionalTopState,
                card = additionalTop,
                surface = sheetSurface,
            )
        } else {
            sheetSurface()
        }
        if (dragHandle != null) {
            DragHandle(style = dragHandle, modifier = Modifier.align(Alignment.TopCenter))
        }
    }
}
