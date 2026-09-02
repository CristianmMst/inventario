plugins {
    alias(libs.plugins.inventario.android.library)
    alias(libs.plugins.inventario.android.hilt)
    alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "co.inventario.data"
}

dependencies {
    implementation(project(":core:domain"))
    implementation(project(":core:common"))

    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.retrofit)
    implementation(libs.retrofit.kotlinx.serialization)
    implementation(libs.okhttp)
    implementation(libs.okhttp.logging)

    testImplementation(libs.okhttp.mockwebserver)
    testImplementation(libs.mockk)
    testImplementation(libs.turbine)
}
