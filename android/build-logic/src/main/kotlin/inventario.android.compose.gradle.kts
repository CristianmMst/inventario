import com.android.build.api.dsl.CommonExtension
import org.gradle.kotlin.dsl.dependencies

// Compose con el compilador del plugin de Kotlin (Kotlin 2.x) y versiones por el BOM.
plugins {
    id("org.jetbrains.kotlin.plugin.compose")
}

val libs = extensions.getByType<VersionCatalogsExtension>().named("libs")

extensions.configure<CommonExtension>("android") {
    buildFeatures.compose = true
}

dependencies {
    val bom = libs.findLibrary("compose-bom").get()
    add("implementation", platform(bom))
    add("androidTestImplementation", platform(bom))
    add("implementation", libs.findLibrary("compose-ui").get())
    add("implementation", libs.findLibrary("compose-material3").get())
    add("implementation", libs.findLibrary("compose-ui-tooling-preview").get())
    add("implementation", libs.findLibrary("androidx-lifecycle-runtime-compose").get())
    add("debugImplementation", libs.findLibrary("compose-ui-tooling").get())
    add("debugImplementation", libs.findLibrary("compose-ui-test-manifest").get())
    add("androidTestImplementation", libs.findLibrary("compose-ui-test-junit4").get())
}
