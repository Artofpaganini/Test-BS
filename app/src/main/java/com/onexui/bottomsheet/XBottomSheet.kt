package com.onexui.bottomsheet

import androidx.activity.compose.PredictiveBackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.LocalWindowInfo
import com.onexui.bottomsheet.additionaltop.AdditionalTopState
import com.onexui.bottomsheet.config.BottomKeyboardBehavior
import com.onexui.bottomsheet.config.XBottomSheetConfig
import com.onexui.bottomsheet.config.XBottomSheetConfigDefault
import com.onexui.bottomsheet.config.resolveHandleStatic
import com.onexui.bottomsheet.config.resolveHandleTheme
import com.onexui.bottomsheet.config.resolveScrim
import com.onexui.bottomsheet.config.resolveSheetBackground
import com.onexui.bottomsheet.gesture.SheetNestedScrollConnection
import com.onexui.bottomsheet.layout.SheetContainer
import com.onexui.bottomsheet.layout.SheetInsets
import com.onexui.bottomsheet.layout.SheetScrim
import com.onexui.bottomsheet.observe.ObserveSheetState
import com.onexui.bottomsheet.presets.PresetLoader
import com.onexui.bottomsheet.state.SheetValue
import com.onexui.bottomsheet.state.XBottomSheetState
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.launch
import org.xplatform.uikit.compose.modifier.keyboard.lift.rememberKeyboardLiftState

/**
 * Корень бottom-листа: связывает стейт, конфиг и слоты (additionalTop/top/bottom/middle), рисует scrim и
 * контейнер, резолвит тему-цвета, вайрит физику/IME/dismiss в стейт одним SideEffect'ом и обрабатывает
 * predictive-back.
 */
@Composable
internal fun XBottomSheet(
    state: XBottomSheetState,
    onDismissRequest: suspend () -> Unit,
    modifier: Modifier = Modifier,
    config: XBottomSheetConfig = XBottomSheetConfigDefault,
    additionalTop: (@Composable XBottomSheetScope.() -> Unit)? = null,
    top: (@Composable XBottomSheetScope.() -> Unit)? = null,
    bottom: (@Composable XBottomSheetScope.() -> Unit)? = null,
    middle: @Composable XBottomSheetScope.() -> Unit,
) {
    val density = LocalDensity.current
    val keyboardController = LocalSoftwareKeyboardController.current
    val focusManager = LocalFocusManager.current
    // onDismissRequest — suspend; state.onDismissRequest остаётся не-suspend launcher'ом (см. SideEffect ниже).
    val dismissScope = rememberCoroutineScope()
    val currentOnDismiss by rememberUpdatedState(onDismissRequest)
    val sheetScope = remember(state) { XBottomSheetScopeImpl(state, config.loadingSheetHeight) }
    // Размеры экрана из LocalWindowInfo.containerSize (px); при повороте обновляется сам.
    val containerSize = LocalWindowInfo.current.containerSize
    val screenHeightPx = containerSize.height
    val screenWidthDp = with(density) { containerSize.width.toDp() }
    val statusBarPx = WindowInsets.statusBars.getTop(density)
    // navBar inset: фон Surface остаётся edge-to-edge под баром, контент паддится инсетом внутри (bottom над баром).
    val navBarPx = WindowInsets.navigationBars.getBottom(density)
    // Loading-якорь = 192dp + navBarPx: видимая зона Loader'а остаётся 192dp несмотря на nav-bar-паддинг внутри.
    val loadingSheetHeightPx = with(density) { config.loadingSheetHeight.roundToPx() } + navBarPx
    val scrimFadeDistancePx = with(density) { config.scrimFadeDistance.toPx() }
    // Цвета резолвятся в композиции корня (тема доступна): Unspecified -> дефолт. Вниз идут готовые Color-примитивы.
    val scrimColor = config.colors.resolveScrim()
    val sheetBackgroundColor = config.colors.resolveSheetBackground()
    val handleThemeColor = config.colors.resolveHandleTheme()
    val handleStaticColor = config.colors.resolveHandleStatic()
    val isWide = screenWidthDp >= config.wideScreenThreshold
    val widthModifier = if (isWide) Modifier.width(config.maxWidth) else Modifier.fillMaxWidth()
    // Единый источник состояния IME для авто-FullScreen и модификаторов подъёма/сжатия.
    val keyboardState = rememberKeyboardLiftState()
    // Жесты высоты отключены при dragHandle==null / Loading / открытой IME; derivedStateOf будит читателей только при смене.
    val interactionsEnabledState = remember(config.dragHandle) {
        derivedStateOf { config.dragHandle != null && !state.isLoading && !keyboardState.value.isKeyboardVisible }
    }
    val interactionsEnabled by interactionsEnabledState
    val isFullScreen = state.currentValue == SheetValue.ExpandedFullScreen
    // Фракция Additional Top (1=Expanded, 0=скрыта). Animatable: .value читается в measure -> инвалидация layout, не композиции.
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

    // onSheetHidden роняет IME: иначе keyboard-lift оффсет поднял бы пустой контейнер и лист «улетел» бы вверх.
    ObserveSheetState(
        state = state,
        keyboardState = keyboardState,
        onSheetHidden = { sheetScope.hideKeyboard() },
    )
    // Predictive-back (Android 14+): жест двигает лист за прогрессом через backShift; отмена -> 0, успех -> dismiss + сброс.
    // enabled=false (дефолт onBackPress) пропускает событие хосту/Activity.
    val backShift = remember { Animatable(0f) }
    val predictiveBackMaxShiftPx = with(density) { config.predictiveBackMaxShift.toPx() }
    PredictiveBackHandler(enabled = state.isVisible && config.dismiss.onBackPress) { progress ->
        try {
            progress.collect { backEvent -> backShift.snapTo(backEvent.progress * predictiveBackMaxShiftPx) }
            state.onDismissRequest()
            backShift.snapTo(0f)
        } catch (exception: CancellationException) {
            backShift.animateTo(0f, NativeSheetSpring)
            throw exception
        }
    }
    // Поворот/resize: снап высоты к якорю (one-shot по размерам, не snapshotFlow — иначе пере-подписал бы коллекторы).
    LaunchedEffect(state, screenHeightPx, statusBarPx) { state.snapToCurrentAnchor() }

    // Один SideEffect: конфиг закрытия/физика + не-suspend launcher поверх suspend onDismissRequest + IME-контроллеры в scope.
    SideEffect {
        state.dismissOnSwipeDown = config.dismiss.onSwipeDown
        state.flingVelocityThresholdPxPerSec = config.flingVelocityThresholdPxPerSec
        state.resistanceMaxPx = config.resistanceMaxPx
        state.onDismissRequest = { dismissScope.launch { currentOnDismiss() } }
        // Live-IME + конфиг-флаг промоушена (стейт читает в shouldPromoteForIme без копий); alwaysFullScreenOnIme = StayUnderKeyboard+bottom.
        state.keyboardState = keyboardState
        state.alwaysFullScreenOnIme =
            config.keyboard.bottomBehavior == BottomKeyboardBehavior.StayUnderKeyboard && bottom != null
        sheetScope.keyboardController = keyboardController
        sheetScope.focusManager = focusManager
    }
    // Единственный потребитель канала жестов (drag/settle по порядку), один на весь лист.
    LaunchedEffect(state) { state.processGestures() }

    val nestedScrollConnection = remember(state) {
        SheetNestedScrollConnection(state = state, enabledState = interactionsEnabledState)
    }
    Box(modifier = Modifier.fillMaxSize().then(modifier)) {
        SheetScrim(
            state = state,
            overlayBackground = config.overlayBackground,
            scrimColor = scrimColor,
            dismissOnOutsideTap = config.dismiss.onOutsideTap,
            scrimFadeDistancePx = scrimFadeDistancePx,
            onDismissRequest = { state.onDismissRequest() },
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
            shape = config.shape,
            sheetBackgroundColor = sheetBackgroundColor,
            handleThemeColor = handleThemeColor,
            handleStaticColor = handleStaticColor,
            dragHandleTopPadding = config.dragHandleTopPadding,
            dragHandleSize = config.dragHandleSize,
            interactionsEnabled = interactionsEnabled,
            nestedScrollConnection = nestedScrollConnection,
            keyboardState = keyboardState,
            isFullScreen = isFullScreen,
            bottomKeyboardBehavior = config.keyboard.bottomBehavior,
            additionalTopFraction = additionalTopFraction,
            additionalTopConfig = config.additionalTop,
            additionalTopOverlap = config.additionalTopOverlap,
            // Слоты биндятся к ОДНОМУ scope литералом (detect/place-копии тела получают тот же sheetScope; requestDismiss из detect недостижим — не placed).
            additionalTop = additionalTop?.let { slot -> @Composable { sheetScope.slot() } },
            top = top?.let { slot -> @Composable { sheetScope.slot() } },
            bottom = bottom?.let { slot -> @Composable { sheetScope.slot() } },
            middle = { if (state.isLoading) sheetScope.PresetLoader() else sheetScope.middle() },
            // backShift.value читается в draw-фазе (graphicsLayer); скрим — отдельный узел выше, не двигается.
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .then(widthModifier)
                .graphicsLayer { translationY = backShift.value },
        )
    }
}
