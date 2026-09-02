import com.android.build.api.dsl.LibraryExtension
import org.gradle.api.JavaVersion
import org.gradle.kotlin.dsl.configure
import org.gradle.kotlin.dsl.dependencies

// Convención para toda librería Android del proyecto: minSdk 24 con desugaring (RNF-14),
// Java 17 y tests JVM con JUnit4. Desde AGP 9 el soporte de Kotlin viene incluido en el
// plugin de Android: aplicar además `org.jetbrains.kotlin.android` es un error.
plugins {
    id("com.android.library")
}

val libs = extensions.getByType<VersionCatalogsExtension>().named("libs")

configure<LibraryExtension> {
    compileSdk = libs.findVersion("compileSdk").get().requiredVersion.toInt()
    defaultConfig {
        minSdk = libs.findVersion("minSdk").get().requiredVersion.toInt()
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
        isCoreLibraryDesugaringEnabled = true
    }
    testOptions.unitTests.isReturnDefaultValues = true
}

dependencies {
    add("coreLibraryDesugaring", libs.findLibrary("desugar-jdk-libs").get())
    add("testImplementation", libs.findLibrary("junit").get())
    add("testImplementation", libs.findLibrary("kotlin-test").get())
    add("testImplementation", libs.findLibrary("kotlin-test-junit").get())
    add("testImplementation", libs.findLibrary("kotlinx-coroutines-test").get())
}
