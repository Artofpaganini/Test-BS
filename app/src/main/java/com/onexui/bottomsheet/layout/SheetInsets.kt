package com.onexui.bottomsheet.layout

/** Пиксельные инсеты и размеры экана, передаваемые в контейнер листа (замер якорей и паддингов). */
internal data class SheetInsets(
    val screenHeightPx: Int,
    val statusBarPx: Int,
    val navBarPx: Int,
    val loadingSheetHeightPx: Int,
)
