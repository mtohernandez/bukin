// Session control, the live roster, and manual registration.
plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.compose.compiler)
}

android {
    namespace = "com.buk.bukin.feature.host"
    compileSdk = libs.versions.compileSdk.get().toInt()
    defaultConfig { minSdk = libs.versions.minSdk.get().toInt() }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    buildFeatures { compose = true }
}

kotlin { jvmToolchain(17) }

dependencies {
    val composeBom = platform(libs.androidx.compose.bom)
    implementation(composeBom)
    debugImplementation(libs.androidx.compose.ui.tooling)

    implementation(project(":core:ble"))
    implementation(project(":core:data"))
    implementation(project(":core:designsystem"))
    implementation(project(":domain"))
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
}
