plugins {
    alias(libs.plugins.inventario.android.library)
    alias(libs.plugins.inventario.android.hilt)
    alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "co.inventario.data"
}

// Room exporta el esquema para poder escribir migraciones verificadas (plan.md §8.2).
extensions.configure<com.google.devtools.ksp.gradle.KspExtension> {
    arg("room.schemaLocation", "$projectDir/schemas")
}

dependencies {
    implementation(project(":core:domain"))
    implementation(project(":core:common"))

    implementation(libs.room.runtime)
    implementation(libs.room.ktx)
    ksp(libs.room.compiler)

    implementation(libs.androidx.core.ktx)
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
