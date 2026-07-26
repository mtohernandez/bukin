plugins {
    alias(libs.plugins.kotlin.jvm)
}

// Pure Kotlin on purpose: :domain must never see android.*. That it builds as a JVM
// library is the enforcement — an Android import would not compile here.
kotlin {
    jvmToolchain(17)
}

dependencies {
    testImplementation(libs.junit)
}
