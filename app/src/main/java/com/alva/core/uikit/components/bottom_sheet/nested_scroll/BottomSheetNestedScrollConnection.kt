package com.alva.core.uikit.components.bottom_sheet.nested_scroll

import androidx.compose.foundation.gestures.AnchoredDraggableState
import androidx.compose.foundation.gestures.FlingBehavior
import androidx.compose.foundation.gestures.ScrollScope
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.unit.Velocity
import com.alva.core.uikit.components.bottom_sheet.AlvaBottomSheetValue

internal class BottomSheetNestedScrollConnection(
    private val draggableState: AnchoredDraggableState<AlvaBottomSheetValue>,
    private val flingBehavior: FlingBehavior,
) : NestedScrollConnection {

    override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
        val delta = available.y
        return if (delta < 0f && source == NestedScrollSource.UserInput) {
            consumeScrollDelta(delta)
        } else {
            Offset.Zero
        }
    }

    override fun onPostScroll(
        consumed: Offset,
        available: Offset,
        source: NestedScrollSource,
    ): Offset {
        return if (source == NestedScrollSource.UserInput) {
            consumeScrollDelta(available.y)
        } else {
            Offset.Zero
        }
    }

    override suspend fun onPreFling(available: Velocity): Velocity {
        val velocity = available.y
        val currentOffset = runCatching { draggableState.requireOffset() }.getOrNull()
            ?: return Velocity.Zero
        val minAnchor = draggableState.anchors.minPosition()
        return if (velocity < 0f && currentOffset > minAnchor) {
            performAnchoredFling(velocity)
            available.copy(x = 0f)
        } else {
            Velocity.Zero
        }
    }

    override suspend fun onPostFling(consumed: Velocity, available: Velocity): Velocity {
        if (available.y != 0f) {
            performAnchoredFling(available.y)
        }
        return available.copy(x = 0f)
    }

    private fun consumeScrollDelta(delta: Float): Offset {
        val consumed = draggableState.dispatchRawDelta(delta)
        return Offset(x = 0f, y = consumed)
    }

    // Скорость флинга списка доносится до листа: фактический settle к якорю делает
    // velocity-aware flingBehavior внутри anchoredDrag (как в каноничном
    // Modifier.anchoredDraggable), а не позиционный settle(animationSpec).
    private suspend fun performAnchoredFling(velocity: Float) {
        draggableState.anchoredDrag {
            val scrollScope = object : ScrollScope {
                override fun scrollBy(pixels: Float): Float {
                    val base = draggableState.offset.let { offset -> if (offset.isNaN()) 0f else offset }
                    val newOffset = (base + pixels).coerceIn(
                        draggableState.anchors.minPosition(),
                        draggableState.anchors.maxPosition(),
                    )
                    val consumed = newOffset - base
                    dragTo(newOffset)
                    return consumed
                }
            }
            with(flingBehavior) {
                scrollScope.performFling(velocity)
            }
        }
    }
}
