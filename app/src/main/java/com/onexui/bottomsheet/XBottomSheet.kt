package com.onexui.bottomsheet

import androidx.activity.compose.BackHandler
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
import com.onexui.bottomsheet.state.SheetValue
import com.onexui.bottomsheet.state.XBottomSheetState
import kotlinx.coroutines.launch
import org.xplatform.uikit.compose.modifier.keyboard.lift.rememberKeyboardLiftState

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
    // onDismissRequest — suspend; компонент вызывает её через свой scope. Поле state.onDismissRequest остаётся
    // не-suspend launcher'ом (см. SideEffect): скрим/settle/back/requestDismiss зовут его синхронно.
    val dismissScope = rememberCoroutineScope()
    val currentOnDismiss by rememberUpdatedState(onDismissRequest)
    val sheetScope = remember(state) { XBottomSheetScopeImpl(state) }
    // Размеры экрана из LocalWindowInfo.containerSize (px); при повороте обновляется сам.
    val containerSize = LocalWindowInfo.current.containerSize
    val screenHeightPx = containerSize.height
    val screenWidthDp = with(density) { containerSize.width.toDp() }
    val statusBarPx = WindowInsets.statusBars.getTop(density)
    // navBar inset: фон Surface остаётся edge-to-edge под баром, контент паддится инсетом внутри (bottom над баром).
    val navBarPx = WindowInsets.navigationBars.getBottom(density)
    // Loading-якорь = 192dp + navBarPx: видимая зона Loader'а остаётся 192dp несмотря на nav-bar-паддинг внутри.
    val loadingSheetHeightPx = with(density) { XBottomSheetDefaults.LoadingSheetHeight.roundToPx() } + navBarPx
    val scrimFadeDistancePx = with(density) { XBottomSheetDefaults.ScrimFadeDistance.toPx() }
    // Цвета резолвятся в композиции корня (тема доступна): Unspecified → дефолт. Вниз идут готовые Color-примитивы.
    val scrimColor = config.colors.resolveScrim()
    val sheetBackgroundColor = config.colors.resolveSheetBackground()
    val handleThemeColor = config.colors.resolveHandleTheme()
    val handleStaticColor = config.colors.resolveHandleStatic()
    val isWide = screenWidthDp >= XBottomSheetDefaults.WideScreenThreshold
    val widthModifier = if (isWide) Modifier.width(XBottomSheetDefaults.MaxWidth) else Modifier.fillMaxWidth()
    // Единый источник состояния IME для авто-FullScreen и модификаторов подъёма/сжатия.
    val keyboardState = rememberKeyboardLiftState()
    // Жесты высоты отключены: dragHandle==null, Loading, ИЛИ открыта IME. derivedStateOf уведомляет читателей
    // только при фактической смене (show/hide IME, isLoading).
    val interactionsEnabledState = remember(config.dragHandle) {
        derivedStateOf { config.dragHandle != null && !state.isLoading && !keyboardState.value.isKeyboardVisible }
    }
    val interactionsEnabled by interactionsEnabledState
    val isFullScreen = state.currentValue == SheetValue.ExpandedFullScreen
    // Фракция Additional Top: 1 = Expanded, 0 = скрыта. Animatable (не animate*AsState): .value читается только
    // в measure → инвалидация layout, не композиции. Старт — по восстановленному additionalTopState.
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

    // onSheetHidden роняет IME: иначе keyboard-lift оффсет продолжал бы поднимать контейнер и лист «улетел» бы
    // вверх, отцепившись от нижней кромки. alwaysFullScreenOnIme для StayUnderKeyboard — bottom должен доставать
    // до нижней кромки экрана, под клавиатуру.
    ObserveSheetState(
        state = state,
        keyboardState = keyboardState,
        alwaysFullScreenOnIme = config.keyboard.bottomBehavior == BottomKeyboardBehavior.StayUnderKeyboard && bottom != null,
        onSheetHidden = { sheetScope.hideKeyboard() },
    )
    // Back-press закрывает лист при dismiss.onBackPress; дефолт false → BackHandler(enabled=false) пропускает
    // событие хосту/Activity (1:1).
    BackHandler(enabled = state.isVisible && config.dismiss.onBackPress) { state.onDismissRequest() }
    // Поворот/resize: снап высоты к якорю (one-shot по размерам, не snapshotFlow — иначе пере-подписал бы коллекторы).
    LaunchedEffect(state, screenHeightPx, statusBarPx) { state.snapToCurrentAnchor() }

    // Конфиг закрытия + не-suspend launcher поверх suspend onDismissRequest (settle/скрим/back/requestDismiss зовут
    // синхронно) + проброс IME-контроллеров в scope — всё в стейт/scope одним SideEffect'ом.
    SideEffect {
        state.dismissOnSwipeDown = config.dismiss.onSwipeDown
        state.onDismissRequest = { dismissScope.launch { currentOnDismiss() } }
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
            sheetBackgroundColor = sheetBackgroundColor,
            handleThemeColor = handleThemeColor,
            handleStaticColor = handleStaticColor,
            interactionsEnabled = interactionsEnabled,
            nestedScrollConnection = nestedScrollConnection,
            keyboardState = keyboardState,
            isFullScreen = isFullScreen,
            bottomKeyboardBehavior = config.keyboard.bottomBehavior,
            additionalTopFraction = additionalTopFraction,
            additionalTopConfig = config.additionalTop,
            // Слоты биндятся к ОДНОМУ инстансу scope литералом в параметр (detect/place-копии тела получают тот же
            // sheetScope; requestDismiss из detect-копии недостижим — она не placed).
            additionalTop = additionalTop?.let { slot -> @Composable { sheetScope.slot() } },
            top = top?.let { slot -> @Composable { sheetScope.slot() } },
            bottom = bottom?.let { slot -> @Composable { sheetScope.slot() } },
            middle = { sheetScope.middle() },
            modifier = Modifier.align(Alignment.BottomCenter).then(widthModifier),
        )
    }
}
