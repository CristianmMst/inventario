plugins {
    alias(libs.plugins.inventario.android.library)
    alias(libs.plugins.inventario.android.compose)
    alias(libs.plugins.inventario.android.hilt)
}

android {
    namespace = "co.inventario.feature.escaneo"
}

dependencies {
    implementation(project(":core:common"))
    implementation(project(":core:designsystem"))

    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.activity.compose)
    implementation(libs.hilt.navigation.compose)
    implementation(libs.kotlinx.coroutines.android)

    // Cámara y lectura de códigos con el modelo empaquetado: funciona sin red (RNF-10).
    implementation(libs.camerax.core)
    implementation(libs.camerax.camera2)
    implementation(libs.camerax.lifecycle)
    implementation(libs.camerax.view)
    implementation(libs.mlkit.barcode)

    testImplementation(libs.turbine)
    testImplementation(libs.kotlinx.coroutines.test)
}
