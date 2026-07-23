package com.onexui.bottomsheet.state

import androidx.compose.animation.core.Animatable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import com.onexui.bottomsheet.NativeSheetSpring
import com.onexui.bottomsheet.additionaltop.AdditionalTopState
import com.onexui.bottomsheet.gesture.resistedOvershoot
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import org.xplatform.uikit.compose.modifier.keyboard.lift.KeyboardLiftState
import kotlin.math.roundToInt
import kotlin.time.Duration.Companion.milliseconds

@Stable
internal class XBottomSheetState internal constructor(
    skipCollapsed: Boolean,
    val initialLoading: Boolean,
    peekFraction: Float,
    anchors: List<XSheetAnchor>,
) {
    private var accumulatedOvershootPx = 0f

    private var anchorTable: SheetAnchorTable? = null

    private var imePromotedFrom: SheetValue? = null

    private var dismissScope: CoroutineScope? = null

    private var dismissRequest: State<suspend () -> Unit>? = null

    private val gestureCommands = Channel<GestureCommand>(Channel.UNLIMITED)

    var skipCollapsed: Boolean by mutableStateOf(skipCollapsed)
    var peekFraction: Float by mutableStateOf(peekFraction)
    var anchors: List<XSheetAnchor> by mutableStateOf(anchors)
        private set

    var currentValue: SheetValue by mutableStateOf(SheetValue.Hidden)
        private set

    var isLoading by mutableStateOf(initialLoading)
        private set

    var additionalTopState by mutableStateOf(AdditionalTopState.Expanded)

    internal var metrics by mutableStateOf<SheetMetrics?>(null)
        private set

    internal val offset = Animatable(0f)

    internal var isDragging by mutableStateOf(false)
        private set

    internal var keyboardState: State<KeyboardLiftState>? = null
        private set

    internal var alwaysFullScreenOnIme: Boolean = false
        private set

    internal var dismissOnSwipeDown: Boolean = true
        private set

    internal var flingVelocityThresholdPxPerSec: Float = 0f
        private set

    internal var resistanceMaxPx: Float = 0f
        private set

    val isVisible: Boolean get() = currentValue != SheetValue.Hidden

    val isAnimating: Boolean get() = offset.isRunning

    /** Меняет набор кастомных rest-якорей той же DSL-грамматикой, что и билдер (`"half" at 0.5f`). */
    fun anchors(configure: XSheetAnchorsBuilder.() -> Unit) {
        anchors = XSheetAnchorsBuilder().apply(configure).build()
    }

    /** Открывает лист: в Loading — к Loader-якорю, иначе к openTarget по замеру. Уже открытый не переоткрывает. */
    suspend fun show() {
        if (isVisible) return
        val measured = awaitMetrics()
        animateTo(if (isLoading) SheetValue.Loading else measured.openTarget(skipCollapsed))
    }

    /** Раскрывает лист из Collapsed в expandTarget; ручной разворот при скрытии IME не откатывается. */
    suspend fun expand() {
        val measured = metrics ?: return
        imePromotedFrom = null
        if (currentValue == SheetValue.Collapsed) animateTo(measured.expandTarget())
    }

    /** Закрывает лист — анимация высоты к Hidden. */
    suspend fun hide() {
        animateTo(SheetValue.Hidden)
    }

    /** Снимает Loading и доводит лист до рест-стейта по РЕАЛЬНОМУ контенту, дождавшись его ре-замера. */
    suspend fun markContentReady() {
        if (!isVisible) {
            isLoading = false
            return
        }
        val loaderMetrics = metrics
        isLoading = false
        val measured = withTimeoutOrNull(REMEASURE_TIMEOUT_MS.milliseconds) {
            awaitContentMetrics(loaderMetrics)
        } ?: awaitContentMetrics(loaderMetrics)
        if (!isVisible) return
        val target = measured.openTarget(skipCollapsed)
        if (shouldPromoteForIme(target, measured)) {
            imePromotedFrom = target
            animateTo(SheetValue.ExpandedFullScreen)
        } else {
            imePromotedFrom = null
            animateTo(target)
        }
    }

    internal fun restore(value: SheetValue, isLoadingSaved: Boolean, additionalTop: AdditionalTopState) {
        currentValue = value
        isLoading = isLoadingSaved
        additionalTopState = additionalTop
    }

    internal fun updateDismissOnSwipeDown(dismissOnSwipeDown: Boolean) {
        if (this.dismissOnSwipeDown == dismissOnSwipeDown) return
        this.dismissOnSwipeDown = dismissOnSwipeDown
    }

    internal fun updateFlingVelocityThreshold(flingVelocityThresholdPxPerSec: Float) {
        if (this.flingVelocityThresholdPxPerSec == flingVelocityThresholdPxPerSec) return
        this.flingVelocityThresholdPxPerSec = flingVelocityThresholdPxPerSec
    }

    internal fun updateResistanceMax(resistanceMaxPx: Float) {
        if (this.resistanceMaxPx == resistanceMaxPx) return
        this.resistanceMaxPx = resistanceMaxPx
    }

    internal fun updateAlwaysFullScreenOnIme(alwaysFullScreenOnIme: Boolean) {
        if (this.alwaysFullScreenOnIme == alwaysFullScreenOnIme) return
        this.alwaysFullScreenOnIme = alwaysFullScreenOnIme
    }

    internal fun updateKeyboardState(keyboardState: State<KeyboardLiftState>) {
        if (this.keyboardState === keyboardState) return
        this.keyboardState = keyboardState
    }

    internal fun updateDismissScope(dismissScope: CoroutineScope) {
        if (this.dismissScope === dismissScope) return
        this.dismissScope = dismissScope
    }

    internal fun updateDismissRequest(dismissRequest: State<suspend () -> Unit>) {
        if (this.dismissRequest === dismissRequest) return
        this.dismissRequest = dismissRequest
    }

    internal fun requestDismiss() {
        val scope = dismissScope ?: return
        val request = dismissRequest ?: return
        scope.launch { request.value() }
    }

    internal suspend fun onContentRemeasured() {
        val measured = metrics ?: return
        if (!isVisible || currentValue == SheetValue.Loading) return
        if (skipCollapsed && currentValue == SheetValue.Content && measured.isFillMode) {
            animateTo(SheetValue.ExpandedFullScreen)
        } else {
            offset.animateTo(measured.anchorPx(currentValue, skipCollapsed).toFloat(), NativeSheetSpring)
        }
    }

    internal suspend fun onLiveConfigChanged() {
        val current = metrics ?: return
        val rebuilt = current.copy(peekFraction = peekFraction, customAnchors = anchors)
        metrics = rebuilt
        val table = rebuilt.toAnchorTable(skipCollapsed)
        anchorTable = table
        if (!isVisible || currentValue == SheetValue.Loading || isDragging) return
        val target = resolveRestTargetAfterConfigChange(table)
        if (target != currentValue) currentValue = target
        offset.animateTo(rebuilt.anchorPx(target, skipCollapsed).toFloat(), NativeSheetSpring)
    }

    internal suspend fun onImeShown() {
        val measured = metrics ?: return
        if (!isVisible) return
        if (shouldPromoteForIme(currentValue, measured)) {
            imePromotedFrom = currentValue
            animateTo(SheetValue.ExpandedFullScreen)
        }
    }

    internal suspend fun onImeHidden() {
        val target = imePromotedFrom ?: return
        imePromotedFrom = null
        if (currentValue == SheetValue.ExpandedFullScreen) animateTo(target)
    }

    internal suspend fun snapToCurrentAnchor() {
        val measured = metrics ?: return
        if (!isVisible || offset.isRunning) return
        offset.snapTo(measured.anchorPx(currentValue, skipCollapsed).toFloat())
    }

    internal fun updateMetrics(
        screenHeightPx: Int,
        statusBarPx: Int,
        contentHeightPx: Int,
        loadingSheetHeightPx: Int,
    ) {
        val current = metrics
        if (current != null &&
            current.screenHeightPx == screenHeightPx &&
            current.statusBarPx == statusBarPx &&
            current.contentHeightPx == contentHeightPx &&
            current.loadingSheetHeightPx == loadingSheetHeightPx
        ) {
            return
        }
        val updated = SheetMetrics(
            screenHeightPx = screenHeightPx,
            statusBarPx = statusBarPx,
            contentHeightPx = contentHeightPx,
            loadingSheetHeightPx = loadingSheetHeightPx,
            peekFraction = peekFraction,
            customAnchors = anchors,
        )
        metrics = updated
        anchorTable = updated.toAnchorTable(skipCollapsed)
    }

    internal fun markDragStarted() {
        if (isDragging) return
        isDragging = true
    }

    internal fun enqueueDrag(deltaHeightPx: Float) {
        markDragStarted()
        gestureCommands.trySend(GestureCommand.Drag(deltaHeightPx))
    }

    internal fun enqueueSettle(velocity: Float) {
        gestureCommands.trySend(GestureCommand.Settle(velocity))
    }

    internal suspend fun processGestures(): Unit = coroutineScope {
        var settleJob: Job? = null
        for (command in gestureCommands) {
            when (command) {
                is GestureCommand.Drag -> {
                    settleJob?.cancelAndJoin()
                    settleJob = null
                    dragBy(command.deltaHeightPx)
                }
                is GestureCommand.Settle -> {
                    settleJob?.cancelAndJoin()
                    settleJob = launch { settle(command.velocity) }
                }
            }
        }
    }

    internal fun isOffsetAtRestAnchor(): Boolean = anchorTable?.isAtRestAnchor(offset.value) ?: false

    private suspend fun awaitMetrics(): SheetMetrics =
        metrics ?: snapshotFlow { metrics }.filterNotNull().first()

    private suspend fun awaitContentMetrics(loaderMetrics: SheetMetrics?): SheetMetrics =
        snapshotFlow { metrics }.filterNotNull().first { remeasured -> remeasured !== loaderMetrics }

    private fun resolveRestTargetAfterConfigChange(table: SheetAnchorTable): SheetValue {
        val value = currentValue
        if (value is SheetValue.Custom && anchors.none { anchor -> anchor.key == value.key }) {
            return table.settleTarget(offset.value, 0f, isDismissAllowed = false, flingVelocityThresholdPxPerSec)
                ?: value
        }
        return value
    }

    private fun shouldPromoteForIme(value: SheetValue, measured: SheetMetrics): Boolean {
        val ime = keyboardState?.value ?: return false
        if (!ime.isKeyboardVisible) return false
        if (value == SheetValue.Loading) return true
        val promotable = value == SheetValue.Content || value == SheetValue.Collapsed ||
            value == SheetValue.ExpandedContent || value is SheetValue.Custom
        if (!promotable) return false
        return alwaysFullScreenOnIme ||
            measured.anchorPx(value, skipCollapsed) + ime.keyboardHeight.roundToInt() > measured.maxHeightPx
    }

    private suspend fun dragBy(deltaHeightPx: Float) {
        val measured = metrics ?: return
        val table = anchorTable ?: return
        val floorPx = if (dismissOnSwipeDown) 0f else table.lowestRestAnchorPx.toFloat()
        val atTop = currentValue == SheetValue.ExpandedFullScreen
        val overshootBase = if (atTop) measured.maxHeightPx.toFloat() else table.highestRestAnchorPx.toFloat()
        val overshootCeiling =
            if (atTop) (measured.maxHeightPx + resistanceMaxPx) else measured.maxHeightPx.toFloat()
        val inOvershoot = accumulatedOvershootPx > 0f ||
            (offset.value >= overshootBase - OVERSHOOT_ENTER_EPS_PX && deltaHeightPx > 0f)
        if (inOvershoot) {
            accumulatedOvershootPx = (accumulatedOvershootPx + deltaHeightPx).coerceAtLeast(0f)
            val resisted = resistedOvershoot(accumulatedOvershootPx, resistanceMaxPx)
            offset.snapTo((overshootBase + resisted).coerceAtMost(overshootCeiling))
        } else {
            accumulatedOvershootPx = 0f
            offset.snapTo((offset.value + deltaHeightPx).coerceIn(floorPx, overshootBase))
        }
    }

    private suspend fun settle(velocity: Float) {
        isDragging = false
        accumulatedOvershootPx = 0f
        imePromotedFrom = null
        val table = anchorTable ?: return
        val target = table.settleTarget(offset.value, velocity, dismissOnSwipeDown, flingVelocityThresholdPxPerSec)
        when {
            target == null -> animateTo(currentValue)
            target == SheetValue.Hidden -> requestDismiss()
            else -> animateTo(target)
        }
    }

    private suspend fun animateTo(target: SheetValue) {
        currentValue = target
        val anchor = metrics?.anchorPx(target, skipCollapsed)?.toFloat() ?: 0f
        offset.animateTo(anchor, NativeSheetSpring)
    }

    private companion object {
        const val REMEASURE_TIMEOUT_MS = 500L
        const val OVERSHOOT_ENTER_EPS_PX = 0.5f
    }
}
