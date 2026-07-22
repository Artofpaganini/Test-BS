package com.onexui.bottomsheet.state

import kotlin.math.abs

/**
 * Якорная математика листа (settle/floor/ceiling/isAtRest). restEntries — rest-якоря, отсортированы по высоте и
 * distinct по px, БЕЗ Hidden (dismissOnSwipeDown мутабелен -> Hidden добавляется в момент запроса settle).
 * Строится через SheetMetrics.toAnchorTable.
 */
internal class SheetAnchorTable internal constructor(private val restEntries: List<AnchorEntry>) {

    internal data class AnchorEntry(val value: SheetValue, val anchorPx: Int)

    val lowestRestAnchorPx: Int = restEntries.firstOrNull()?.anchorPx ?: 0
    val highestRestAnchorPx: Int = restEntries.lastOrNull()?.anchorPx ?: 0

    /**
     * Выбор якоря при отпускании: fling -> якорь по направлению, иначе ближайший. Hidden(0) — толко при
     * isDismissAllowed и отсутствии rest-якоря на 0px. null — если кандидатов нет (метрики без якорей).
     */
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

    /** Стоит ли offset на rest-якоре (без Hidden): onPreFling не съедает инерцию списка, если лист дорос до якоря. */
    fun isAtRestAnchor(offsetPx: Float): Boolean =
        restEntries.any { entry -> abs(entry.anchorPx - offsetPx) < ANCHOR_EPS }
}

private const val ANCHOR_EPS = 1f
