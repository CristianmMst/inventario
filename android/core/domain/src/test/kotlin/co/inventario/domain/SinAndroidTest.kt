package co.inventario.domain

import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * plan.md §8.1: core:domain es Kotlin puro. Ningún archivo del módulo importa `android.*`,
 * `androidx.*`, Retrofit ni Room, y el build no declara dependencias de Android.
 */
class SinAndroidTest {

    private val raiz: File = generateSequence(File("").absoluteFile) { it.parentFile }
        .first { File(it, "build.gradle.kts").exists() && it.name == "domain" }

    @Test
    fun `el modulo no importa android ni frameworks de datos`() {
        val prohibidos = listOf("import android.", "import androidx.", "import retrofit2.", "import okhttp3.")
        val culpables = File(raiz, "src/main").walkTopDown()
            .filter { it.isFile && it.extension == "kt" }
            .filter { archivo -> prohibidos.any { archivo.readText().contains(it) } }
            .map { it.relativeTo(raiz).path }
            .toList()
        assertEquals(emptyList(), culpables)
    }

    @Test
    fun `el build no declara dependencias de android`() {
        val build = File(raiz, "build.gradle.kts").readText()
        assert(!build.contains("com.android")) { "el build de core:domain aplica un plugin de Android" }
        assert(!build.contains("androidx")) { "el build de core:domain depende de androidx" }
    }
}
