package com.onexui.bottomsheet.state

import kotlin.math.abs

internal class SheetAnchorTable internal constructor(private val restEntries: List<AnchorEntry>) {
    val lowestRestAnchorPx: Int = restEntries.firstOrNull()?.anchorPx ?: 0
    val highestRestAnchorPx: Int = restEntries.lastOrNull()?.anchorPx ?: 0

    fun settleTarget(
        offsetPx: Float,
        velocityPxPerSec: Float,
        isDismissAllowed: Boolean,
        flingVelocityThresholdPxPerSec: Float,
    ): SheetValue? {
        val candidates = if (isDismissAllowed) {
            (restEntries + AnchorEntry(SheetValue.Hidden, 0))
                .distinctBy { entry -> entry.anchorPx }
                .sortedBy { entry -> entry.anchorPx }
        } else {
            restEntries
        }
        if (candidates.isEmpty()) return null
        val chosen = when {
            velocityPxPerSec < -flingVelocityThresholdPxPerSec ->
                candidates.firstOrNull { entry -> entry.anchorPx > offsetPx + ANCHOR_EPS } ?: candidates.last()
            velocityPxPerSec > flingVelocityThresholdPxPerSec ->
                candidates.lastOrNull { entry -> entry.anchorPx < offsetPx - ANCHOR_EPS } ?: candidates.first()
            else ->
                candidates.minByOrNull { entry -> abs(entry.anchorPx - offsetPx) } ?: candidates.first()
        }
        return chosen.value
    }

    fun isAtRestAnchor(offsetPx: Float): Boolean =
        restEntries.any { entry -> abs(entry.anchorPx - offsetPx) < ANCHOR_EPS }

    internal data class AnchorEntry(val value: SheetValue, val anchorPx: Int)
}

private const val ANCHOR_EPS = 1f
