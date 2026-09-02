plugins {
    alias(libs.plugins.inventario.jvm.library)
}

// Resultado<T> y el mapeo de errores de la API a texto en español (T-072). Kotlin puro.
dependencies {
    implementation(libs.kotlinx.coroutines.core)
}
