package com.onexui.bottomsheet.config

// Дефолт config-параметра XBottomSheet (замена companion Default по поправке №3): статичный immutable-инстанс со
// стабильной идентичностью → referential equality держит skip. Тема-зависимость не нужна — цвета Unspecified, резолв в корне.
internal val XBottomSheetConfigDefault: XBottomSheetConfig = XBottomSheetConfigBuilder().build()
