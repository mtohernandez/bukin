plugins {
    alias(libs.plugins.android.library)
}

// No Compose plugin and no :core:designsystem dependency on purpose. This module owns the
// radio and nothing else: it returns typed results and never a string, so every user-facing
// sentence stays in the one strings.xml that :core:designsystem holds.
android {
    namespace = "com.buk.bukin.ble"
    compileSdk = libs.versions.compileSdk.get().toInt()
    defaultConfig { minSdk = libs.versions.minSdk.get().toInt() }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

kotlin { jvmToolchain(17) }

dependencies {
    implementation(project(":domain"))
    implementation(libs.androidx.core.ktx)
    implementation(libs.kotlinx.coroutines.android)
}
