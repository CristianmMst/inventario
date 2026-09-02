plugins {
    alias(libs.plugins.inventario.android.library)
    alias(libs.plugins.inventario.android.compose)
}

android {
    namespace = "co.inventario.designsystem"
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.compose.material.icons)
}
