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
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpSize
import com.onexui.bottomsheet.config.BottomKeyboardBehavior
import com.onexui.bottomsheet.handle.DragHandle
import com.onexui.bottomsheet.handle.DragHandleStyle
import org.xplatform.uikit.compose.modifier.keyboard.lift.KeyboardLiftState

@Composable
internal fun SheetBody(
    dragHandle: DragHandleStyle?,
    shape: Shape,
    sheetBackgroundColor: Color,
    handleThemeColor: Color,
    handleStaticColor: Color,
    dragHandleTopPadding: Dp,
    dragHandleSize: DpSize,
    keyboardState: State<KeyboardLiftState>,
    isFullScreen: Boolean,
    bottomKeyboardBehavior: BottomKeyboardBehavior,
    navBarPx: Int,
    isFillHeight: Boolean,
    top: (@Composable () -> Unit)?,
    bottom: (@Composable () -> Unit)?,
    middle: @Composable () -> Unit,
) {
    val isBottomUnderKeyboardMode = bottomKeyboardBehavior == BottomKeyboardBehavior.StayUnderKeyboard && bottom != null
    val sizeModifier = if (isFillHeight) Modifier.fillMaxSize() else Modifier.fillMaxWidth()
    val sheetSurface: @Composable () -> Unit = {
        SheetSurface(modifier = sizeModifier, shape = shape, backgroundColor = sheetBackgroundColor) {
            if (isBottomUnderKeyboardMode) {
                Column(modifier = sizeModifier) {
                    top?.invoke()
                    StayUnderKeyboardContent(
                        modifier = (if (isFillHeight) Modifier.weight(1f) else Modifier.weight(1f, fill = false))
                            .fillMaxWidth(),
                        keyboardState = keyboardState,
                        navBarPx = navBarPx,
                        middle = middle,
                        bottom = bottom,
                    )
                }
            } else {
                LiftContent(
                    modifier = sizeModifier,
                    keyboardState = keyboardState,
                    isFullScreen = isFullScreen,
                    navBarPx = navBarPx,
                    isFillHeight = isFillHeight,
                    top = top,
                    middle = middle,
                    bottom = bottom,
                )
            }
        }
    }
    Box(
        modifier = sizeModifier
            .pointerInput(Unit) { detectTapGestures {} },
    ) {
        sheetSurface()
        if (dragHandle != null) {
            DragHandle(
                style = dragHandle,
                themeColor = handleThemeColor,
                staticColor = handleStaticColor,
                topPadding = dragHandleTopPadding,
                size = dragHandleSize,
                modifier = Modifier.align(Alignment.TopCenter),
            )
        }
    }
}
