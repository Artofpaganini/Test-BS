package com.onexui.bottomsheet.state

import androidx.compose.runtime.Immutable

/**
 * Кастомный rest-якорь fill-режима: heightFraction — доля высоты экана (0..1). internal constructor —
 * якоря создаёт только XSheetAnchorsBuilder (DSL). Равенство по ссылке достаточно: якоря живут списком в одном
 * snapshot-поле стейта, наружу не копируются; смена набора = новый список новых инстансов (подхватывается re-settle).
 */
@Immutable
internal class XSheetAnchor internal constructor(val key: String, val heightFraction: Float)
