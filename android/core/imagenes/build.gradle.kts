plugins {
    alias(libs.plugins.inventario.android.library)
}

android {
    namespace = "co.inventario.imagenes"
    // Robolectric con gráficos nativos: el test de compresión trabaja con bytes JPEG reales (RNF-05).
    testOptions.unitTests.isIncludeAndroidResources = true
}

dependencies {
    implementation(project(":core:domain"))
    testImplementation(libs.robolectric)
}
