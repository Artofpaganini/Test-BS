plugins {
    // AGP 9+ включает встроенную поддержку Kotlin — отдельный kotlin-android плагин не нужен.
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.compose.compiler) apply false
}
