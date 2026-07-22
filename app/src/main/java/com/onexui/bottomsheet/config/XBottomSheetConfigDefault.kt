package com.onexui.bottomsheet.config

/**
 * Дефолт config-параметра XBottomSheet: статичный immutable-инстанс со стабильной идентичностью -> referential
 * equality держит skip рекомпозици. Тема не нужна — цвета Unspecified, резолв в корне.
 */
internal val XBottomSheetConfigDefault: XBottomSheetConfig = XBottomSheetConfigBuilder().build()
