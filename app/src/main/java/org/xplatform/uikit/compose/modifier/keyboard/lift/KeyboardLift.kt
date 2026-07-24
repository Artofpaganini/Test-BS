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
import androidx.core.view.WindowInsetsAnimationCompat
import androidx.core.view.WindowInsetsCompat

data class KeyboardLiftState(
    val isKeyboardVisible: Boolean,
    val keyboardHeight: Float,
    val targetKeyboardHeightPx: Float,
)

private val imeInsetType = WindowInsetsCompat.Type.ime()

/**
 * Модификатор для получения актуальных данных о состоянии клавиатуры.
 *
 * keyboardHeight - анимированная по-кадрово высота IME (API >= 30 через WindowInsetsAnimationCompat.Callback,
 * иначе установившееся значение через OnGlobalLayout). isKeyboardVisible - запрошенная видимость (переключается
 * в начале show/hide), развязана с высотой: высоту НЕ обнуляем по булеану. targetKeyboardHeightPx - целевая
 * высота полностью открытой клавиатуры (для расчёта фракции анимации). Значение НЕ читается в фазе композиции:
 * коллбеки пишут в State, а читатели работают в layout/coroutine.
 */
@Composable
fun rememberKeyboardLiftState(
    currentView: View = LocalView.current,
): State<KeyboardLiftState> {
    val keyboardLift =
        remember(currentView) { mutableStateOf(KeyboardLiftState(false, 0f, 0f)) }

    DisposableEffect(currentView) {
        val rootView = currentView.rootView
        var isImeAnimating = false
        var targetKeyboardHeightPx = 0f

        fun pushState(isRequestedVisible: Boolean, heightPx: Float) {
            val isVisible = isRequestedVisible && rootView.hasWindowFocus()
            val newState = KeyboardLiftState(
                isKeyboardVisible = isVisible,
                keyboardHeight = heightPx,
                targetKeyboardHeightPx = targetKeyboardHeightPx,
            )
            if (keyboardLift.value != newState) {
                keyboardLift.value = newState
            }
        }

        fun readSettledState() {
            val insets = ViewCompat.getRootWindowInsets(rootView) ?: return
            val isVisible = insets.isVisible(imeInsetType)
            val heightPx = insets.getInsets(imeInsetType).bottom.toFloat()
            if (isVisible && heightPx > 0f) targetKeyboardHeightPx = heightPx
            pushState(isVisible, heightPx)
        }

        val globalListener = ViewTreeObserver.OnGlobalLayoutListener {
            if (isImeAnimating) return@OnGlobalLayoutListener
            readSettledState()
        }
        val animationCallback = object : WindowInsetsAnimationCompat.Callback(
            DISPATCH_MODE_CONTINUE_ON_SUBTREE,
        ) {
            override fun onPrepare(animation: WindowInsetsAnimationCompat) {
                if (animation.typeMask and imeInsetType != 0) isImeAnimating = true
            }

            override fun onStart(
                animation: WindowInsetsAnimationCompat,
                bounds: WindowInsetsAnimationCompat.BoundsCompat,
            ): WindowInsetsAnimationCompat.BoundsCompat {
                if (animation.typeMask and imeInsetType != 0) {
                    val upperHeightPx = bounds.upperBound.bottom.toFloat()
                    if (upperHeightPx > 0f) targetKeyboardHeightPx = upperHeightPx
                }
                return bounds
            }

            override fun onProgress(
                insets: WindowInsetsCompat,
                runningAnimations: MutableList<WindowInsetsAnimationCompat>,
            ): WindowInsetsCompat {
                pushState(
                    isRequestedVisible = insets.isVisible(imeInsetType),
                    heightPx = insets.getInsets(imeInsetType).bottom.toFloat(),
                )
                return insets
            }

            override fun onEnd(animation: WindowInsetsAnimationCompat) {
                if (animation.typeMask and imeInsetType != 0) isImeAnimating = false
                readSettledState()
            }
        }

        val viewTreeObserver = rootView.viewTreeObserver
        viewTreeObserver.addOnGlobalLayoutListener(globalListener)
        ViewCompat.setWindowInsetsAnimationCallback(rootView, animationCallback)
        onDispose {
            if (viewTreeObserver.isAlive) {
                viewTreeObserver.removeOnGlobalLayoutListener(globalListener)
            }
            ViewCompat.setWindowInsetsAnimationCallback(rootView, null)
        }
    }
    return keyboardLift
}
