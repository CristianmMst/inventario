plugins {
    alias(libs.plugins.inventario.jvm.library)
}

// KOTLIN PURO. Sin android.*, sin Retrofit, sin Room (plan.md §8.1). El test
// `test_rf_...` del módulo comprueba que no entra ninguna dependencia de Android.
dependencies {
    implementation(libs.kotlinx.coroutines.core)
}
