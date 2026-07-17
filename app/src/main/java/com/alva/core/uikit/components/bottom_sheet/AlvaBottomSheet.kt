package com.alva.core.uikit.components.bottom_sheet

import androidx.compose.foundation.gestures.FlingBehavior
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.anchoredDraggable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.focus.FocusManager
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.onLayoutRectChanged
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.SoftwareKeyboardController
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.navigationevent.NavigationEventInfo
import androidx.navigationevent.compose.NavigationBackHandler
import androidx.navigationevent.compose.rememberNavigationEventState
import com.alva.core.device.model.SystemBarsInsets
import com.alva.core.device.model.WindowSizeClass
import com.alva.core.device.rememberDeviceStateStub
import com.alva.core.uikit.components.bottom_sheet.config.AlvaBottomSheetConfig
import com.alva.core.uikit.components.bottom_sheet.config.rememberAlvaBottomSheetConfig
import com.alva.core.uikit.components.bottom_sheet.drag.AlvaDragHandle
import com.alva.core.uikit.components.bottom_sheet.fling.rememberAlvaBottomSheetFlingBehavior
import com.alva.core.uikit.components.bottom_sheet.nested_scroll.BottomSheetNestedScrollConnection
import com.alva.core.uikit.modifier.keyboard.rememberKeyboardLiftState
import com.alva.core.uikit.utils.noRippleEffectClick
import kotlin.math.roundToInt
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first

@Composable
fun AlvaBottomSheet(
    state: AlvaBottomSheetStateHolder,
    onDismissRequest: (reason: DismissReason) -> Unit,
    modifier: Modifier = Modifier,
    config: AlvaBottomSheetConfig = rememberAlvaBottomSheetConfig(),
    flingBehavior: FlingBehavior = rememberAlvaBottomSheetFlingBehavior(state, config),
    dragHandle: (@Composable () -> Unit)? = { AlvaDragHandle() },
    content: @Composable () -> Unit,
) {
    // Порт для лаборатории z-order: в реальном Alva deviceState приходит из koinInject<DeviceStateProvider>()
    // .collectAsState(); здесь заменено на compose-заглушку rememberDeviceStateStub() (см. core/device/DeviceStateStub).
    val deviceState = rememberDeviceStateStub()
    val systemBarsInsets = deviceState.systemBarsInsets
    val adaptiveWidthModifier = rememberAdaptiveWidthModifier(
        widthSizeClass = deviceState.widthSizeClass,
        screenWidthDp = deviceState.screenWidthDp,
        adaptiveWidthFraction = config.adaptiveWidthFraction,
    )
    val horizontalSafeAreaPadding = rememberHorizontalSafeAreaPadding(systemBarsInsets)
    val keyboardLiftState = rememberKeyboardLiftState()
    val sheetKeyboardController = remember { mutableStateOf<SoftwareKeyboardController?>(null) }
    val sheetFocusManager = remember { mutableStateOf<FocusManager?>(null) }
    val nestedScrollConnection = remember(state, flingBehavior) {
        BottomSheetNestedScrollConnection(
            draggableState = state.draggableState,
            flingBehavior = flingBehavior,
        )
    }
    val currentOnDismissRequest by rememberUpdatedState(onDismissRequest)
    // Эффективный scrim: глобальный override из настроек (LocalAlvaBottomSheetScrimOverride) бьёт per-route
    // config.scrim, если задан. На его основе вычисляем модальность.
    val effectiveScrim = LocalAlvaBottomSheetScrimOverride.current ?: config.scrim
    // Modal (scrim Enabled): перехватываем back и тапы вне листа — закрываем. Non-modal (scrim Disabled):
    // back не перехватываем, полноэкранного перехватчика тапов нет — пустая область пропускает тапы на фон.
    val isModal = effectiveScrim is AlvaBottomSheetScrim.Enabled
    LaunchedEffect(state) {
        snapshotFlow { state.hasMeasuredHeight }.first { isMeasured -> isMeasured }
        state.showOnAppear()
    }
    LaunchedEffect(state) {
        snapshotFlow { state.settledValue }.first { value -> value != AlvaBottomSheetValue.Hidden }
        state.markSettledVisible()
    }
    LaunchedEffect(state) {
        snapshotFlow { state.pendingDismiss }
            .filterNotNull()
            .collect { state.animateToHidden() }
    }
    LaunchedEffect(state) {
        snapshotFlow { state.isSettledAtHidden }.first { isSettled -> isSettled }
        currentOnDismissRequest(state.pendingDismiss ?: DismissReason.UserSwipedDown)
    }
    // Единый обработчик back: при открытой клавиатуре прячем её, иначе — запрос на закрытие через
    // confirm-dismiss гейт (state.requestDismiss). Контроллеры клавиатуры/фокуса берём из ядра через
    // SideEffect-мост, т.к. обработчик объявлен выше по дереву, чем контент листа.
    val onBackDismiss: () -> Unit = {
        if (keyboardLiftState.value.isKeyboardVisible) {
            sheetKeyboardController.value?.hide()
            sheetFocusManager.value?.clearFocus()
        } else {
            state.requestDismiss(DismissReason.UserPressedBack)
        }
    }
    // Modal заполняет весь экран (scrim + лист снизу) → выравнивание TopStart, лист внутри ядра кладётся
    // BottomCenter. Non-modal оборачивает только лист → выравниваем по BottomCenter. Значение идёт в
    // contentAlignment Box-обёртки.
    val sheetAlignment = if (isModal) Alignment.TopStart else Alignment.BottomCenter
    // Лист рендерится in-tree (без Popup): фон рисует OverlayScene сзади. Back перехватываем своим
    // NavigationBackHandler (тот же navigationevent-диспетчер, что и у самого NavDisplay) — только в
    // modal через isBackEnabled; в non-modal (isBackEnabled=false) back уходит в NavDisplay.
    // currentInfo = None: своей back-истории лист не ведёт, нужен лишь перехват завершённого back-жеста.
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = sheetAlignment,
    ) {
        val navigationEventState = rememberNavigationEventState(currentInfo = NavigationEventInfo.None)
        NavigationBackHandler(
            state = navigationEventState,
            isBackEnabled = isModal,
            onBackCompleted = onBackDismiss,
        )
        AlvaBottomSheetCore(
            state = state,
            config = config,
            scrim = effectiveScrim,
            flingBehavior = flingBehavior,
            nestedScrollConnection = nestedScrollConnection,
            horizontalSafeAreaPadding = horizontalSafeAreaPadding,
            adaptiveWidthModifier = adaptiveWidthModifier,
            systemBarsInsets = systemBarsInsets,
            sheetKeyboardController = sheetKeyboardController,
            sheetFocusManager = sheetFocusManager,
            dragHandle = dragHandle,
            modifier = modifier,
            content = content,
        )
    }
}

// Общее ядро bottom sheet. Содержит мост контроллеров клавиатуры/фокуса (SideEffect),
// предоставление StateHolder через CompositionLocal и modal/non-modal разметку (scrim-слой + лист, inset/IME).
@Composable
private fun AlvaBottomSheetCore(
    state: AlvaBottomSheetStateHolder,
    config: AlvaBottomSheetConfig,
    scrim: AlvaBottomSheetScrim,
    flingBehavior: FlingBehavior,
    nestedScrollConnection: BottomSheetNestedScrollConnection,
    horizontalSafeAreaPadding: PaddingValues,
    adaptiveWidthModifier: Modifier,
    systemBarsInsets: SystemBarsInsets,
    sheetKeyboardController: MutableState<SoftwareKeyboardController?>,
    sheetFocusManager: MutableState<FocusManager?>,
    dragHandle: (@Composable () -> Unit)?,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    val innerKeyboardController = LocalSoftwareKeyboardController.current
    val innerFocusManager = LocalFocusManager.current
    SideEffect {
        sheetKeyboardController.value = innerKeyboardController
        sheetFocusManager.value = innerFocusManager
    }
    val isModal = scrim is AlvaBottomSheetScrim.Enabled
    CompositionLocalProvider(LocalAlvaBottomSheetStateHolder provides state) {
        when (isModal) {
            true -> BoxWithConstraints(modifier = modifier.fillMaxSize()) {
                val sheetHeightModifier = rememberSheetHeightModifier(
                    availableHeight = maxHeight,
                    safeTop = systemBarsInsets.safeTop,
                )
                AlvaBottomSheetScrimLayer(
                    state = state,
                    scrim = scrim,
                    dismissOnScrimTap = config.dismissOnScrimTap,
                )
                AlvaBottomSheetSurface(
                    state = state,
                    config = config,
                    flingBehavior = flingBehavior,
                    nestedScrollConnection = nestedScrollConnection,
                    horizontalSafeAreaPadding = horizontalSafeAreaPadding,
                    adaptiveWidthModifier = adaptiveWidthModifier,
                    sheetHeightModifier = sheetHeightModifier,
                    dragHandle = dragHandle,
                    modifier = Modifier.align(Alignment.BottomCenter),
                    content = content,
                )
            }
            false -> BoxWithConstraints(modifier = modifier) {
                val sheetHeightModifier = rememberSheetHeightModifier(
                    availableHeight = maxHeight,
                    safeTop = systemBarsInsets.safeTop,
                )
                AlvaBottomSheetSurface(
                    state = state,
                    config = config,
                    flingBehavior = flingBehavior,
                    nestedScrollConnection = nestedScrollConnection,
                    horizontalSafeAreaPadding = horizontalSafeAreaPadding,
                    adaptiveWidthModifier = adaptiveWidthModifier,
                    sheetHeightModifier = sheetHeightModifier,
                    dragHandle = dragHandle,
                    modifier = Modifier,
                    content = content,
                )
            }
        }
    }
}

// Полноэкранный scrim-слой (только modal-режим): рисует затемнение по expandProgress
// и закрывает лист по тапу. alpha читается внутри drawBehind (deferred read) — без
// лишних рекомпозиций, только перерисовка кадра.
@Composable
private fun AlvaBottomSheetScrimLayer(
    state: AlvaBottomSheetStateHolder,
    scrim: AlvaBottomSheetScrim,
    dismissOnScrimTap: Boolean,
    modifier: Modifier = Modifier,
) {
    val baseScrimColor = remember(scrim) {
        when (scrim) {
            is AlvaBottomSheetScrim.Enabled -> scrim.color
            AlvaBottomSheetScrim.Disabled -> Color.Transparent
        }
    }
    Box(
        modifier = modifier
            // Тап по scrim (вне листа) закрывает лист. Открывающий тап сюда не проваливается:
            // его pointer-down принадлежит элементу под scrim (навигация уже его поглотила), а
            // clickable scrim требует собственный down внутри себя.
            // Во время анимации inner Box сдвинут offset'ом вниз: тап в области хэндла
            // при settled=Expanded промахивается мимо inner Box и попадает сюда. Проверяем
            // isAnimationRunning, чтобы не интерпретировать такой нечаянный тап как dismiss.
            .noRippleEffectClick(isEnable = dismissOnScrimTap) {
                if (!state.draggableState.isAnimationRunning) {
                    state.requestDismiss(DismissReason.UserTappedScrim)
                }
            }
            .fillMaxSize()
            .drawBehind {
                val alpha = when (scrim) {
                    is AlvaBottomSheetScrim.Enabled -> state.expandProgress
                    AlvaBottomSheetScrim.Disabled -> 0f
                }
                drawRect(color = baseScrimColor, alpha = alpha)
            },
    )
}

// Заэкранный layout-offset листа, пока не построены анкеры (draggableState.offset = NaN до первого
// измерения высоты). Гарантированно ниже любой реальной высоты экрана — лист не виден до готовности.
private const val OFFSCREEN_BEFORE_MEASURE_PX = 100_000

// Сам лист: внешний позиционирующий Box (safe-area + адаптивная ширина + cap высоты) +
// перетаскиваемый контейнер, чью позицию задаёт LAYOUT-offset из draggableState (читается в
// placement-лямбде, deferred) — draw/layout/hit двигаются вместе, анимация без рекомпозиций.
@Composable
private fun AlvaBottomSheetSurface(
    state: AlvaBottomSheetStateHolder,
    config: AlvaBottomSheetConfig,
    flingBehavior: FlingBehavior,
    nestedScrollConnection: BottomSheetNestedScrollConnection,
    horizontalSafeAreaPadding: PaddingValues,
    adaptiveWidthModifier: Modifier,
    sheetHeightModifier: Modifier,
    dragHandle: (@Composable () -> Unit)?,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    Box(
        modifier = modifier
            .padding(horizontalSafeAreaPadding)
            .then(adaptiveWidthModifier)
            .then(sheetHeightModifier)
            // Внешний Box своего pointer-input НЕ несёт: тап по «тёмной» области над видимым листом
            // проходит сквозь него на scrim-сосед снизу и корректно закрывает лист. Высоту меряем здесь —
            // Box не сдвигается offset'ом, поэтому измерение стабильно (лист внутри двигается layout-
            // offset'ом, не меняя своего размера).
            .onLayoutRectChanged(throttleMillis = 0, debounceMillis = 0) { bounds ->
                state.updateMaxHeight(bounds.boundsInRoot.height.toFloat())
            },
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .nestedScroll(nestedScrollConnection)
                // Позиция листа — LAYOUT-offset (не draw-only graphicsLayer): отрисовка, layout и hit-test
                // двигаются вместе, поэтому видимый лист совпадает с областью, ловящей тап. offset читается
                // в placement-лямбде (deferred) — анимация появления/перетаскивания без рекомпозиций.
                .offset {
                    // Пока анкеры не построены (первое измерение высоты ещё не прошло), draggableState.offset
                    // = NaN. РАНЬШЕ в этом кадре лист ставился на y=0 (раскрыт сверху) → мелькание: кадр
                    // раскрытого листа, затем снап в Hidden и appear заново («мгновенное + плавное», через раз).
                    // Держим лист ЗА экраном снизу до готовности анкеров — первый видимый кадр уже штатный Hidden.
                    val sheetOffset = state.draggableState.offset
                    val y = if (sheetOffset.isNaN()) OFFSCREEN_BEFORE_MEASURE_PX else sheetOffset.roundToInt()
                    IntOffset(x = 0, y = y)
                }
                .clip(config.shape)
                .drawBehind { drawRect(color = config.containerColor) }
                // Во время первой appear-анимации drag выключен: иначе pointer-down по телу листа
                // мгновенно перехватывает жест (startDragImmediately при работающей анимации), обрывает
                // появление у near-Hidden offset и осаживает лист в Hidden — нечаянный тап сразу после
                // открытия закрывал лист. После оседания на видимом якоре drag включается штатно.
                .anchoredDraggable(
                    state = state.draggableState,
                    enabled = config.draggable && !state.isAppearing,
                    orientation = Orientation.Vertical,
                    flingBehavior = flingBehavior,
                )
                // No-op click гасит тапы по видимому листу, чтобы они не проваливались на scrim (сосед
                // снизу по z) и не закрывали лист. Узел совпадает с видимым листом (тот же layout-offset);
                // clickable арбитрит tap vs drag: перетаскивание уходит в anchoredDraggable, тап — гасится
                // здесь. Нужен и при config.draggable = false, когда anchoredDraggable событий не ловит.
                .noRippleEffectClick(isEnable = true) {},
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .windowInsetsPadding(
                        WindowInsets.ime
                            .union(WindowInsets.navigationBars)
                            .only(WindowInsetsSides.Bottom),
                    ),
            ) {
                if (dragHandle != null) {
                    Box(modifier = Modifier.align(Alignment.CenterHorizontally)) {
                        dragHandle()
                    }
                }
                content()
            }
        }
    }
}


// Только горизонтальные safe-area отступы (вырезы/жесты по бокам). Нижний инсет
// (навигация) накладывается один раз внутри листа — на контент, чтобы фон листа
// доходил до нижней границы экрана и под него уходил scrim, без зазора у нав-бара.
@Composable
private fun rememberHorizontalSafeAreaPadding(sysBars: SystemBarsInsets): PaddingValues =
    remember(sysBars.safeLeft, sysBars.safeRight) {
        PaddingValues(
            start = sysBars.safeLeft,
            end = sysBars.safeRight,
        )
    }

// Cap — это МАКСИМУМ высоты: лист по высоте контента, но не выше доступной области.
// availableHeight берём из реальных constraints layout (BoxWithConstraints.maxHeight), а НЕ
// из DeviceState.screenHeightDp. Эти constraints равны полной высоте области оверлея (edge-to-edge).
// Вычитаем только верхний инсет (статус-бар): низ листа прилегает к реальной нижней границе экрана,
// а нижний инсет навигации компенсируется отступом контента, а не высотой листа.
@Composable
private fun rememberSheetHeightModifier(
    availableHeight: Dp,
    safeTop: Dp,
): Modifier = remember(availableHeight, safeTop) {
    val maxHeight = availableHeight - safeTop
    if (maxHeight > 0.dp) Modifier.heightIn(max = maxHeight) else Modifier
}

@Composable
private fun rememberAdaptiveWidthModifier(
    widthSizeClass: WindowSizeClass,
    screenWidthDp: Dp,
    adaptiveWidthFraction: Float,
): Modifier = remember(widthSizeClass, screenWidthDp, adaptiveWidthFraction) {
    when (widthSizeClass) {
        WindowSizeClass.Compact -> Modifier.fillMaxWidth()
        WindowSizeClass.Medium, WindowSizeClass.Expanded ->
            Modifier.widthIn(max = (screenWidthDp.value * adaptiveWidthFraction).dp)
    }
}
