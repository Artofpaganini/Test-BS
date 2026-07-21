package com.onexui.bottomsheet.layout

import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import com.onexui.bottomsheet.config.BottomKeyboardBehavior
import com.onexui.bottomsheet.handle.DragHandle
import com.onexui.bottomsheet.handle.DragHandleStyle
import com.onexui.bottomsheet.presets.PresetLoader
import com.onexui.bottomsheet.state.XBottomSheetState
import org.xplatform.uikit.compose.modifier.keyboard.lift.KeyboardLiftState

// Тело листа: Surface(20/20/0/0) с top(sticky) / middle(scroll) / bottom(sticky). DragHandle рисуется поверх
// (TopCenter) и вёрстку не двигает.
@Composable
internal fun SheetBody(
    state: XBottomSheetState,
    dragHandle: DragHandleStyle?,
    sheetBackgroundColor: Color,
    handleThemeColor: Color,
    handleStaticColor: Color,
    keyboardState: State<KeyboardLiftState>,
    isFullScreen: Boolean,
    bottomKeyboardBehavior: BottomKeyboardBehavior,
    navBarPx: Int,
    fillHeight: Boolean,
    top: (@Composable () -> Unit)?,
    bottom: (@Composable () -> Unit)?,
    middle: @Composable () -> Unit,
) {
    // StayUnderKeyboard активен только при наличии bottom-слота.
    val isBottomUnderKeyboardMode = bottomKeyboardBehavior == BottomKeyboardBehavior.StayUnderKeyboard && bottom != null
    // fillHeight (place): тело заполняет offset (фон на всю высоту, нет дыры снизу). !fillHeight (detect): wrap по контенту.
    val sizeModifier = if (fillHeight) Modifier.fillMaxSize() else Modifier.fillMaxWidth()
    // Middle без нашего verticalScroll — скролл предоставляет контент. weight(1f, fill=false): короткий контент
    // wrap, ленивый/скролл заполняет. Loading → Loader.
    val middleContent: @Composable () -> Unit = {
        if (state.isLoading) {
            PresetLoader()
        } else {
            middle()
        }
    }
    // В detect тело wrap'ится (fillMaxWidth, без fillMaxHeight) — измеритель видит натуральную высоту контента;
    // fillMaxSize сломал бы wrap-детект.
    val sheetSurface: @Composable () -> Unit = {
        SheetSurface(modifier = sizeModifier, backgroundColor = sheetBackgroundColor) {
            if (isBottomUnderKeyboardMode) {
                // bottom прижат к нижней кромке и уходит ПОД клавиатуру (StayUnderKeyboardContent); top — над регионом.
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
            // No-op гаситель тапов по телу: consume up → тап не проваливается на scrim и не закрывает лист.
            // Драги проходят (detectTapGestures отменяется по slop).
            .pointerInput(Unit) { detectTapGestures {} },
    ) {
        // Тело = только Surface. DragHandle у верхней кромки (ниже карточки Additional Top).
        sheetSurface()
        if (dragHandle != null) {
            DragHandle(
                style = dragHandle,
                themeColor = handleThemeColor,
                staticColor = handleStaticColor,
                modifier = Modifier.align(Alignment.TopCenter),
            )
        }
    }
}
