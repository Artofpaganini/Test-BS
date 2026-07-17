package com.alva.core.uikit.components.bottom_sheet

/**
 * Описывает anchor-состояние нижнего листа: скрыт, частично раскрыт или полностью раскрыт.
 */
sealed interface AlvaBottomSheetValue {

    /** Лист скрыт и не виден пользователю. */
    data object Hidden : AlvaBottomSheetValue

    /**
     * Лист частично раскрыт на [fraction] собственной высоты листа, где fraction ∈ (0f, 1f).
     * Типичные значения: 0.25f, 0.5f, 0.75f. Высота отсчитывается от измеренной высоты
     * листа (см. buildAnchors), а не от высоты экрана.
     */
    data class PartialExpanded(val fraction: Float) : AlvaBottomSheetValue

    /** Лист полностью раскрыт. */
    data object Expanded : AlvaBottomSheetValue
}
