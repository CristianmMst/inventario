plugins {
    alias(libs.plugins.inventario.android.library)
    alias(libs.plugins.inventario.android.compose)
    alias(libs.plugins.inventario.android.hilt)
}

android {
    namespace = "co.inventario.feature.auth"
}

dependencies {
    implementation(project(":core:domain"))
    implementation(project(":core:common"))
    implementation(project(":core:data"))
    implementation(project(":core:designsystem"))

    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.hilt.navigation.compose)
    implementation(libs.kotlinx.coroutines.android)

    testImplementation(libs.turbine)
    testImplementation(libs.mockk)
}
