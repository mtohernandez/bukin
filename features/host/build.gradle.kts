// Placeholder. Session 2 fills this in with session control, the live roster, and
// manual registration. Empty on purpose — nothing here yet.
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
    implementation(project(":core:designsystem"))
}
