package org.xplatform.uikit.compose.modifier.keyboard.adjustment

import android.content.ContextWrapper
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
import androidx.compose.ui.node.invalidatePlacement
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.unit.Constraints
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import org.xplatform.uikit.compose.modifier.keyboard.lift.KeyboardLiftState

@ExperimentalComposeUiApi
internal class KeyboardAdjustmentNode(
    private var shouldChangeSoftInputMode: Boolean,
    private var easing: Easing,
    private var durationMillis: Int,
    private var additionalOffsetPx: Float,
    private var keyboardState: State<KeyboardLiftState>?,
) : Modifier.Node(),
    LayoutModifierNode,
    CompositionLocalConsumerModifierNode {

    private var currentTranslationY = 0f
    private var bottomY: Int? = null
    private var softInputMode = 0
    private var layoutHandle: RegistrationHandle? = null
    private var globalLayoutListener: ViewTreeObserver.OnGlobalLayoutListener? = null
    private var registeredViewTreeObserver: ViewTreeObserver? = null
    private var animationJob: Job? = null
    private var keyboardObserverJob: Job? = null
    private val animatable = Animatable(0f)

    override fun onAttach() {
        super.onAttach()
        applySoftInputMode()
        layoutHandle = registerOnLayoutRectChanged(0, 0) { info ->
            val screenHeight = currentValueOf(LocalWindowInfo).containerSize.height
            bottomY = screenHeight - info.boundsInRoot.bottom
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
        val placeable = measurable.measure(constraints)
        return layout(placeable.width, placeable.height) {
            placeable.placeWithLayer(0, 0) {
                translationY = -currentTranslationY
            }
        }
    }

    fun update(
        newShouldChangeSoftInputMode: Boolean,
        newEasing: Easing,
        newDurationMillis: Int,
        newAdditionalOffsetPx: Float,
        newKeyboardState: State<KeyboardLiftState>?,
    ) {
        val softInputChanged = shouldChangeSoftInputMode != newShouldChangeSoftInputMode
        val keyboardStateChanged = keyboardState !== newKeyboardState
        shouldChangeSoftInputMode = newShouldChangeSoftInputMode
        easing = newEasing
        durationMillis = newDurationMillis
        additionalOffsetPx = newAdditionalOffsetPx
        keyboardState = newKeyboardState
        if (softInputChanged) {
            restoreSoftInputMode()
            applySoftInputMode()
        }
        if (keyboardStateChanged && isAttached) {
            stopKeyboardObservation()
            startKeyboardObservation()
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

    private fun handleKeyboardChange(isKeyboardVisible: Boolean, keyboardHeight: Float) {
        bottomY?.let { y ->
            val target = when {
                isKeyboardVisible -> (keyboardHeight - y - additionalOffsetPx).coerceAtLeast(0f)
                else -> 0f
            }
            val useSyncPath = !isLessThan30Api() && shouldChangeSoftInputMode
            when {
                useSyncPath -> snapToTarget(target)
                else -> animateToTarget(target)
            }
        }
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
        if (!shouldChangeSoftInputMode) return
        val activity = resolveActivity() ?: return
        val window = activity.window
        softInputMode = window.attributes.softInputMode
        window.setSoftInputMode(
            when {
                isLessThan30Api() -> WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE
                else -> WindowManager.LayoutParams.SOFT_INPUT_ADJUST_NOTHING
            }
        )
    }

    private fun restoreSoftInputMode() {
        if (!shouldChangeSoftInputMode) return
        resolveActivity()?.window?.setSoftInputMode(softInputMode)
    }

    private fun attachGlobalLayoutListener() {
        val rootView = currentValueOf(LocalView).rootView
        val listener = ViewTreeObserver.OnGlobalLayoutListener {
            val insets = ViewCompat.getRootWindowInsets(rootView)
            val isKeyboardVisible = insets?.isVisible(WindowInsetsCompat.Type.ime()) ?: false
            val keyboardHeight = insets?.getInsets(WindowInsetsCompat.Type.ime())?.bottom?.toFloat() ?: 0f
            handleKeyboardChange(isKeyboardVisible, keyboardHeight)
        }
        val currentViewTreeObserver = rootView.viewTreeObserver
        currentViewTreeObserver.addOnGlobalLayoutListener(listener)
        globalLayoutListener = listener
        registeredViewTreeObserver = currentViewTreeObserver
    }

    private fun detachGlobalLayoutListener() {
        val listener = globalLayoutListener ?: return
        val savedViewTreeObserver = registeredViewTreeObserver
        when {
            savedViewTreeObserver != null && savedViewTreeObserver.isAlive ->
                savedViewTreeObserver.removeOnGlobalLayoutListener(listener)
        }
        globalLayoutListener = null
        registeredViewTreeObserver = null
    }

    private fun snapToTarget(targetValue: Float) {
        animationJob?.cancel()
        animationJob = coroutineScope.launch {
            animatable.snapTo(targetValue)
            currentTranslationY = targetValue
            invalidatePlacement()
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
                invalidatePlacement()
            }
        }
    }
}
