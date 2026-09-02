plugins {
    `kotlin-dsl`
}

group = "co.inventario.buildlogic"

// Los plugins de convención se escriben como scripts precompilados en src/main/kotlin.
// Para que puedan aplicar AGP, Kotlin, KSP y Hilt por id, esos plugins van al classpath.
dependencies {
    implementation(libs.android.gradlePlugin)
    implementation(libs.kotlin.gradlePlugin)
    implementation(libs.kotlin.compose.gradlePlugin)
    implementation(libs.kotlin.serialization.gradlePlugin)
    implementation(libs.ksp.gradlePlugin)
    implementation(libs.hilt.gradlePlugin)
}
