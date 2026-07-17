package org.xplatform.uikit.compose.modifier.keyboard.lift

import android.view.View
import android.view.ViewTreeObserver
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalView
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat

data class KeyboardLiftState(
    val isKeyboardVisible: Boolean,
    val keyboardHeight: Float
)

/**
 * Модификатор для получения актуальных данных о состоянии клавиатуры
 */
@Composable
fun rememberKeyboardLiftState(
    currentView: View = LocalView.current,
): State<KeyboardLiftState> {
    val keyboardLift =
        remember(currentView) { mutableStateOf(KeyboardLiftState(false, 0f)) }

    DisposableEffect(currentView) {
        val rootView = currentView.rootView
        val viewTreeObserver = rootView.viewTreeObserver
        val globalListener = ViewTreeObserver.OnGlobalLayoutListener {
            val insetsOrNull = ViewCompat.getRootWindowInsets(rootView)
            insetsOrNull?.let { inset ->
                val isKeyboardVisible = inset.isVisible(WindowInsetsCompat.Type.ime())
                val keyboardBottom = inset.getInsets(WindowInsetsCompat.Type.ime()).bottom.toFloat()
                val isEffectivelyVisible = isKeyboardVisible && rootView.hasWindowFocus()
                val newState = KeyboardLiftState(
                    isKeyboardVisible = isEffectivelyVisible,
                    keyboardHeight = if (isEffectivelyVisible) keyboardBottom else 0f
                )
                if (keyboardLift.value != newState) {
                    keyboardLift.value = newState
                }
            }
        }
        viewTreeObserver.addOnGlobalLayoutListener(globalListener)
        onDispose {
            if (viewTreeObserver.isAlive) {
                viewTreeObserver.removeOnGlobalLayoutListener(globalListener)
            }
        }
    }
    return keyboardLift
}
