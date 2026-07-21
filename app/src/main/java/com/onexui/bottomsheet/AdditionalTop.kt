package com.onexui.bottomsheet

// Состояния Additional Top (§1). Переключаются ТОЛЬКО внешними факторами (кнопка/логика экрана), не жестами.
// Кейс: «Добавить в купон» / «Отслеживать». Раскладка карточки (протрузия над листом) — в SubcomposeLayout
// корня (слот AdditionalTopCardSlot): карточка — отдельный слой НАД телом листа, поэтому анимация её видимой
// высоты (fraction) не входит в высоту/детект тела и не дёргает контент листа.
internal enum class AdditionalTopState { Expanded, Collapsed }
