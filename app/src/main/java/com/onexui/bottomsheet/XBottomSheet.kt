package com.onexui.bottomsheet

import android.view.WindowManager
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationVector1D
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.IntrinsicMeasurable
import androidx.compose.ui.layout.IntrinsicMeasureScope
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.Measurable
import androidx.compose.ui.layout.MeasurePolicy
import androidx.compose.ui.layout.MeasureResult
import androidx.compose.ui.layout.MeasureScope
import androidx.compose.ui.layout.SubcomposeLayout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import com.onexui.bottomsheet.presets.PresetLoader
import org.xplatform.uikit.compose.modifier.keyboard.adjustment.withAdjustmentForKeyboard
import org.xplatform.uikit.compose.modifier.keyboard.lift.KeyboardLiftState
import org.xplatform.uikit.compose.modifier.keyboard.lift.rememberKeyboardLiftState
import org.xplatform.uikit.compose.modifier.keyboard.shrink.withKeyboardShrink
import kotlin.math.roundToInt

// Поведение bottom-слота при открытой клавиатуре — гибкость, что именно поднимать над IME:
// Lift — весь контент (top/middle/bottom) поднимается над клавиатурой (дефолт, как было);
// StayUnderKeyboard — над клавиатурой поднимаются только top+middle, а bottom остаётся прижатым к нижней
// кромке листа и уходит ПОД клавиатуру (появляется при её скрытии). Требует FullScreen — при показе IME
// с этим режимом лист форсированно разворачивается на весь экран.
internal enum class BottomKeyboardBehavior { Lift, StayUnderKeyboard }

@Composable
internal fun XBottomSheet(
    state: XBottomSheetState,
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
    config: XBottomSheetConfig = XBottomSheetConfig.Default,
    additionalTop: (@Composable () -> Unit)? = null,
    top: (@Composable () -> Unit)? = null,
    bottom: (@Composable () -> Unit)? = null,
    middle: @Composable () -> Unit,
) {
    val density = LocalDensity.current
    val keyboardController = LocalSoftwareKeyboardController.current
    val focusManager = LocalFocusManager.current
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
    val scrimFadeDistancePx = with(density) { XBottomSheetDefaults.ScrimFadeDistance.toPx() }
    val isWide = screenWidthDp >= XBottomSheetDefaults.WideScreenThreshold
    val widthModifier = if (isWide) Modifier.width(XBottomSheetDefaults.MaxWidth) else Modifier.fillMaxWidth()
    // Клавиатура: единый источник состояния IME (xbet KeyboardLiftState), общий для авто-FullScreen и
    // модификаторов подъёма/сжатия. Alva-копию KeyboardLiftState не трогаем — здесь xbet-пакет.
    val keyboardState = rememberKeyboardLiftState()
    // Взаимодействие с высотой отключено: хендл скрыт, Loading, ИЛИ открыта клавиатура (уточнение юзера §6 —
    // при видимой IME жесты высоты выключены; закрытие только тапом снаружи/кнопкой). derivedStateOf: боолево
    // значение уведомляет читателей только при ФАКТИЧЕСКОЙ смене (show/hide IME, isLoading), а не на каждый
    // пересчёт входов (правило 5).
    val interactionsEnabledState = remember(config.dragHandle) {
        derivedStateOf { config.dragHandle != null && !state.isLoading && !keyboardState.value.isKeyboardVisible }
    }
    val interactionsEnabled by interactionsEnabledState
    val isFullScreen = state.currentValue == SheetValue.ExpandedFullScreen
    // Плавная анимация появления/сворачивания Additional Top: 1 = Expanded (карточка утоплена overlap),
    // 0 = скрыта (уехала вверх). Анимация — через Animatable (правило: НЕ animate*AsState, чтобы не гонять
    // рекомпозицию покадрово): .value читается ТОЛЬКО в measure AdditionalTopMeasurePolicy → инвалидация layout,
    // а не композиции. Стартовое значение — по восстановленному additionalTopState (после ротации/process-death
    // карточка появляется сразу в нужном положении). snapshotFlow гонит animateTo при смене состояния.
    val additionalTopFraction = remember(state) {
        Animatable(if (state.additionalTopState == AdditionalTopState.Expanded) 1f else 0f)
    }
    LaunchedEffect(state) {
        snapshotFlow { state.additionalTopState }
            .collect { additionalTopState ->
                additionalTopFraction.animateTo(
                    targetValue = if (additionalTopState == AdditionalTopState.Expanded) 1f else 0f,
                    animationSpec = NativeSheetSpring,
                )
            }
    }

    // Все реактивные snapshotFlow-наблюдатели листа — в одном месте (ObserveSheetState):
    // рост контента → высота за контентом; клавиатура → авто-FullScreen/откат (§6); Hidden → дроп IME (§6).
    // onSheetHidden роняет клавиатуру: иначе keyboard-lift оффсет продолжал бы поднимать контейнер и лист
    // «улетел» бы вверх, отцепившись от нижней кромки. alwaysFullScreenOnIme=true для StayUnderKeyboard —
    // bottom должен доставать до нижней кромки экрана, под клавиатуру.
    ObserveSheetState(
        state = state,
        keyboardState = keyboardState,
        alwaysFullScreenOnIme = config.keyboard.bottomBehavior == BottomKeyboardBehavior.StayUnderKeyboard && bottom != null,
        onSheetHidden = {
            keyboardController?.hide()
            focusManager.clearFocus(force = true)
        },
    )
    // Поворот/resize: высота снапится к якорю текущего стейта (без анимации). one-shot по размерам экрана —
    // не snapshotFlow, поэтому вне менеджера (иначе смена размеров пере-подписывала бы коллекторы).
    LaunchedEffect(state, screenHeightPx, statusBarPx) { state.snapToCurrentAnchor() }

    // Конфиг закрытия для жестов: пишем в стейт (команды канала — без лямбд). onDismissRequest берём свежий.
    SideEffect {
        state.dismissOnSwipeDown = config.dismiss.onSwipeDown
        state.onDismissRequest = { currentOnDismiss() }
    }
    // Единственный потребитель канала жестов: применяет drag/settle по порядку (без гонок), один на весь лист.
    LaunchedEffect(state) { state.processGestures() }

    val nestedScrollConnection = remember(state) {
        SheetNestedScrollConnection(state = state, enabledState = interactionsEnabledState)
    }
    Box(modifier = Modifier.fillMaxSize().then(modifier)) {
        SheetScrim(
            state = state,
            overlayBackground = config.overlayBackground,
            dismissOnOutsideTap = config.dismiss.onOutsideTap,
            scrimFadeDistancePx = scrimFadeDistancePx,
            onDismissRequest = { currentOnDismiss() },
        )

        SheetContainer(
            state = state,
            insets = SheetInsets(
                screenHeightPx = screenHeightPx,
                statusBarPx = statusBarPx,
                navBarPx = navBarPx,
                loadingSheetHeightPx = loadingSheetHeightPx,
            ),
            overlayBackground = config.overlayBackground,
            dragHandle = config.dragHandle,
            interactionsEnabled = interactionsEnabled,
            nestedScrollConnection = nestedScrollConnection,
            keyboardState = keyboardState,
            isFullScreen = isFullScreen,
            bottomKeyboardBehavior = config.keyboard.bottomBehavior,
            additionalTopFraction = additionalTopFraction,
            additionalTopConfig = AdditionalTopConfig(cornerRadius = config.additionalTopCornerRadius),
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

// Геометрия/инсеты листа для измерителя — группировка скалярных параметров SheetContainer (RO-RO, без
// primitive-простыни). Чистые данные, без лямбд.
internal data class SheetInsets(
    val screenHeightPx: Int,
    val statusBarPx: Int,
    val navBarPx: Int,
    val loadingSheetHeightPx: Int,
)

// Конфиг слоя Additional Top. Слоты (additionalTop-контент) передаются ОТДЕЛЬНЫМИ параметрами, не полями —
// правило 10 (никаких лямбд в data class).
internal data class AdditionalTopConfig(
    val cornerRadius: Dp,
)

// Контейнер листа: измеритель контента через кастомный Layout с ОДНИМ ребёнком-телом (тело композируется
// РОВНО один раз, без SubcomposeLayout). soft-shadow при overlayBackground=false. Подъём над клавиатурой —
// withAdjustmentForKeyboard (вместо imePadding), включён только вне FullScreen: в FullScreen лист уже у потолка,
// подъём выгнал бы верх под статус-бар — там вместо подъёма Middle сжимается (см. SheetBody).
@Composable
private fun SheetContainer(
    state: XBottomSheetState,
    insets: SheetInsets,
    overlayBackground: Boolean,
    dragHandle: DragHandleStyle?,
    interactionsEnabled: Boolean,
    nestedScrollConnection: SheetNestedScrollConnection,
    keyboardState: State<KeyboardLiftState>,
    isFullScreen: Boolean,
    bottomKeyboardBehavior: BottomKeyboardBehavior,
    additionalTopFraction: Animatable<Float, AnimationVector1D>,
    additionalTopConfig: AdditionalTopConfig,
    additionalTop: (@Composable () -> Unit)?,
    top: (@Composable () -> Unit)?,
    bottom: (@Composable () -> Unit)?,
    middle: @Composable () -> Unit,
    modifier: Modifier = Modifier,
) {
    val ceilingPx = (insets.screenHeightPx - insets.statusBarPx).coerceAtLeast(0)
    val shadowModifier = if (!overlayBackground) Modifier.softSheetShadow(XBottomSheetDefaults.Shape) else Modifier
    val keyboardAdjustmentModifier = if (!isFullScreen) {
        Modifier.withAdjustmentForKeyboard(keyboardState = keyboardState)
    } else {
        Modifier
    }
    // fillHeight: detect-слот wrap'ит тело (для замера контента), place-слот заполняет offset (фон Surface тянется
    // на всю высоту листа — при overshoot/растяжении снизу не остаётся прозрачной дыры).
    @Composable
    fun sheetBodySlot(fillHeight: Boolean) {
        SheetBody(
            state = state,
            dragHandle = dragHandle,
            keyboardState = keyboardState,
            isFullScreen = isFullScreen,
            bottomKeyboardBehavior = bottomKeyboardBehavior,
            navBarPx = insets.navBarPx,
            fillHeight = fillHeight,
            additionalTopFraction = additionalTopFraction,
            additionalTopCornerRadius = additionalTopConfig.cornerRadius,
            additionalTop = additionalTop,
            top = top,
            bottom = bottom,
            middle = middle,
        )
    }
    // Слот-лямбды detect/place — В КОМПОЗИЦИИ (не в measure-теле): стабильная идентичность контента между
    // layout-пассами (contentChanged=false у SubcomposeLayout) → SubcomposeLayout не пере-сетит контент каждый
    // пасс и не аллоцирует новые лямбды на кадр драга. Тело композируется один раз на слот. Лямбды НЕ в remember.
    val detectBody: @Composable () -> Unit = { sheetBodySlot(fillHeight = false) }
    val placeBody: @Composable () -> Unit = { sheetBodySlot(fillHeight = true) }
    // SubcomposeLayout: тело композируется ОДИН раз, меряется ДВАЖДЫ. (1) detect — при ОГРАНИЧЕННОЙ высоте
    // maxHeight: короткий контент wrap (< maxHeight), скроллируемый/ленивый (LazyColumn и т.п.) fill (== maxHeight).
    // Это contentHeightPx → режим wrap/fill (см. SheetMetrics.isFillMode). LazyColumn при ограниченной высоте НЕ
    // крашится и wrap'ится при малом контенте (проверено пробом). (2) place — при fixed(offset): реальная высота
    // листа (offset может превышать maxHeight на overshoot верхнего якоря → до screenHeightPx).
    SubcomposeLayout(
        modifier = modifier
            .then(shadowModifier)
            .then(keyboardAdjustmentModifier)
            .nestedScroll(nestedScrollConnection)
            .sheetDrag(state = state, enabled = interactionsEnabled),
    ) { constraints ->
        val width = constraints.maxWidth
        // Один Measurable нельзя мерить дважды за пасс → два слота. DETECT — отдельная (невидимая) композиция
        // тела, меряется при maxHeight (стабильно между кадрами): короткий контент wrap (<maxHeight),
        // ленивый/скролл fill (==maxHeight) → режим. PLACE — видимая композиция, меряется при fixed(offset).
        val detectHeight = subcompose(ContentMeasureSlot, detectBody).first().measure(
            Constraints(maxWidth = width, minHeight = 0, maxHeight = ceilingPx),
        ).height
        state.updateMetrics(
            screenHeightPx = insets.screenHeightPx,
            statusBarPx = insets.statusBarPx,
            contentHeightPx = detectHeight,
            loadingSheetHeightPx = insets.loadingSheetHeightPx,
        )
        val placeHeight = state.offset.value.roundToInt().coerceIn(0, insets.screenHeightPx)
        val placeable = subcompose(VisibleContentSlot, placeBody).first().measure(
            Constraints.fixed(width = width, height = placeHeight),
        )
        layout(width = width, height = placeHeight) {
            placeable.place(x = 0, y = 0)
        }
    }
}

private object ContentMeasureSlot
private object VisibleContentSlot

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
    bottomKeyboardBehavior: BottomKeyboardBehavior,
    navBarPx: Int,
    fillHeight: Boolean,
    additionalTopFraction: Animatable<Float, AnimationVector1D>,
    additionalTopCornerRadius: Dp,
    additionalTop: (@Composable () -> Unit)?,
    top: (@Composable () -> Unit)?,
    bottom: (@Composable () -> Unit)?,
    middle: @Composable () -> Unit,
) {
    // StayUnderKeyboard активен только когда есть bottom-слот. Тогда bottom прижат к нижней кромке листа и
    // уходит под клавиатуру (кастомный layout ниже), а не поднимается вместе с контентом.
    val isBottomUnderKeyboardMode = bottomKeyboardBehavior == BottomKeyboardBehavior.StayUnderKeyboard && bottom != null
    // fillHeight (place-слот): тело заполняет offset (Surface/Column fillMaxSize, middle weight fill=true) — фон
    // листа тянется на всю высоту, при растяжении снизу нет прозрачной дыры. !fillHeight (detect): wrap по контенту.
    val sizeModifier = if (fillHeight) Modifier.fillMaxSize() else Modifier.fillMaxWidth()
    // Middle БЕЗ нашего verticalScroll: скролл предоставляет сам контент (LazyColumn/LazyGrid/Column+verticalScroll).
    // weight(1f, fill=false): короткий контент wrap'ится (лист по высоте контента), ленивый/скролл заполняет
    // (fill-режим, фикс-якоря) + резервирует место bottom. Loading → Loader вместо контента.
    val middleContent: @Composable () -> Unit = {
        if (state.isLoading) {
            PresetLoader()
        } else {
            middle()
        }
    }
    // Высота ТЕЛА wrap'ится (fillMaxWidth, без fillMaxHeight) — SubcomposeLayout при detect-замере видит натуральную
    // высоту контента; при place-замере fixed(offset) форсит высоту листа. fillMaxSize сломал бы wrap-детект.
    val sheetSurface: @Composable () -> Unit = {
        SheetSurface(modifier = sizeModifier) {
            if (isBottomUnderKeyboardMode) {
                // Режим «bottom под клавиатурой»: bottom прижат к нижней кромке и уходит ПОД клавиатуру (кастомный
                // layout StayUnderKeyboardContent), а не поднимается вместе с контентом. top — над регионом.
                Column(modifier = sizeModifier) {
                    top?.invoke()
                    StayUnderKeyboardContent(
                        modifier = (if (fillHeight) Modifier.weight(1f) else Modifier.weight(1f, fill = false))
                            .fillMaxWidth(),
                        keyboardState = keyboardState,
                        navBarPx = navBarPx,
                        middle = middleContent,
                        bottom = bottom,
                    )
                }
            } else {
                LiftContent(
                    modifier = sizeModifier,
                    keyboardState = keyboardState,
                    isFullScreen = isFullScreen,
                    navBarPx = navBarPx,
                    fillHeight = fillHeight,
                    top = top,
                    middle = middleContent,
                    bottom = bottom,
                )
            }
        }
    }
    Box(
        modifier = sizeModifier
            // No-op гаситель тапов по телу листа: consume up → тап не проваливается на scrim (сосед снизу по z)
            // и не закрывает лист. detectTapGestures пропускает drag'и (отменяется по slop) — драг листа/скролл
            // списка работают штатно.
            .pointerInput(Unit) { detectTapGestures {} },
    ) {
        // Additional Top держим смонтированным всегда (при наличии слота) — видимая высота карточки ПЛАВНО
        // анимируется через additionalTopFraction (1 = Expanded, 0 = скрыта). Верхние углы карточки — cornerRadius.
        if (additionalTop != null) {
            AdditionalTopStack(
                visibleFraction = additionalTopFraction,
                cornerRadius = additionalTopCornerRadius,
                additionalTopContent = additionalTop,
                sheetContent = sheetSurface,
            )
        } else {
            sheetSurface()
        }
        if (dragHandle != null) {
            DragHandle(style = dragHandle, modifier = Modifier.align(Alignment.TopCenter))
        }
    }
}

// Обёртка-Surface листа: вшитая форма (20/20/0/0) и фон. Слот-контент — под-клавиатурный (StayUnderKeyboardContent)
// или Lift-режим (LiftContent).
@Composable
private fun SheetSurface(
    modifier: Modifier,
    content: @Composable () -> Unit,
) {
    Surface(
        modifier = modifier,
        shape = XBottomSheetDefaults.Shape,
        color = XBottomSheetDefaults.SheetBackground,
    ) {
        content()
    }
}

// Lift-режим (дефолт): над клавиатурой поднимается ВЕСЬ контент — top + middle + bottom. Вне FullScreen подъём
// делает withAdjustmentForKeyboard (весь лист едет вверх). В FullScreen лист уже у потолка → поджимаем НИЗ на
// высоту клавиатуры: middle (weight) ужимается withKeyboardShrink, bottom встаёт прямо над клавиатурой. Без IME —
// инсет nav bar. Нижний inset считается в measure-фазе (keyboardState.value в layout-лямбде) → keyboardHeightPx
// НЕ живёт в композиции корня, реакция на IME чисто layout-фазная. Зеркально StayUnderKeyboardContent.
@Composable
private fun LiftContent(
    modifier: Modifier,
    keyboardState: State<KeyboardLiftState>,
    isFullScreen: Boolean,
    navBarPx: Int,
    fillHeight: Boolean,
    top: (@Composable () -> Unit)?,
    middle: @Composable () -> Unit,
    bottom: (@Composable () -> Unit)?,
) {
    val middleShrinkModifier = if (isFullScreen) {
        Modifier.withKeyboardShrink(
            softInputMode = WindowManager.LayoutParams.SOFT_INPUT_ADJUST_NOTHING,
            keyboardState = keyboardState,
        )
    } else {
        Modifier
    }
    val bottomInsetModifier = Modifier.layout { measurable, constraints ->
        val ime = keyboardState.value
        val bottomInset = if (isFullScreen && ime.isKeyboardVisible) {
            ime.keyboardHeight.roundToInt()
        } else {
            navBarPx
        }
        val placeable = measurable.measure(
            constraints.copy(
                minHeight = (constraints.minHeight - bottomInset).coerceAtLeast(0),
                maxHeight = (constraints.maxHeight - bottomInset).coerceAtLeast(0),
            ),
        )
        layout(placeable.width, placeable.height + bottomInset) {
            placeable.place(0, 0)
        }
    }
    Column(modifier = modifier.then(bottomInsetModifier)) {
        top?.invoke()
        Box(
            modifier = (if (fillHeight) Modifier.weight(1f) else Modifier.weight(1f, fill = false))
                .fillMaxWidth()
                .then(middleShrinkModifier),
        ) {
            middle()
        }
        bottom?.invoke()
    }
}

// Middle + Bottom для режима StayUnderKeyboard. Кастомный Layout с двумя детьми (каждый композируется один раз):
//   · middle (скролл) кладётся сверху и ограничен по высоте так, чтобы кончаться у ВЕРХНЕЙ кромки клавиатуры
//     (при IME) или у верха bottom-слота (без IME);
//   · bottom прижат к НИЖНЕЙ кромке региона (= нижняя кромка листа = низ экрана в FullScreen): без клавиатуры —
//     над nav bar; при клавиатуре — уходит вниз ПОД неё (не виден, появляется при скрытии IME).
// Регион = weight(1f) под top-слотом; его низ совпадает с низом Surface (в FullScreen — низ экрана).
// keyboardHeightPx — inset IME от низа экрана (включает зону nav bar), поэтому у клавиатуры nav-инсет не добавляем.
@Composable
private fun StayUnderKeyboardContent(
    modifier: Modifier,
    keyboardState: State<KeyboardLiftState>,
    navBarPx: Int,
    middle: @Composable () -> Unit,
    bottom: @Composable () -> Unit,
) {
    // Политика — ОДИН инстанс на лист (remember без ключей). Обе меняющиеся величины читаются в measure как State:
    // keyboardState.value (IME) и navBarState.value (nav bar; rememberUpdatedState держит поле свежим без
    // пересоздания политики). Реакция чисто layout-фазная — LayoutNode не получает новую политику.
    val navBarState = rememberUpdatedState(navBarPx)
    val measurePolicy = remember { StayUnderKeyboardMeasurePolicy(keyboardState, navBarState) }
    Layout(
        modifier = modifier,
        content = {
            // middle сам предоставляет скролл (LazyColumn/verticalScroll) — наш verticalScroll не нужен.
            Box(modifier = Modifier.fillMaxWidth()) { middle() }
            Box(modifier = Modifier.fillMaxWidth()) { bottom() }
        },
        measurePolicy = measurePolicy,
    )
}

private class StayUnderKeyboardMeasurePolicy(
    private val keyboardState: State<KeyboardLiftState>,
    private val navBarState: State<Int>,
) : MeasurePolicy {

    override fun MeasureScope.measure(
        measurables: List<Measurable>,
        constraints: Constraints,
    ): MeasureResult {
        val width = constraints.maxWidth
        // Снапшот-чтение IME/navBar в layout-фазе: инвалидация measure этого узла при их смене, без рекомпозиции.
        val navBarPx = navBarState.value
        val keyboardHeightPx = keyboardState.value.let { liftState ->
            if (liftState.isKeyboardVisible) liftState.keyboardHeight.roundToInt() else 0
        }
        val isKeyboardVisible = keyboardHeightPx > 0
        val bottomPlaceable = measurables[1].measure(
            constraints.copy(minHeight = 0, maxHeight = Constraints.Infinity),
        )
        val bottomHeight = bottomPlaceable.height
        // intrinsic-проход (maxHeight = Infinity) не должен доходить сюда — есть override maxIntrinsicHeight;
        // но на всякий случай кладём middle натурально, bottom под ним, и возвращаем конечную высоту.
        if (constraints.maxHeight == Constraints.Infinity) {
            val middleNatural = measurables[0].measure(
                constraints.copy(minHeight = 0, maxHeight = Constraints.Infinity),
            )
            val naturalHeight = middleNatural.height + bottomHeight
            return layout(width, naturalHeight) {
                middleNatural.place(x = 0, y = 0)
                bottomPlaceable.place(x = 0, y = middleNatural.height)
            }
        }
        val height = constraints.maxHeight
        // Резерв под низом middle: при IME — высота клавиатуры (middle кончается у её верха); без IME —
        // высота bottom + nav bar (middle кончается у верха bottom, тот над баром).
        val reservePx = if (isKeyboardVisible) keyboardHeightPx else bottomHeight + navBarPx
        val middleMaxHeight = (height - reservePx).coerceAtLeast(0)
        val middlePlaceable = measurables[0].measure(
            constraints.copy(minHeight = 0, maxHeight = middleMaxHeight),
        )
        // Позиция bottom: без IME — над nav bar; при IME — прижат к низу экрана (уходит под клавиатуру).
        val bottomY = if (isKeyboardVisible) height - bottomHeight else height - bottomHeight - navBarPx
        return layout(width, height) {
            middlePlaceable.place(x = 0, y = 0)
            bottomPlaceable.place(x = 0, y = bottomY.coerceAtLeast(0))
        }
    }

    // Натуральная высота = высота списка + высота bottom + nav bar (как у старого Column со стики-низом).
    // Без override Compose прогнал бы measure с Infinity-высотой → мусор в contentHeightPx и краш.
    override fun IntrinsicMeasureScope.maxIntrinsicHeight(
        measurables: List<IntrinsicMeasurable>,
        width: Int,
    ): Int = measurables[0].maxIntrinsicHeight(width) +
        measurables[1].maxIntrinsicHeight(width) +
        navBarState.value
}
