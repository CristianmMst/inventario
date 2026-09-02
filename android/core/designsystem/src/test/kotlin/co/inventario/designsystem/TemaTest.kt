package co.inventario.designsystem

import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import co.inventario.designsystem.tema.Dimensiones
import co.inventario.designsystem.tema.Tipografia
import co.inventario.designsystem.tema.contraste
import co.inventario.designsystem.tema.paresDeContraste
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** RNF-08 y RNF-09: los mínimos están en los tokens compartidos, no en cada pantalla. */
class TemaTest {

    @Test
    fun `rnf 09 ningun estilo de contenido baja de 16 sp`() {
        for (estilo in Tipografia.estilos) {
            assertTrue(estilo.fontSize.value >= 16f, "estilo con ${estilo.fontSize}")
        }
        assertEquals(16.sp, Tipografia.tamanoMinimoContenido)
        assertTrue(Tipografia.stockDestacado.fontSize.value >= 32f, "el stock se lee de pie")
    }

    @Test
    fun `rnf 08 el area tactil minima es 48 dp y el boton principal mas alto`() {
        assertEquals(48.dp, Dimensiones.areaTactilMinima)
        assertTrue(Dimensiones.alturaBotonPrincipal >= 48.dp)
    }

    @Test
    fun `rnf 09 todos los pares texto fondo del tema cumplen wcag aa`() {
        for ((texto, fondo) in paresDeContraste) {
            val relacion = contraste(texto, fondo)
            assertTrue(relacion >= 4.5, "contraste $relacion entre $texto y $fondo")
        }
    }

    @Test
    fun `rnf 08 los componentes compartidos no fijan tamanos por debajo de los minimos`() {
        val raiz = generateSequence(File("").absoluteFile) { it.parentFile }
            .first { File(it, "build.gradle.kts").exists() && it.name == "designsystem" }
        val fuentes = File(raiz, "src/main").walkTopDown().filter { it.extension == "kt" }.map { it.readText() }.toList()
        val sp = Regex("""(\d+(?:\.\d+)?)\.sp""")
        val dpAltura = Regex("""(?:height|minHeight|size)\s*=\s*(\d+)\.dp""")
        for (fuente in fuentes) {
            sp.findAll(fuente).forEach { assertTrue(it.groupValues[1].toFloat() >= 16f, "texto de ${it.value}") }
            dpAltura.findAll(fuente).forEach { assertTrue(it.groupValues[1].toInt() >= 48, "objetivo de ${it.value}") }
        }
    }
}
