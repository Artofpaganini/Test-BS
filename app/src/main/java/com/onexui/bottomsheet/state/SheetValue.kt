package com.onexui.bottomsheet.state

/**
 * Логический стейт высоы листа. Hidden — закрыт; Content — wrap по контенту; Collapsed — peek-якорь;
 * ExpandedContent — контент целиком в пределах экрана; ExpandedFullScreen — во весь экран; Loading — Loader-якорь;
 * Custom — кастомный якорь по ключу (fill-режим).
 */
sealed interface SheetValue {
    data object Hidden : SheetValue
    data object Content : SheetValue
    data object Collapsed : SheetValue
    data object ExpandedContent : SheetValue
    data object ExpandedFullScreen : SheetValue
    data object Loading : SheetValue
    data class Custom(val key: String) : SheetValue
}
