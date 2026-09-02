plugins {
    alias(libs.plugins.inventario.android.application)
    alias(libs.plugins.inventario.android.compose)
    alias(libs.plugins.inventario.android.hilt)
    alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "co.inventario.app"

    defaultConfig {
        applicationId = "co.inventario.app"
        versionCode = 1
        versionName = "1.0.0"
    }

    // La app apunta al backend por la IP de la máquina en la red local (plan.md §10).
    // Se sobreescribe con -PbackendUrl=http://192.168.x.y:8000/ al compilar.
    val backendUrl = (project.findProperty("backendUrl") as String?) ?: "http://10.0.2.2:8000/"

    buildTypes {
        debug {
            buildConfigField("String", "BACKEND_URL", "\"$backendUrl\"")
            manifestPlaceholders["usesCleartextTraffic"] = "true"
        }
        release {
            isMinifyEnabled = false
            buildConfigField("String", "BACKEND_URL", "\"https://inventario.invalid/\"")
            manifestPlaceholders["usesCleartextTraffic"] = "false"
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
        // Para medir RNF-10 (arranque < 3 s, cámara < 1,5 s) en un teléfono real: igual que
        // release —no depurable, con R8— pero firmada con la clave de depuración para poder
        // instalarla y apuntando al backend local. No se publica.
        create("medicion") {
            initWith(getByName("release"))
            isMinifyEnabled = true
            signingConfig = signingConfigs.getByName("debug")
            buildConfigField("String", "BACKEND_URL", "\"$backendUrl\"")
            manifestPlaceholders["usesCleartextTraffic"] = "true"
            matchingFallbacks += listOf("release")
        }
    }

    buildFeatures {
        buildConfig = true
    }
}

dependencies {
    implementation(project(":core:domain"))
    implementation(project(":core:common"))
    implementation(project(":core:data"))
    implementation(project(":core:designsystem"))
    implementation(project(":feature:auth"))
    implementation(project(":feature:escaneo"))
    implementation(project(":feature:catalogo"))
    implementation(project(":feature:movimientos"))
    implementation(project(":feature:compras"))
    implementation(project(":feature:facturas"))
    implementation(project(":feature:reportes"))
    implementation(project(":feature:ajustes"))

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.hilt.navigation.compose)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.kotlinx.coroutines.android)

    testImplementation(libs.kotlinx.serialization.json)
}
