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

    private val raiz = generateSequence(File("").absoluteFile) { it.parentFile }
        .first { File(it, "build.gradle.kts").exists() && it.name == "designsystem" }

    private val fuentes: List<String> =
        File(raiz, "src/main").walkTopDown().filter { it.extension == "kt" }.map { it.readText() }.toList()

    private val tokens: String = File(raiz, "src/main/kotlin/co/inventario/designsystem/tema/Tokens.kt").readText()

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

    /**
     * `RNF-08` habla de **objetivos táctiles**, así que la comprobación mira alturas de control:
     * `height` y `minHeight`. `size` se dejó fuera a propósito al añadir iconografía: el glifo de
     * un icono mide 20 o 24 dp y no es lo que el dedo toca — lo que se toca es el `IconButton`
     * que lo envuelve, y ese sí pasa por [Dimensiones.areaTactilMinima]. Lo que sustituye a esa
     * vigilancia es el test de convención de nombres de abajo.
     */
    @Test
    fun `rnf 08 los componentes compartidos no fijan alturas por debajo del minimo`() {
        val sp = Regex("""(\d+(?:\.\d+)?)\.sp""")
        val dpAltura = Regex("""(?:height|minHeight)\s*=\s*(\d+)\.dp""")
        for (fuente in fuentes) {
            sp.findAll(fuente).forEach { assertTrue(it.groupValues[1].toFloat() >= 16f, "texto de ${it.value}") }
            dpAltura.findAll(fuente).forEach { assertTrue(it.groupValues[1].toInt() >= 48, "objetivo de ${it.value}") }
        }
    }

    /**
     * Convención que sostiene lo anterior: si un token mide algo que el dedo toca, se llama
     * `altura…` o `areaTactil…` y no baja de 48 dp. Grosores, glifos y espacios llevan otro
     * prefijo justamente para no confundirse con un objetivo táctil.
     */
    @Test
    fun `rnf 08 todo token de altura tactil llega a 48 dp`() {
        val declaracion = Regex("""val ((?:altura|areaTactil)\w*)\s*:\s*Dp\s*=\s*(\d+)\.dp""")
        val encontrados = declaracion.findAll(tokens).toList()
        assertTrue(encontrados.isNotEmpty(), "no se encontró ningún token de altura en Tokens.kt")
        for (token in encontrados) {
            assertTrue(
                token.groupValues[2].toInt() >= 48,
                "${token.groupValues[1]} mide ${token.groupValues[2]} dp; un objetivo táctil no baja de 48",
            )
        }
    }

    /**
     * Añadir un color al sistema sin añadir su par de contraste dejaría un texto sin verificar.
     * Todo contenedor y todo color de [co.inventario.designsystem.tema.Estado] tiene que aparecer
     * en `paresDeContraste`, que es lo que comprueba el test de AA de arriba.
     */
    @Test
    fun `rnf 09 ningun color de estado o contenedor se queda sin par de contraste`() {
        val listaDePares = tokens.substringAfter("val paresDeContraste")
        val declarados = Regex("""val (\w+) = Color\(0x""").findAll(tokens).map { it.groupValues[1] }.toList()
        val bloqueEstado = tokens.substringAfter("object Estado {").substringBefore("\n}")
        val deEstado = Regex("""val (\w+) = Color\(0x""").findAll(bloqueEstado).map { it.groupValues[1] }.toSet()

        val obligatorios = declarados.filter { it.contains("Contenedor") || it in deEstado }
        assertTrue(obligatorios.isNotEmpty(), "no se encontraron colores de contenedor en Tokens.kt")
        for (nombre in obligatorios) {
            assertTrue(
                Regex("""\b$nombre\b""").containsMatchIn(listaDePares),
                "$nombre no aparece en paresDeContraste: su contraste no se está verificando",
            )
        }
    }
}
