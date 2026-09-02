plugins {
    alias(libs.plugins.inventario.android.library)
    alias(libs.plugins.inventario.android.compose)
    alias(libs.plugins.inventario.android.hilt)
}

android {
    namespace = "co.inventario.feature.catalogo"
    // Robolectric con gráficos nativos: el test de compresión trabaja con bytes JPEG reales (RNF-05).
    testOptions.unitTests.isIncludeAndroidResources = true
}

dependencies {
    implementation(project(":core:domain"))
    implementation(project(":core:common"))
    implementation(project(":core:data"))
    implementation(project(":core:designsystem"))

    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.activity.compose)
    implementation(libs.hilt.navigation.compose)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.coil.compose)
    implementation(libs.coil.network.okhttp)

    testImplementation(libs.turbine)
    testImplementation(libs.mockk)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.robolectric)
}
