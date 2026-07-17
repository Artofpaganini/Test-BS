plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.compose.compiler)
}

android {
    namespace = "com.zorderlab"
    compileSdk = libs.versions.compileSdk.get().toInt()

    defaultConfig {
        applicationId = "com.zorderlab"
        minSdk = libs.versions.minSdk.get().toInt()
        targetSdk = libs.versions.targetSdk.get().toInt()
        versionCode = libs.versions.versionCode.get().toInt()
        versionName = libs.versions.versionName.get()
    }

    buildFeatures {
        compose = true
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }

    buildTypes {
        debug {
            isMinifyEnabled = false
        }
        release {
            isMinifyEnabled = false
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }
}

dependencies {
    // Compose Multiplatform (JetBrains) — те же артефакты, что в Alva
    implementation(libs.compose.runtime)
    implementation(libs.compose.runtime.saveable)
    implementation(libs.compose.foundation)
    implementation(libs.compose.ui)
    implementation(libs.compose.ui.util)
    implementation(libs.compose.animation)
    implementation(libs.compose.material3)

    // Navigation 3 (JetBrains 1.1.1) — API, под который написан порт AlvaBottomSheet
    implementation(libs.jetbrains.navigation.ui)
    implementation(libs.jetbrains.navigation.viewmodel.lifecycle)
    implementation(libs.jetbrains.navigationevent.compose)
    implementation(libs.androidx.lifecycle.runtime)

    // Мост Compose <-> Activity + View-мир (Fragment-диалоги)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.androidx.fragment.ktx)
    implementation(libs.material)
}
