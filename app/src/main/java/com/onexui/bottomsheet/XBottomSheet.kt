package com.onexui.bottomsheet

import android.view.WindowManager
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.IntrinsicMeasurable
import androidx.compose.ui.layout.IntrinsicMeasureScope
import androidx.compose.ui.layout.Layout
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
import androidx.compose.ui.unit.dp
import com.onexui.bottomsheet.presets.PresetLoader
import org.xplatform.uikit.compose.modifier.keyboard.adjustment.withAdjustmentForKeyboard
import org.xplatform.uikit.compose.modifier.keyboard.lift.KeyboardLiftState
import org.xplatform.uikit.compose.modifier.keyboard.lift.rememberKeyboardLiftState
import org.xplatform.uikit.compose.modifier.keyboard.shrink.withKeyboardShrink
import kotlin.math.roundToInt

// Fade-дистанция затемнения scrim: на первых px открытия/закрытия alpha разгоняется 0 → полный.
private val ScrimFadeDistance = 120.dp

// Поведение bottom-слота при открытой клавиатуре — гибкость, что именно поднимать над IME:
// Lift — весь контент (top/middle/bottom) поднимается над клавиатурой (дефолт, как было);
// StayUnderKeyboard — над клавиатурой поднимаются только top+middle, а bottom остаётся прижатым к нижней
// кромке листа и уходит ПОД клавиатуру (появляется при её скрытии). Требует FullScreen — при показе IME
// с этим режимом лист форсированно разворачивается на весь экран.
internal enum class KeyboardBottomBehavior { Lift, StayUnderKeyboard }

@Composable
internal fun XBottomSheet(
    state: XBottomSheetState,
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
    overlayBackground: Boolean = true,
    dragHandle: DragHandleStyle? = DragHandleStyle.Theme,
    dismissOnOutsideTap: Boolean = true,
    dismissOnSwipeDown: Boolean = true,
    bottomKeyboardBehavior: KeyboardBottomBehavior = KeyboardBottomBehavior.Lift,
    additionalTopCornerRadius: Dp = 0.dp,
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
    val scrimFadeDistancePx = with(density) { ScrimFadeDistance.toPx() }
    val isWide = screenWidthDp >= XBottomSheetDefaults.WideScreenThreshold
    val widthModifier = if (isWide) Modifier.width(XBottomSheetDefaults.MaxWidth) else Modifier.fillMaxWidth()
    // Клавиатура: единый источник состояния IME (xbet KeyboardLiftState), общий для авто-FullScreen и
    // модификаторов подъёма/сжатия. Alva-копию KeyboardLiftState не трогаем — здесь xbet-пакет.
    val keyboardState = rememberKeyboardLiftState()
    val imeVisible = keyboardState.value.isKeyboardVisible
    val keyboardHeightPx = if (imeVisible) keyboardState.value.keyboardHeight.roundToInt() else 0
    // Взаимодействие с высотой отключено: хендл скрыт, Loading, ИЛИ открыта клавиатура (уточнение юзера §6 —
    // при видимой IME жесты высоты выключены; закрытие только тапом снаружи/кнопкой).
    val interactionsEnabled = dragHandle != null && !state.isLoading && !imeVisible
    val isFullScreen = state.currentValue == SheetValue.ExpandedFullScreen
    // Плавная анимация появления/сворачивания Additional Top: 1 = Expanded (карточка утоплена overlap),
    // 0 = скрыта (уехала вверх). Считается ОДИН раз (не в SubcomposeLayout-теле, чтобы не дублироваться).
    val additionalTopFraction by animateFloatAsState(
        targetValue = if (state.additionalTopState == AdditionalTopState.Expanded) 1f else 0f,
        label = "additionalTopFraction",
    )

    // Все реактивные snapshotFlow-наблюдатели листа — в одном месте (SheetSnapshotFlowManager):
    // рост контента → высота за контентом; клавиатура → авто-FullScreen/откат (§6); Hidden → дроп IME (§6).
    // onSheetHidden роняет клавиатуру: иначе keyboard-lift оффсет продолжал бы поднимать контейнер и лист
    // «улетел» бы вверх, отцепившись от нижней кромки. alwaysFullScreenOnIme=true для StayUnderKeyboard —
    // bottom должен доставать до нижней кромки экрана, под клавиатуру.
    SheetSnapshotFlowManager(
        state = state,
        keyboardState = keyboardState,
        alwaysFullScreenOnIme = bottomKeyboardBehavior == KeyboardBottomBehavior.StayUnderKeyboard && bottom != null,
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
        state.dismissOnSwipeDown = dismissOnSwipeDown
        state.onDismissRequest = { currentOnDismiss() }
    }
    // Единственный потребитель канала жестов: применяет drag/settle по порядку (без гонок), один на весь лист.
    LaunchedEffect(state) { state.processGestures() }

    val nestedScrollConnection = remember(state, interactionsEnabled) {
        SheetNestedScrollConnection(
            state = state,
            interactionsEnabled = interactionsEnabled,
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
            keyboardState = keyboardState,
            isFullScreen = isFullScreen,
            bottomKeyboardBehavior = bottomKeyboardBehavior,
            keyboardHeightPx = keyboardHeightPx,
            navBarPx = navBarPx,
            additionalTopFraction = additionalTopFraction,
            additionalTopCornerRadius = additionalTopCornerRadius,
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
    keyboardState: State<KeyboardLiftState>,
    isFullScreen: Boolean,
    bottomKeyboardBehavior: KeyboardBottomBehavior,
    keyboardHeightPx: Int,
    navBarPx: Int,
    additionalTopFraction: Float,
    additionalTopCornerRadius: Dp,
    additionalTop: (@Composable () -> Unit)?,
    top: (@Composable () -> Unit)?,
    bottom: (@Composable () -> Unit)?,
    middle: @Composable () -> Unit,
    modifier: Modifier = Modifier,
) {
    val ceilingPx = (screenHeightPx - statusBarPx).coerceAtLeast(0)
    val shadowModifier = if (!overlayBackground) Modifier.softSheetShadow(XBottomSheetDefaults.Shape) else Modifier
    val keyboardAdjustmentModifier = if (!isFullScreen) {
        Modifier.withAdjustmentForKeyboard(keyboardState = keyboardState)
    } else {
        Modifier
    }
    // fillHeight: detect-слот wrap'ит тело (для замера контента), place-слот заполняет offset (фон Surface тянется
    // на всю высоту листа — при overshoot/растяжении снизу не остаётся прозрачной дыры).
    val bodyOf: @Composable (Boolean) -> Unit = { fillHeight ->
        SheetBody(
            state = state,
            dragHandle = dragHandle,
            keyboardState = keyboardState,
            isFullScreen = isFullScreen,
            bottomKeyboardBehavior = bottomKeyboardBehavior,
            keyboardHeightPx = keyboardHeightPx,
            navBarPx = navBarPx,
            fillHeight = fillHeight,
            additionalTopFraction = additionalTopFraction,
            additionalTopCornerRadius = additionalTopCornerRadius,
            additionalTop = additionalTop,
            top = top,
            bottom = bottom,
            middle = middle,
        )
    }
    // SubcomposeLayout: тело композируется ОДИН раз, меряется ДВАЖДЫ. (1) detect — при ОГРАНИЧЕННОЙ высоте
    // maxHeight: короткий контент wrap (< maxHeight), скроллируемый/ленивый (LazyColumn и т.п.) fill (== maxHeight).
    // Это contentHeightPx → режим wrap/fill (см. SheetMetrics.fills). LazyColumn при ограниченной высоте НЕ
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
        val detectHeight = subcompose(SlotDetect) { bodyOf(false) }.first().measure(
            Constraints(maxWidth = width, minHeight = 0, maxHeight = ceilingPx),
        ).height
        state.updateMetrics(
            screenHeightPx = screenHeightPx,
            statusBarPx = statusBarPx,
            contentHeightPx = detectHeight,
            loadingSheetHeightPx = loadingSheetHeightPx,
        )
        val placeHeight = state.offset.value.roundToInt().coerceIn(0, screenHeightPx)
        val placeable = subcompose(SlotPlace) { bodyOf(true) }.first().measure(
            Constraints.fixed(width = width, height = placeHeight),
        )
        layout(width = width, height = placeHeight) {
            placeable.place(x = 0, y = 0)
        }
    }
}

private object SlotDetect
private object SlotPlace

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
    bottomKeyboardBehavior: KeyboardBottomBehavior,
    keyboardHeightPx: Int,
    navBarPx: Int,
    fillHeight: Boolean,
    additionalTopFraction: Float,
    additionalTopCornerRadius: Dp,
    additionalTop: (@Composable () -> Unit)?,
    top: (@Composable () -> Unit)?,
    bottom: (@Composable () -> Unit)?,
    middle: @Composable () -> Unit,
) {
    val density = LocalDensity.current
    // StayUnderKeyboard активен только когда есть bottom-слот. Тогда bottom прижат к нижней кромке листа и
    // уходит под клавиатуру (кастомный layout ниже), а не поднимается вместе с контентом.
    val stayUnderKeyboard = bottomKeyboardBehavior == KeyboardBottomBehavior.StayUnderKeyboard && bottom != null
    // fillHeight (place-слот): тело заполняет offset (Surface/Column fillMaxSize, middle weight fill=true) — фон
    // листа тянется на всю высоту, при растяжении снизу нет прозрачной дыры. !fillHeight (detect): wrap по контенту.
    val sizeModifier = if (fillHeight) Modifier.fillMaxSize() else Modifier.fillMaxWidth()
    val middleShrinkModifier = if (isFullScreen && !stayUnderKeyboard) {
        Modifier.withKeyboardShrink(
            softInputMode = WindowManager.LayoutParams.SOFT_INPUT_ADJUST_NOTHING,
            keyboardState = keyboardState,
        )
    } else {
        Modifier
    }
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
        Surface(
            modifier = sizeModifier,
            shape = XBottomSheetDefaults.Shape,
            color = XBottomSheetDefaults.SheetBackground,
        ) {
            if (stayUnderKeyboard) {
                // Режим «bottom под клавиатурой». Инсет nav bar и клавиатуру обрабатывает кастомный layout
                // (не windowInsetsPadding), чтобы bottom мог опуститься ПОД клавиатуру к нижней кромке экрана.
                Column(modifier = sizeModifier) {
                    top?.invoke()
                    StayUnderKeyboardContent(
                        modifier = (if (fillHeight) Modifier.weight(1f) else Modifier.weight(1f, fill = false))
                            .fillMaxWidth(),
                        keyboardHeightPx = keyboardHeightPx,
                        navBarPx = navBarPx,
                        middle = middleContent,
                        bottom = bottom,
                    )
                }
            } else {
                // Lift (дефолт): над клавиатурой поднимается ВЕСЬ контент — top + middle + bottom. Вне FullScreen
                // подъём делает withAdjustmentForKeyboard (весь лист едет вверх, bottom едет вместе с ним). В
                // FullScreen лист уже у потолка, выше не уедет → поджимаем НИЗ контента паддингом на высоту
                // клавиатуры: middle (weight) ужимается, а bottom встаёт прямо над клавиатурой, а не под ней.
                // Без IME — обычный инсет nav bar (bottom над баром, фон Surface продолжается под ним edge-to-edge).
                val liftBottomInset = if (isFullScreen && keyboardHeightPx > 0) {
                    Modifier.padding(bottom = with(density) { keyboardHeightPx.toDp() })
                } else {
                    Modifier.windowInsetsPadding(WindowInsets.navigationBars)
                }
                Column(
                    modifier = sizeModifier.then(liftBottomInset),
                ) {
                    top?.invoke()
                    Box(
                        modifier = (if (fillHeight) Modifier.weight(1f) else Modifier.weight(1f, fill = false))
                            .fillMaxWidth()
                            .then(middleShrinkModifier),
                    ) {
                        middleContent()
                    }
                    bottom?.invoke()
                }
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
    keyboardHeightPx: Int,
    navBarPx: Int,
    middle: @Composable () -> Unit,
    bottom: @Composable () -> Unit,
) {
    val measurePolicy = remember(keyboardHeightPx, navBarPx) {
        StayUnderKeyboardMeasurePolicy(keyboardHeightPx, navBarPx)
    }
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
    private val keyboardHeightPx: Int,
    private val navBarPx: Int,
) : MeasurePolicy {

    override fun MeasureScope.measure(
        measurables: List<Measurable>,
        constraints: Constraints,
    ): MeasureResult {
        val width = constraints.maxWidth
        val keyboardVisible = keyboardHeightPx > 0
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
        val reservePx = if (keyboardVisible) keyboardHeightPx else bottomHeight + navBarPx
        val middleMaxHeight = (height - reservePx).coerceAtLeast(0)
        val middlePlaceable = measurables[0].measure(
            constraints.copy(minHeight = 0, maxHeight = middleMaxHeight),
        )
        // Позиция bottom: без IME — над nav bar; при IME — прижат к низу экрана (уходит под клавиатуру).
        val bottomY = if (keyboardVisible) height - bottomHeight else height - bottomHeight - navBarPx
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
        navBarPx
}
