package org.xplatform.uikit.compose.modifier.keyboard.shrink

import android.content.ContextWrapper
import android.view.View
import android.view.ViewTreeObserver
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.tween
import androidx.compose.runtime.State
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.Measurable
import androidx.compose.ui.layout.MeasureResult
import androidx.compose.ui.layout.MeasureScope
import androidx.compose.ui.layout.registerOnLayoutRectChanged
import androidx.compose.ui.node.CompositionLocalConsumerModifierNode
import androidx.compose.ui.node.DelegatableNode.RegistrationHandle
import androidx.compose.ui.node.LayoutModifierNode
import androidx.compose.ui.node.currentValueOf
import androidx.compose.ui.node.invalidateMeasurement
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Density
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsAnimationCompat
import androidx.core.view.WindowInsetsCompat
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import org.xplatform.uikit.compose.modifier.keyboard.adjustment.isLessThan30Api
import org.xplatform.uikit.compose.modifier.keyboard.lift.KeyboardLiftState
import kotlin.math.roundToInt

@ExperimentalComposeUiApi
internal class KeyboardShrinkNode(
    private var softInputMode: Int?,
    private var easing: Easing,
    private var durationMillis: Int,
    private var additionalShrink: Density.() -> Float,
    private var keyboardState: State<KeyboardLiftState>?,
) : Modifier.Node(),
    LayoutModifierNode,
    CompositionLocalConsumerModifierNode {

    private var currentTranslationY = 0f
    private var lastTarget = 0f
    private var bottomY: Int? = null
    private var layoutHandle: RegistrationHandle? = null
    private var globalLayoutListener: ViewTreeObserver.OnGlobalLayoutListener? = null
    private var registeredViewTreeObserver: ViewTreeObserver? = null
    private var animationJob: Job? = null
    private var keyboardObserverJob: Job? = null
    private var windowFocusListener: ViewTreeObserver.OnWindowFocusChangeListener? = null
    private var insetsAnimationView: View? = null
    private var imeAnimating = false
    private val animatable = Animatable(0f)

    override fun onAttach() {
        super.onAttach()
        applySoftInputMode()
        layoutHandle = registerOnLayoutRectChanged(0, 0) { info ->
            val screenHeight = currentValueOf(LocalWindowInfo).containerSize.height
            bottomY = screenHeight - info.boundsInRoot.bottom
            readAndHandleKeyboardState()
        }
        startKeyboardObservation()
    }

    override fun onDetach() {
        restoreSoftInputMode()
        layoutHandle?.unregister()
        layoutHandle = null
        stopKeyboardObservation()
        animationJob?.cancel()
        animationJob = null
        bottomY = null
        super.onDetach()
    }

    override fun MeasureScope.measure(measurable: Measurable, constraints: Constraints): MeasureResult {
        val isBounded = constraints.maxHeight != Constraints.Infinity
        val childConstraints = when {
            isBounded -> {
                val shrinkPx = currentTranslationY.roundToInt().coerceIn(0, constraints.maxHeight)
                val childMaxHeight = (constraints.maxHeight - shrinkPx).coerceAtLeast(0)
                constraints.copy(minHeight = 0, maxHeight = childMaxHeight)
            }
            else -> constraints
        }
        val placeable = measurable.measure(childConstraints)
        val nodeHeight = when {
            isBounded -> constraints.maxHeight
            else -> placeable.height
        }
        return layout(placeable.width, nodeHeight) {
            placeable.place(0, 0)
        }
    }

    fun update(
        newSoftInputMode: Int?,
        newEasing: Easing,
        newDurationMillis: Int,
        newAdditionalShrink: Density.() -> Float,
        newKeyboardState: State<KeyboardLiftState>?,
    ) {
        val softInputModeChanged = softInputMode != newSoftInputMode
        val keyboardStateChanged = keyboardState !== newKeyboardState
        easing = newEasing
        durationMillis = newDurationMillis
        additionalShrink = newAdditionalShrink
        keyboardState = newKeyboardState
        if (softInputModeChanged) {
            restoreSoftInputMode()
            softInputMode = newSoftInputMode
            applySoftInputMode()
        }
        if (keyboardStateChanged && isAttached) {
            stopKeyboardObservation()
            startKeyboardObservation()
        }
        if (isAttached) {
            readAndHandleKeyboardState()
        }
    }

    private fun startKeyboardObservation() {
        when (keyboardState) {
            null -> attachGlobalLayoutListener()
            else -> observeKeyboardState()
        }
    }

    private fun stopKeyboardObservation() {
        detachGlobalLayoutListener()
        keyboardObserverJob?.cancel()
        keyboardObserverJob = null
    }

    private fun observeKeyboardState() {
        val state = keyboardState ?: return
        keyboardObserverJob = coroutineScope.launch {
            snapshotFlow { state.value }
                .distinctUntilChanged()
                .collect { liftState ->
                    handleKeyboardChange(liftState.isKeyboardVisible, liftState.keyboardHeight)
                }
        }
    }

    private fun readAndHandleKeyboardState() {
        if (imeAnimating) return
        val rootView = currentValueOf(LocalView).rootView
        val insets = ViewCompat.getRootWindowInsets(rootView)
        val isKeyboardVisible = (insets?.isVisible(WindowInsetsCompat.Type.ime()) ?: false) && rootView.hasWindowFocus()
        val keyboardHeight = when {
            isKeyboardVisible -> insets.getInsets(WindowInsetsCompat.Type.ime()).bottom.toFloat() ?: 0f
            else -> 0f
        }
        handleKeyboardChange(isKeyboardVisible, keyboardHeight)
    }

    private fun handleKeyboardChange(isKeyboardVisible: Boolean, keyboardHeight: Float) {
        val target = when {
            !isShrinkEnabled() -> 0f
            !isKeyboardVisible -> 0f
            else -> {
                val y = bottomY ?: return
                val extraShrink = currentValueOf(LocalDensity).additionalShrink()
                (keyboardHeight - y + extraShrink).coerceAtLeast(0f)
            }
        }
        if (target == lastTarget) return
        lastTarget = target
        val useSyncPath = !isLessThan30Api()
        when {
            useSyncPath -> snapToTarget(target)
            else -> animateToTarget(target)
        }
    }

    private fun isShrinkEnabled(): Boolean {
        val mode = softInputMode ?: return true
        return mode == WindowManager.LayoutParams.SOFT_INPUT_ADJUST_NOTHING
    }

    private fun resolveActivity(): ComponentActivity? {
        var context = currentValueOf(LocalView).context
        while (context is ContextWrapper) {
            if (context is ComponentActivity) return context
            context = context.baseContext
        }
        return null
    }

    private fun applySoftInputMode() {
        val mode = softInputMode ?: return
        val window = resolveActivity()?.window ?: return
        SoftInputModeGuard.acquire(window, mode)
    }

    private fun restoreSoftInputMode() {
        if (softInputMode == null) return
        val window = resolveActivity()?.window ?: return
        SoftInputModeGuard.release(window)
    }

    private fun attachGlobalLayoutListener() {
        val rootView = currentValueOf(LocalView).rootView
        val layoutListener = ViewTreeObserver.OnGlobalLayoutListener { readAndHandleKeyboardState() }
        val focusListener = ViewTreeObserver.OnWindowFocusChangeListener { readAndHandleKeyboardState() }
        val currentViewTreeObserver = rootView.viewTreeObserver
        currentViewTreeObserver.addOnGlobalLayoutListener(layoutListener)
        currentViewTreeObserver.addOnWindowFocusChangeListener(focusListener)
        globalLayoutListener = layoutListener
        windowFocusListener = focusListener
        registeredViewTreeObserver = currentViewTreeObserver
        attachInsetsAnimationCallback()
    }

    private fun detachGlobalLayoutListener() {
        val savedViewTreeObserver = registeredViewTreeObserver
        when {
            savedViewTreeObserver != null && savedViewTreeObserver.isAlive -> {
                globalLayoutListener?.let { savedViewTreeObserver.removeOnGlobalLayoutListener(it) }
                windowFocusListener?.let { savedViewTreeObserver.removeOnWindowFocusChangeListener(it) }
            }
        }
        globalLayoutListener = null
        windowFocusListener = null
        registeredViewTreeObserver = null
        detachInsetsAnimationCallback()
    }

    private fun attachInsetsAnimationCallback() {
        val rootView = currentValueOf(LocalView).rootView
        val animationCallback = object : WindowInsetsAnimationCompat.Callback(DISPATCH_MODE_CONTINUE_ON_SUBTREE) {
            override fun onPrepare(animation: WindowInsetsAnimationCompat) {
                if (animation.typeMask and WindowInsetsCompat.Type.ime() != 0) imeAnimating = true
            }
            override fun onProgress(
                insets: WindowInsetsCompat,
                runningAnimations: MutableList<WindowInsetsAnimationCompat>,
            ): WindowInsetsCompat {
                val isKeyboardVisible = insets.isVisible(WindowInsetsCompat.Type.ime()) && rootView.hasWindowFocus()
                val keyboardHeight = when {
                    isKeyboardVisible -> insets.getInsets(WindowInsetsCompat.Type.ime()).bottom.toFloat()
                    else -> 0f
                }
                handleKeyboardChange(isKeyboardVisible, keyboardHeight)
                return insets
            }
            override fun onEnd(animation: WindowInsetsAnimationCompat) {
                if (animation.typeMask and WindowInsetsCompat.Type.ime() != 0) imeAnimating = false
                readAndHandleKeyboardState()
            }
        }
        ViewCompat.setWindowInsetsAnimationCallback(rootView, animationCallback)
        insetsAnimationView = rootView
    }

    private fun detachInsetsAnimationCallback() {
        val savedView = insetsAnimationView ?: return
        ViewCompat.setWindowInsetsAnimationCallback(savedView, null)
        insetsAnimationView = null
        imeAnimating = false
    }

    private fun snapToTarget(targetValue: Float) {
        animationJob?.cancel()
        animationJob = coroutineScope.launch {
            animatable.snapTo(targetValue)
            currentTranslationY = targetValue
            invalidateMeasurement()
        }
    }

    private fun animateToTarget(targetValue: Float) {
        animationJob?.cancel()
        animationJob = coroutineScope.launch {
            animatable.animateTo(
                targetValue = targetValue,
                animationSpec = tween(durationMillis = durationMillis, easing = easing)
            ) {
                currentTranslationY = value
                invalidateMeasurement()
            }
        }
    }
}
