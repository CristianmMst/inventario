plugins {
    alias(libs.plugins.inventario.android.library)
    alias(libs.plugins.inventario.android.compose)
}

android {
    namespace = "co.inventario.designsystem"
}

dependencies {
    implementation(libs.androidx.core.ktx)
    // `api`, no `implementation`: los módulos `feature` dibujan iconos del objeto `Iconos`,
    // que expone tipos de esta librería.
    api(libs.compose.material.icons)
}
