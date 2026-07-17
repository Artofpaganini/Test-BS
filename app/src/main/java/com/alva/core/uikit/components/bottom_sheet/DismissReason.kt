package com.alva.core.uikit.components.bottom_sheet

/**
 * Описывает источник закрытия нижнего листа — жест пользователя или программный запрос.
 */
sealed interface DismissReason {

    /** Пользователь смахнул лист вниз. */
    data object UserSwipedDown : DismissReason

    /** Пользователь нажал на затемнённую область за листом. */
    data object UserTappedScrim : DismissReason

    /** Пользователь нажал системную кнопку «Назад». */
    data object UserPressedBack : DismissReason

    /** Закрытие инициировано программно. */
    data object RequestedByCode : DismissReason
}
