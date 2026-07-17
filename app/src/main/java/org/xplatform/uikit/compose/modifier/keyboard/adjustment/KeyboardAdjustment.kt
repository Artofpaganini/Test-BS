package org.xplatform.uikit.compose.modifier.keyboard.adjustment

import android.os.Build.VERSION
import android.os.Build.VERSION_CODES
import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.runtime.State
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import org.xplatform.uikit.compose.modifier.keyboard.lift.KeyboardLiftState

/**
 * Модификатор для корректного отображения контента, при открытии клавиатуры. Composable функция/ или даже целый контейнер,
 * состоящий из Composable функций, при открытии клавиатуры будут плавно(анимацюи можно регулировать) подняты вверх,
 * и отобразятся над клавиатурой, исключая Composable функции/ или даже целые контейнеры, состоящий из Composable функций,
 * к которым не был применен модификатор. Т.о контент который поднимать не нужно останется под клавиатурой.
 *
 * Пример реализации, можно посмотреть на экранах авторизации для стиля (Simple) или нового picker dialog'f
 *
 *
 * Рекомендуется использовать вместо дефолтного модификатора imePadding(), ввиду полного контроля, при минимальных
 * затратах на производительность.
 */

@OptIn(ExperimentalComposeUiApi::class)
fun Modifier.withAdjustmentForKeyboard(
    // при использовании внутри DialogFragment указывать false и менять setSoftInputMode до super.onViewCreated(view, savedInstanceState)
    shouldChangeSoftInputMode: Boolean = true,
    easing: Easing = FastOutSlowInEasing,
    durationMillis: Int = 300,
    additionalOffsetPx: Float = 0f,
    keyboardState: State<KeyboardLiftState>? = null,
): Modifier = this then KeyboardAdjustmentNodeElement(
    shouldChangeSoftInputMode = shouldChangeSoftInputMode,
    easing = easing,
    durationMillis = durationMillis,
    additionalOffsetPx = additionalOffsetPx,
    keyboardState = keyboardState,
)

fun isLessThan30Api(): Boolean = VERSION.SDK_INT < VERSION_CODES.R
